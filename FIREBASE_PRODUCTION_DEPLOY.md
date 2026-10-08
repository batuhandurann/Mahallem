# Canlı Firebase dağıtımı ve kanıtı

`Firebase Production Deploy and Audit` yalnız **main** dalından elle çalışır.
`audit` canlı ayarları ve mevcut dağıtımı salt okunur doğrular. `deploy`, aynı
kontrolleri geçtikten sonra bu commit'in Rules/index/Storage/Functions kodunu ve
iki temizleme TTL politikasını dağıtır. Push/PR olayı üretim kaynaklarını değiştirmez.
Workflow hazırlanması canlı dağıtım yapıldığı anlamına gelmez.

## Bir kez yapılacak yetkilendirme

GitHub repository → Settings → Environments → `production` ortamını **main dalı**
ile sınırlandırın; required reviewers ve prevent self-review etkinleştirin.
Workflow'un `environment: production` alanı bu mevcut korumaları uygular; dosya,
Console ortam korumasını kendi başına oluşturmaz. Cloud'da Workload Identity
Federation provider kurup güven koşulunu bu repository'nin değişmez sayısal
repository/owner ID'sine ve `ref == refs/heads/main` değerine sınırlandırın.
Service account impersonation kullanın; repository'ye JSON hizmet hesabı anahtarı
veya uzun ömürlü access token koymayın.

`production` ortamında şu değerler gerekir:

| Tür | Ad | İçerik |
| --- | --- | --- |
| Variable | `FIREBASE_PRODUCTION_PROJECT_ID` | Gerçek ve açık project ID |
| Variable | `FIREBASE_ANDROID_API_KEY_ID` | Android config'teki API key'nin kaynak ID'si/UID'si; key string değil |
| Variable | `GCP_WORKLOAD_IDENTITY_PROVIDER` | `projects/NUMBER/locations/global/workloadIdentityPools/POOL/providers/PROVIDER` |
| Variable | `FIREBASE_DEPLOY_SERVICE_ACCOUNT` | Aynı proje içinde deploy service account e-postası |
| Secret | `GOOGLE_SERVICES_JSON_BASE64` | `com.batuhanduran.burada` kaydı için yeni config'in base64 değeri |

Deploy principal'a yalnız gerekli proje kaynaklarında Rules release, Firestore
index/TTL, Storage Rules ve gen2 Functions dağıtım izinlerini verin. Function
runtime principal'a named `mahallem` database, özel Storage, Firebase Auth user
read ve FCM send izinleri gerekir; deploy principal ile runtime principal aynı
rol paketiyle çalışmak zorunda değildir. Audit ek olarak Firebase app/certificate,
App Check, API key metadata/**keyString**, Auth config, bucket metadata/IAM,
Functions ve Firestore admin read ister. WIF principal'ın service account'a
`roles/iam.workloadIdentityUser` yetkisini repository/branch koşuluyla sınırlayın.
Gen2 dağıtım için build/runtime service-account `actAs`, Eventarc/Cloud Build ve
Artifact Registry izinlerini mevcut Cloud dağıtım modeline göre daraltın;
`Owner` veya `Editor` atamak bu workflow'un gereği değildir.

## Dağıtımın durduğu gerçek güvenlik koşulları

Script, açık `GOOGLE_APPLICATION_CREDENTIALS` olmadan veya başka projeye ait
principal/config ile çalışmaz. Şunları yetkili Cloud API'den **canlı** okur:

- Aynı project ID/number, aktif canonical Android app, kayıtlı SHA-256 ve Android
  ile backend'in aynı default Storage bucket kullanması.
- `mahallem` adlı Native Firestore database ve `europe-west3` konumu.
- Play Integrity kaydı, Auth/Firestore/Storage App Check `ENFORCED`, Android
  package+SHA-1 API kısıtları ve config ile birebir API key eşleşmesi.
- Email enumeration protection, en az 8 karakterlik zorunlu password policy,
  email auth açık ve şu anda kullanılmayan SMS auth kapalı.
- Bucket aynı projede, public access prevention `enforced`, public IAM yok.

Eksik enforcement veya yanlış config'i **otomatik açmaz/düzeltmez**. Önce
`FIREBASE_SECURITY_RELEASE.md` içindeki Play internal testing ve gerçek cihaz
doğrulamasını tamamlayın, sonra yetkili Console operatörü enforcement açsın.
API erişiminde 403/404/eksik response başarılı kanıt sayılmaz.

Dağıtım kapsamı yalnız:

```sh
firebase deploy --only firestore:rules,firestore:indexes,storage,functions:trusted-backend --project PROJECT_ID --non-interactive
gcloud firestore fields ttls update expiresAt --collection-group=_abuseBudgets --database=mahallem --enable-ttl --project=PROJECT_ID
gcloud firestore fields ttls update expiresAt --collection-group=_pushDeliveries --database=mahallem --enable-ttl --project=PROJECT_ID
```

Auth provider/config dağıtılmaz; istenmeyen function silmeyi kabul etmek için
`--force` kullanılmaz. Function deploy kısmen başarısız olabilir: workflow kırmızı
kalır; düzeltip yeniden çalıştırın. TTL, abuse/delivery kayıtlarının temizliğidir;
anlık rate limit yerine geçmez. Mevcut özel fotoğrafların eski bearer download
tokenlarını ayrıca kaldırın. Cloud audit bu eski tokenları veya nesne ACL'lerini
taramaz; bucket IAM/public access prevention kontrolü tek başına bunun kanıtı değildir.

Son kontrol yayımlanan Firestore/Storage Rules içeriğini commit ile birebir
karşılaştırır; moderasyon index'i `READY`, iki TTL `ACTIVE`, beş Function `ACTIVE`,
Node 22 ve en fazla 5 instance, push trigger database `mahallem` ister.
Yeni index henüz oluşturuluyorsa başarı iddia edilmez; Cloud hazır olduğunda
`audit` işlemini tekrar çalıştırın. Function kaydı App Check tokenının gerçekten
reddedildiğini veya canlı kodun cihazdan çalıştığını kanıtlamaz.

Yalnız timestamp, sonuç, proje/package/database ve Rules SHA-256 gibi izinli
alanlardan oluşan `firebase-production-redacted-evidence` artifact'i yüklenir.
Raw Cloud response, API key string, SMTP secret, Auth test numarası ve ADC/token
dosyası log/artifact'e yazılmaz. Secret içerme ihtimali olan `firebase-debug.log`
yayımlanmaz ve iş sonunda silinir.

Yerelde, açık yetkili ADC ve gcloud ile aynı salt okunur kontrol:

```sh
python3 scripts/verify_firebase_cloud.py --project PROJECT_ID --key-id KEY_RESOURCE_ID --deployed --report /private/firebase-redacted-result.json
python3 -m unittest discover -s test/config -p 'test_*.py'
```

Bu testlerin fixture ile geçmesi canlı ayarların açık olduğu anlamına gelmez.
Gerçek cihaz/Play lisansı, canlı push teslimi, trafik/billing uyarıları ve uçtan uca
App Check yanlış-token reddi ayrı doğrulamalardır; raporda başarıya çevrilmez.

Resmi kaynaklar: [Workload Identity Federation](https://github.com/google-github-actions/auth),
[App Check](https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.services),
[API key kısıtları](https://cloud.google.com/api-keys/docs/reference/rest/v2/projects.locations.keys),
[Email enumeration](https://cloud.google.com/identity-platform/docs/admin/email-enumeration-protection),
[TTL yönetimi](https://cloud.google.com/sdk/gcloud/reference/firestore/fields/ttls/update).
