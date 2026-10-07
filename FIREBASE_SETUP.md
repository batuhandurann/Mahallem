# Firebase, Android ve CI doğrulaması

Uygulama Authentication ile giriş yapar ve `mahallem` adlı Firestore veritabanını
kullanır. Room/demo ilanları, otomatik teklifler ve otomatik yanıtlar kaldırıldı.
Eski Room kayıtları gerçek UID taşımadığı için otomatik sunucuya gönderilmez.

## Koleksiyonlar

- `users/{uid}`: sadece sahibinin okuyabildiği özel profil.
- `providers/{id}`, `requests/{id}`: giriş yapmış kullanıcıların okuyabildiği
  ilanlar. Sahip UID'si sabittir; açık adres/telefon genel akışta yoktur.
- `providerContacts/{id}`: sadece ilan sahibinin telefon kaydı.
- `requestContacts/{id}`: talep sahibi ve kabul edilmiş teklifin sağlayıcısı
  okuyabilir. Otomatik iletişim paylaşım ekranı henüz yoktur; metin sohbeti kullanılabilir.
- `quotes/{id}`: sadece müşteri ve sağlayıcı UID'lerinin özel teklifleri.
  Sağlayıcı yalnız kendi hizmet ilanı adına teklif verir. Kabul transaction'ı
  ilanı ve teklifi birlikte değiştirir. Bir ilana bir teklif kabul edilir.
- `conversations/{id}/messages/{id}`: yalnız iki katılımcı. Gönderen UID'si
  oturuma eşittir. Mesajın bana ait olması okuyucunun UID'sinden hesaplanır.
- `users/{uid}/favorites`: hesap bazında favoriler; `reports`: UID bağlı raporlar.

Ödeme/escrow, ses ve fotoğraf yükleme bağlı backend olmadan başarı üretmez.
İstemci puan, doğrulama/sertifika rozeti, makbuz veya ödeme durumu üretemez.
Chat içindeki fiyat mesajı bir ödeme veya kabul edilmiş iş teklifi değildir.

## Canlı Firebase

E-posta/şifre Auth sağlayıcısını açın. `app/google-services.json` application ID
ile eşleşmelidir: `com.aistudio.mahallemde.kxqrvz`.

```bash
npm ci
npx firebase deploy --only firestore --project mahallem-batuhandurann-261007
```

Bu komut yetkili Firebase oturumu gerektirir. GitHub commit'i canlı Firebase
kurallarını dağıtmaz. Eski kurallar yeni ilan/teklif/sohbet erişimini reddeder.
Yeni kuralları yeni uygulama sürümünden önce dağıtın. Yönetici anahtarı depoya koymayın.

## Build ve test

Gerekenler: JDK 21, Node 22+, Android SDK `platforms;android-36.1` ve
`build-tools;36.0.0`. Windows'ta `./gradlew` yerine `gradlew.bat` kullanın.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
npm ci
npm run test:rules
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
kontrollerden biri başarısız veya iptal olursa başarısız olur. Hızlı ardışık
commitlerde eski çalışma iptal edilir; son commit kontrolü esas alınır. Raporlar
ve APK Actions artifact'lerine yüklenir.

Workflow dosyası tek başına main dalını korumaz. GitHub Settings → Rules → Rulesets:
`main` için Active ruleset, pull request zorunlu, status check `required-checks`
zorunlu, güncel branch zorunlu, force push ve dal silme engelli, bypass kapalı.
GitHub bağlantısındaki araçlar ruleset/branch protection yazma işlemi sunmuyor;
bu ayarın uygulandığını ayrıca doğrulayın.

## Kapsam sınırları

Akışlar şu an koleksiyonları dinleyip yerelde filtreler. Büyük veri için sunucuda
filtreleme, sayfalama ve yük testi gerekir. Moderasyon, spam/rate limit, App Check,
ödeme sağlayıcısı, push bildirim ve kimlik doğrulama rozetleri tamamlanmış değildir.
Firestore emulator üretimdeki bütün index/transaction/App Check davranışlarını
birebir taklit etmez; staging ve gerçek cihaz onayı yayın öncesi gereklidir.
