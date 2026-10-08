# Burada Android uygulama kimliği

| Alan | Kanonik değer |
| --- | --- |
| Kullanıcıya görünen ad | Burada |
| Android application ID | `com.batuhanduran.burada` |
| Kotlin namespace / paket kökü | `com.batuhanduran.burada` |
| Gradle proje adı | `Burada` |
| Mevcut Firestore database ID | `mahallem` |

Firestore database ID bir marka metni değildir. Mevcut Auth kullanıcıları ve
veritabanı korunur; database ID değiştirmek ayrı veri taşıma işlemi gerektirir.
GitHub depo adı da Android application ID değildir.

## Firebase Android uygulama kaydı

Depoda gelen `app/google-services.json`, eski
`com.aistudio.mahallemde.kxqrvz` Android kaydına aittir. JSON içindeki package name
elle değiştirilmez: Firebase Console'da mevcut proje altında yeni Android
uygulaması `com.batuhanduran.burada` package name ile kaydedilir ve bu uygulamanın
gerçek `google-services.json` dosyası indirilir.

1. Firebase Console → Project settings → Your apps → Add app → Android.
2. Android package name: `com.batuhanduran.burada`; uygulama adı: `Burada`.
3. Gerçek debug/release sertifikalarının SHA-1 ve SHA-256 parmak izlerini ekleyin.
   Play Store dağıtımında Play App Signing sertifikası ile upload sertifikası
   farklı olabilir; uygulamayı çalıştıracak imzanın parmak izi gereklidir.
4. İndirilen dosyayı `app/google-services.json` olarak yerleştirin. Mevcut proje
   altında yeni Android app eklemek Auth kullanıcılarını silmez.
5. Firebase App Check → yeni Burada Android uygulaması → Play Integrity provider
   kaydını ve üretim sertifikasını yapılandırın. API anahtarındaki Android
   uygulama kısıtlarını yeni package name ve ilgili SHA-1 ile eşleştirin.
6. Release build, imzalı fiziksel cihaz, Auth/Firestore/Storage ve App Check
   metriklerini staging ortamında doğrulayın; enforcement yalnız istemci
   tokenlarının kabul edildiği doğrulandıktan sonra açılır.

API anahtarını veya Firebase app ID değerlerini başka uygulama kaydından kopyalayıp
yeni kayıt varmış gibi kullanmayın. Backend servis hesabı anahtarı depoya girmez.

## Test ve kurulum davranışı

Debug emulator modu `-PfirebaseEmulators=true` ile yalnız `demo-mahallem`
projesini kullanır; canlı Firebase Android app kaydı gerektirmez. Release için
gerçek yeni Android kaydı ve eşleşen yapılandırma zorunludur.

Application ID değiştiği için eski package name ile kurulan uygulama yeni APK
tarafından yerinde güncellenmez. Android iki uygulamayı ayrı kurulum sayar.
Room demo verisi sunucuya otomatik taşınmaz; gerçek kullanıcı yeniden oturum açar.
Eski Firebase Android app kaydını ve veritabanını bu kaynak kodu değişikliği adına
silmeyin. Play Console'da ilk yayın yeni application ID ile yapılmalıdır.

## Kaynak belgeler

- [Android Firebase kurulum ve kayıt](https://firebase.google.com/docs/android/setup)
- [Google Services plugin: package name ile client seçimi](https://firebase.google.com/docs/android/google-services-plugin-and-file)
