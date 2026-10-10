# Firebase, Android ve CI doğrulaması

Uygulama Authentication ile giriş yapar ve `mahallem` adlı Firestore veritabanını
kullanır. Room/demo ilanları, otomatik teklifler ve otomatik yanıtlar kaldırıldı.
Eski Room kayıtları gerçek UID taşımadığı için otomatik sunucuya gönderilmez.

## Koleksiyonlar

- `users/{uid}`: sadece sahibinin okuyabildiği özel profil.
- `providers/{id}`, `requests/{id}`: `visibility=published` ilanlar keşfette
  giriş yapmış kullanıcılara görünür. Gizlenen ilanları yalnız sahipleri okuyabilir.
  Sahip UID'si sabittir; açık adres/telefon genel akışta yoktur.
- `providerContacts/{id}`: sadece ilan sahibinin telefon kaydı.
- `requestContacts/{id}`: talep sahibi ve kabul edilmiş teklifin sağlayıcısı
  okuyabilir. Otomatik iletişim paylaşım ekranı henüz yoktur; metin sohbeti kullanılabilir.
- `quotes/{id}`: sadece müşteri ve sağlayıcı UID'lerinin özel teklifleri.
  Sağlayıcı yalnız kendi hizmet ilanı adına teklif verir. Kabul transaction'ı
  ilanı ve teklifi birlikte değiştirir. Bir ilana bir teklif kabul edilir.
- `conversations/{id}/messages/{id}`: yalnız iki katılımcı. Gönderen UID'si
  oturuma eşittir. Mesajın bana ait olması okuyucunun UID'sinden hesaplanır.
- `users/{uid}/favorites`, `blocks`, `devices`: hesap bazında favori, engel ve cihaz kayıtları.
- `reports`: UID bağlı raporlar. Yetkili moderatör callable backend ile inceler;
  karar ve denetim kaydı transaction ile yazılır. İstemci moderatör rolü üretemez.

Fotoğraflar App Check korumalı callable backend üzerinden JPEG olarak doğrulanır,
EXIF/GPS temizlenir ve özel Storage alanına yüklenir. Okuma her istekte UID/engel
kontrolünden geçer; genel indirme URL'si üretilmez. Metin sohbeti Firestore realtime
listener kullanır. Push yalnız aktif hesabın UID'sine yönelik veri bildirimi işler.
Ödeme/escrow ve ses bağlı backend olmadan başarı üretmez.
İstemci puan, doğrulama/sertifika rozeti, makbuz veya ödeme durumu üretemez.
Chat içindeki fiyat mesajı bir ödeme veya kabul edilmiş iş teklifi değildir.

## Canlı Firebase

E-posta/şifre Auth sağlayıcısını açın. `app/google-services.json` application ID
ile eşleşmelidir: `com.batuhanduran.burada`. 10 Ekim 2026'da gerçek Firebase
projesinde bu paket adına ait aktif Yakıno Android kaydı CLI ile doğrulandı.
Doğru yapılandırma yerel olarak indirildi; eski yapılandırma Git takibinden
çıkarıldı. Yeni bir checkout yapılandırmayı korunan erişimle yeniden indirmelidir:

```bash
npx firebase-tools@15.32.1 apps:sdkconfig ANDROID 1:212821509565:android:623fe6723afb4c93ddbed4 --project mahallem-batuhandurann-261007 --out app/google-services.json
```

Dosyayı depoya eklemeyin; CI ve yayın ortamında korunan yapılandırmayı kullanın.
Elle package name değişikliği gerçek Firebase kaydı oluşturmaz.
Ayrıntılar: [Android kimlik geçişi](BRANDING_FIREBASE_MIGRATION.md). Canlı debug ve
release derlemeleri eşleşen yapılandırma olmadan durur. Emulator debug derlemesi
programatik `demo-mahallem` yapılandırmasını kullanır.

```bash
npm ci
npm ci --prefix functions
```

Canlı dağıtım için [korunan dağıtım ve denetim akışını](FIREBASE_PRODUCTION_DEPLOY.md)
kullanın. GitHub commit'i canlı Firebase kurallarını dağıtmaz. Eski kurallar yeni
ilan/teklif/sohbet erişimini reddeder. Yeni kuralları yeni uygulama sürümünden önce
dağıtın. Yapılandırma indirilmesi App Check, gerçek SMS, FCM veya dağıtım kanıtı
değildir. Yönetici anahtarı depoya koymayın.

## Build ve test

Gerekenler: JDK 21, Node 22+, Android SDK `platforms;android-36.1` ve
`build-tools;36.0.0`. Windows'ta `./gradlew` yerine `gradlew.bat` kullanın.

```bash
./gradlew -PfirebaseEmulators=true :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
npm ci
npm ci --prefix functions
npm run test:backend
npm run test:rules
npm run test:media
```

Rules birim testleri test SDK'sının desteklediği varsayılan emulator veritabanına
aynı kuralları yükler. Gerçek Auth token entegrasyon testi ayrıca uygulamanın
`mahallem` veritabanında çalışır ve başka profil erişiminin reddedildiğini doğrular.
Üretim hesabına istek gönderilmez. Emulator projesi yalnız `demo-mahallem`'dir.

Android emulator başlatın (`10.0.2.2` ana bilgisayar adresidir):

```bash
npx firebase emulators:exec --project demo-mahallem --only auth,firestore './gradlew -PfirebaseEmulators=true :app:connectedDebugAndroidTest'
```

Android testi gerçek Android Firebase SDK'sıyla kayıt, yanlış şifre, giriş/çıkış,
ilan, sağlayıcı, teklif, sohbet, hesap değiştirme ve teklif kabulünü çalıştırır.
`firebaseEmulators=true` olmadan bu test güvenlik amacıyla hata verir. Release
build'inde emulator bağlantısı daima kapalıdır.

## USB ile gerçek cihaz

USB hata ayıklamayı açın; `adb devices` cihazı `device` durumunda göstermelidir.
Telefonun USB izin isteğini kabul edin. Birden fazla cihaz varsa `ANDROID_SERIAL`
ile hedef cihazı seçin.

```bash
adb reverse tcp:9099 tcp:9099
adb reverse tcp:8080 tcp:8080
npx firebase emulators:exec --project demo-mahallem --only auth,firestore './gradlew -PfirebaseEmulators=true -PemulatorHost=127.0.0.1 :app:connectedDebugAndroidTest'
```

Ayrıca ağ kesintisi, arka plana alma/yeniden açma, klavye, küçük ekran ve izin
kontrollerini fiziksel cihazda yapın. Emulator sonucu gerçek cihaz sonucu değildir.

## GitHub Actions ve gerçek merge engeli

`Android Quality` tüm branch push ve pull request'lerinde build, unit test, lint,
Auth/Rules ve Android emulator testlerini çalıştırır. `required-checks` diğer
kontrollerden biri başarısız veya iptal olursa başarısız olur. Her commit kendi
kontrolünü çalıştırır. Raporlar
ve APK Actions artifact'lerine yüklenir.

Workflow dosyası tek başına main dalını korumaz. GitHub Settings → Rules → Rulesets:
`main` için Active ruleset, pull request zorunlu, status check `required-checks`
zorunlu, güncel branch zorunlu, force push ve dal silme engelli, bypass kapalı.
GitHub bağlantısındaki araçlar ruleset/branch protection yazma işlemi sunmuyor;
bu ayarın uygulandığını ayrıca doğrulayın.

## Kapsam sınırları

Mahalle modeli İzmir/Buca'da beş mahallelik pilot katalogla başlar. Hassas koordinat
genel ilanda saklanmaz; yalnız yaklaşık geohash paylaşılır. Türkiye geneli katalog,
harita altyapısı ve sunucu tarafı sayfalama/yük testi henüz tamamlanmadı.
Production App Check sağlayıcısı release kodunda Play Integrity'dir; Firebase/Play
Console kaydı, SHA-256, enforcement ve API kısıtları ayrıca uygulanmalıdır.
[Yayın güvenlik kontrolleri](FIREBASE_SECURITY_RELEASE.md) erişim ve kanıt gereksinimlerini açıklar.
Backend fotoğraf ve push kotaları içerir; mesaj/ilan/rapor yazma akışları için tam
sunucu tarafı abuse koruması ve üretim yük doğrulaması henüz tamamlanmadı.
Ödeme sağlayıcısı ve kimlik doğrulama rozetleri henüz bağlı değildir.
Firestore emulator üretimdeki bütün index/transaction/App Check davranışlarını
birebir taklit etmez; staging ve gerçek cihaz onayı yayın öncesi gereklidir.
