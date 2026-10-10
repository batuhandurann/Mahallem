# Yakıno: canlı sürüm erişimleri ve doğrulama

2026-10-10 yerel kontrolünde kanonik `com.batuhanduran.burada` Firebase
yapılandırması mevcut ve yapısal olarak geçerli. `adb devices` çıktısında bağlı
telefon yok. Yerel imzalama ortam değişkenleri mevcut değil. Bu kontrol GitHub
Environment sırlarının veya bulut yetkilerinin mevcut olmadığını kanıtlamaz.

## Güvenli ön kontrol

Python 3 ile depo kökünden çalıştırın:

```sh
python scripts/production_preflight.py
```

SDK PATH üzerinde değilse `--adb /tam/yol/adb` kullanın; Windows'ta `adb.exe`.
Araç yalnızca dosya/ortam girdilerinin varlığını, Firebase kayıt yapısını ve adb
telefon durumunu okur. Parolaları, dosya yollarını, cihaz kimliğini veya API
anahtarını yazdırmaz. Eksikler varsa çıkış kodu 2 ve `BLOCKED` verir.
`LOCAL_INPUTS_PRESENT` yalnızca ön koşulların varlığıdır; imza doğruluğu, canlı
SMS, FCM veya App Check başarısı değildir. Hiçbir uygulama yüklenmez.

## İmzalı Android sürümü

Mevcut yayın/upload anahtarı ve parolaları kullanılmalı; bu iş için yeni bir
anahtar üretilmedi. GitHub `production` Environment içinde mevcut iş akışının
beklediği sırlar:

- `GOOGLE_SERVICES_JSON_BASE64`: kanonik Android Firebase yapılandırması.
- `ANDROID_KEYSTORE_BASE64`: mevcut upload keystore.
- `ANDROID_KEYSTORE_PASSWORD` ve `ANDROID_KEY_PASSWORD`.
- `ANDROID_KEY_ALIAS`: gerçek anahtar alias'ı; iş akışındaki varsayılan `upload`.

Yerel karşılıklar `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.
Ön kontrol açık bir alias ister. Parolaları sohbet veya sürüm kontrolüne koymayın.
`Production Signed APK and AAB` iş akışı yalnızca manuel, incelenmiş `main`
üzerinde çalışır. Temiz gerçek yapılandırma ile APK/AAB üretir; iki imzayı verilen
upload sertifikasıyla doğrular ve 7 gün saklanan çıktıları sunar. Play Store'a
yükleme yapmaz. Play App Signing sertifikası upload sertifikasından farklı
olabilir; mağazadan kurulacak uygulamanın Firebase/SMS/App Check kaydı buna göre
ayrıca doğrulanmalıdır.

## Telefon, SMS ve bildirim

Android 7/API 24 veya üstü fiziksel telefon, USB hata ayıklama izni ve adb
yetkilendirmesi gerekir. Telefon bağlandıktan sonra:

```sh
python scripts/physical_android_smoke.py --preflight-only
```

Asıl `physical_android_smoke.py` Linux/bash araç zincirinde izole
`demo-mahallem` Auth/Firestore/Functions emülatörlerini başlatır, test APK'larını
yükler ve yaşam döngüsü testlerini çalıştırır. Windows'ta salt ön kontrol
desteklenir; tam çalıştırma bash/npm/Gradle komut uyumluluğu gerektirir. Bu
izole test gerçek SMS teslimini veya FCM'yi doğrulamaz.

Canlı sürümde onaylı test telefonuyla şu kanıtlar ayrıca toplanmalıdır:

- Gerçek SMS'in alınması, doğru kodla aynı UID'ye bağlanma, yanlış/süresi dolan
  kodun reddi ve yeniden gönderme sınırı.
- Bildirim izninin kabul/red durumları; uygulama açıkken ve arka plandayken
  gerçek bildirimin teslimi; bildirimin doğru özel sohbete açılması.
- Hesap değişiminden sonra eski hesabın bildirimine/sohbetine erişilememesi;
  cihaz token yenilenmesi ve çıkışta kaydın temizlenmesi.
- Play üzerinden kurulmuş sürümle Play Integrity/App Check doğrulaması ve
  attestation geçmeyen istemcinin reddi. Debug sağlayıcı bu kanıtın yerine geçmez.

Mevcut `Android Physical Test Lab Smoke` iş akışı `staging` Environment içinde
`FIREBASE_TEST_LAB_SERVICE_ACCOUNT`, faturalandırması açık izole GCP projesi ve
uygun fiziksel model gerektirir. Yalnızca seçili mahalle keşfi Compose testini
çalıştırır; bütün Android testleri veya gerçek SMS/FCM testi olarak raporlanmaz.

## Firebase üretim iş akışı

Bu ilk envanterden sonra 10 Ekim'de yetkili kurulum tamamlandı: telefon Auth
etkin, SMS allowlist yalnız TR, parola minimumu 8 ve e-posta sorgulama koruması
açık. Frankfurt özel Storage oluşturuldu; Firestore/Storage kuralları ve indeksler
doğrulanmış `f8ef198` kaynağından yayımlandı. 11 sunucu işlevi ACTIVE; iki özel
geçici koleksiyonda TTL ACTIVE. Canlı Rules içerikleri yerel doğrulanmış kaynakla
aynı SHA256'ya sahip. İlk dağıtımın Cloud kurulum hataları yeniden denemeyle
çözüldü; iki callable'ın yarım kalan IAM taşıma ayarı düzeltildi.

Kanonik API anahtarının Android izin listesi başlangıçta boştu. Mevcut geliştirme
APK sertifikaları Firebase'e kaydedildi ve anahtar kanonik paket/geliştirme SHA1
ile sınırlandı. Bu sertifika debug sertifikasıdır; Play üretim imzası değildir.
Firestore/Auth App Check halen UNENFORCED; gerçek telefon ve Play sürümü kanıtı
geldikten sonra kontrollü etkinleştirme gerekir. Gizli değerlerden arındırılmış
son kanıtlar `work/live-final-redacted-20261010.json` ve
`work/live-cloud-redacted-20261010.json` dosyalarındadır. Aşağıdaki ilk envanter
bulguları geçmiş durum olarak korunur.

2026-10-10 yetkili Firebase CLI oturumuyla yapılan salt okunur canlı envanterde
faturalandırma açık, Functions listesi boş, kanonik Android Play Integrity kaydı
mevcut (TTL 3600 saniye) bulundu. Auth e-posta girişi açık; telefon girişi kapalı,
parola politikası alanı mevcut değil, SMS bölge politikası allowlist türünde.
Firestore ve Auth için App Check `UNENFORCED`, Data Connect için `ENFORCED`.
Yapılandırılmış Storage bucket sorgusu 404 döndü. `cloud.firestore/mahallem`
Rules yayınının son güncellemesi 7 Ekim. Android kısıtlı bir API anahtarı yanında
kısıtsız başka anahtar da bulunuyor; hangisinin uygulamada kullanıldığı bu
envanterle kesinleşmedi. Bu durumlar düzeltilmiş veya üretim testi geçmiş olarak
sayılmaz. Gizli değerleri çıkarılmış yerel kanıt: çalışma alanındaki
`work/live-cloud-redacted-20261010.json`; rapor herhangi bir anahtar değeri
içermez. Sonraki canlı değişikliklerin sonucu ayrıca kaydedilmelidir.

Mevcut korumalı `firebase-production.yml` şu Environment değişkenlerini kullanır:
`FIREBASE_PRODUCTION_PROJECT_ID`, `FIREBASE_ANDROID_API_KEY_ID`,
`FIREBASE_ANDROID_SIGNING_SHA1`, `FIREBASE_ANDROID_SIGNING_SHA256`,
`GCP_WORKLOAD_IDENTITY_PROVIDER`, `FIREBASE_DEPLOY_SERVICE_ACCOUNT`.
Yapılandırma sırrı `GOOGLE_SERVICES_JSON_BASE64` ile birlikte gerçek proje ve
sertifikayla eşleşmelidir. WIF sağlayıcısı ve servis hesabı erişimi ayrıca
doğrulanır; Firebase CLI oturumu bulunması bu yetkileri kanıtlamaz.

## Bu değişiklikte doğrulananlar

Ön kontrolün 5 yeni testi dahil Python yapılandırma/cihaz/imzalama testleri
**65/65 geçti** (Windows, Python 3.12, JDK 21). Testler gizli değerlerin rapora
sızmamasını, sanal cihazın fiziksel sayılmamasını ve adb zaman aşımının başarı
sayılmamasını kapsar. Bu testler fixture kullanır; gerçek yayın veya telefon
kanıtı değildir. Canlı ödeme için ayrıca iyzico hesabı, ilgili ürün/sözleşme ve
korumalı sağlayıcı sırları gerekir; ödeme yetkisi bu ön kontrolle sınanmaz.
