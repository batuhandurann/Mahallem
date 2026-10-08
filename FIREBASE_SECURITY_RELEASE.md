# Burada Firebase yayın güvenliği

Kodda App Check kuruldu: `BuradaApplication.onCreate` önce Firebase uygulamasını ve
App Check sağlayıcısını başlatır, sonra diğer SDK'lar kullanılır. Release source
set Play Integrity; debug source set Debug App Check sağlar. Debug SDK release
bağımlılıklarına girmez; debug token hiçbir kaynak/BuildConfig alanına konulmaz.
SDK kurulması, Firebase Console'da enforcement'ın açık olduğu anlamına gelmez.
Bu çalışma sırasında Cloud/Play Console yetkisi veya fiziksel cihaz erişimi yoktu;
bu servislerin canlı ayarlarının etkinleştirildiği ileri sürülmez.

## Uygulama kaydı ve Play Integrity

1. Firebase'de `com.batuhanduran.burada` Android uygulamasını kaydedin. Play App
   Signing'in **uygulama imzalama** SHA-256 parmak izini ekleyin; upload key tek
   başına Play'den yüklenmiş uygulamanın imzasını doğrulamaz. Gereken staging
   imzasını ayrı uygulama/projede kaydedin.
2. Yeni kaydın `google-services.json` dosyasını `app/` içine indirin. Eski
   `com.aistudio.mahallemde.kxqrvz` kaydına ait dosya canlı veya release build için
   özellikle reddedilir. Package değişikliği Auth UID'lerini veya mevcut Firestore
   verilerini dönüştürmez; aynı Firebase projesi ve `mahallem` veritabanı korunur.
3. Play Console → App integrity → Play Integrity API → aynı Firebase Cloud
   projesini bağlayın. Firebase → Security → App Check → Apps altında Play Integrity
   kaydını ve imzayı doğrulayın. Play Store dağıtımı için tanınan uygulama ve
   lisanslı kurulum gereksinimlerini resmi dağıtım tablosuna göre seçin.
4. Gerçek Android cihazına Play internal testing kanalından release yükleyin.
   E-posta giriş/kayıt, sunucudan profil okuma, ilan/teklif, sohbet ve fotoğraf
   işlemlerini yapın; App Check metriklerinde geçerli token sayısını gözlemleyin.
   Tokenı, API anahtarını veya debug secret'ı raporlara/loglara kopyalamayın.
5. Geçerli sürüm çalıştıktan sonra Auth, Firestore ve Storage için enforcement
   açın. Callable backend işlevlerinde `enforceAppCheck: true` korunur. App Check
   tokenı eksik/yanlış istemciyle staging isteklerinin reddedildiğini doğrulayın.
   Emulator testi enforcement veya Play Integrity kanıtı değildir.

Resmi kaynaklar: [Play Integrity kurulumu](https://firebase.google.com/docs/app-check/android/play-integrity-provider),
[debug ortamı](https://firebase.google.com/docs/app-check/android/debug-provider).

## Debug ve yerel Firebase emulator

`-PfirebaseEmulators=true` sadece debug Google Services işleme görevini atlar;
Application programatik `demo-mahallem` config ve named `emulator` app kullanır.
Auth/Firestore verisi gerçek projeye gitmez. Release içinde emulator bayrağı her
zaman false'tur ve demo config reddedilir. CI'da bu bayrak unit/build/lint ve cihaz
instrumentation görevlerinde de geçirilir.

Canlı staging projesinde debug build gerekiyorsa cihazın SDK tarafından üretilen
Debug App Check secret'ını yalnız o staging projesinin Debug Tokens alanında
kaydedin. Kaynak koduna, `.env`, artifact, CI logu veya public issue'ya eklemeyin.
Üretim projesinde test tokenlarını kalıcı tutmayın. Debug build Play Integrity
kanıtı olarak kullanılmaz.

## API kısıtları ve abuse

Google Cloud → APIs & Services → Credentials içinde Android Firebase API anahtarı
package + imza SHA-1 uygulama kısıtlarıyla sınırlandırılmalıdır. Play uygulama imzası
ile upload imzasını karıştırmayın. Debug anahtarını ayrı staging projesinde tutun.
API allowlist'te kullanılan Firebase API'leri kalmalı; gereksiz ücretli Google API
anahtarları bu Android anahtarıyla birleştirilmemeli. Auth ve token yenileme dahil
SDK işlemlerini kısıtlardan sonra gerçek cihazda tekrar deneyin. Firebase API key
kullanıcı yetkilendirmesi yerine geçmez; Auth, Rules ve App Check birlikte gerekir.

Auth tarafında e-posta enumeration korumasını ve password policy'yi açın. SMS Auth
henüz kullanılmıyorsa SMS sağlayıcısını açmayın; açılırsa region policy'yi hedef
ülkelerle sınırlayın, SMS quota ve billing alarmı kurun. Identity Toolkit quota'ları,
App Check geçersiz token oranı, Firestore read/write ve Storage trafiğine uyarı
kurun. Bütçe alarmı harcamayı otomatik durdurmaz. İstemci cooldown'ı sunucu spam
kontrolü değildir. Mesaj/ilan/teklif gibi istemcinin doğrudan yazdığı Firestore
verileri için Rules içerik/UID doğrulaması yapılır; genel trafik rate limit'inin
kanıtı olarak sunulmaz. Güçlü günlük UID/kaynak limitleri gerektiğinde yazma
işlemini App Check doğrulayan transactional callable backend'e taşıyın.

[Firebase API key kısıtları](https://firebase.google.com/docs/projects/api-keys)

## Doğrulanabilir canlı ayar denetimi

`scripts/audit_firebase_production.py` app kaydını ve yetkili Cloud API'den alınan
JSON delilini karşılaştırır. Cloud API'leri bu script **değiştirmez**. Yetkili operatör
şu salt okunur GET yanıtlarını özel bir JSON dosyasında aşağıdaki property'lere
koyar. Access token veya hizmet hesabı anahtarı bu dosyaya/depo içine konulmaz.
API key string içeren delil dosyasını da commit/CI artifact yapmayın.

| Property | Salt okunur API yanıtı |
| --- | --- |
| `appCheckServices` | `firebaseappcheck.googleapis.com/v1/projects/PROJECT_NUMBER/services` listesinin tüm sayfaları birleştirilmiş yanıtı |
| `playIntegrityConfig` | `firebaseappcheck.googleapis.com/v1/projects/PROJECT_NUMBER/apps/FIREBASE_APP_ID/playIntegrityConfig` |
| `androidApiKey` | `apikeys.googleapis.com/v2/projects/PROJECT_NUMBER/locations/global/keys/KEY_ID` |
| `androidApiKeyString` | aynı KEY_ID için `.../keys/KEY_ID/keyString` yanıtı; config'teki anahtarla eşleşme denetlenir, değer yazdırılmaz |

```bash
python3 scripts/audit_firebase_production.py --config app/google-services.json --cloud-evidence /private/firebase-cloud-evidence.json
python3 -m unittest discover -s test/config -p 'test_*.py'
```

Delil olmadan audit başarısız olur. Farklı projenin enforcement ayarı, eski
package, kısıtsız anahtar veya denetlenen anahtardan farklı app anahtarı da
başarısızdır. Script anlık Cloud ayarlarını, imza parmak izi kaydını veya Play Cloud
bağlantısını kendi başına kanıtlamaz; Play cihaz testi ve Console kontrolü gerekir.

[App Check service API](https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.services),
[Play Integrity config API](https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.playIntegrityConfig),
[API key restrictions API](https://cloud.google.com/api-keys/docs/reference/rest/v2/projects.locations.keys)
