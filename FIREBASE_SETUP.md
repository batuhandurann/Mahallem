# Mahallem Firebase kurulumu

## Proje

- Firebase proje kimliği: `mahallem-batuhandurann-261007`
- Android uygulama kimliği: `com.aistudio.mahallemde.kxqrvz`
- Firestore: `mahallem`, Enterprise Native, Frankfurt (`europe-west3`)
- Authentication: e-posta ve şifre
- Android SDK: Firebase BoM `34.19.0` (Auth `24.2.0`, Firestore `26.6.0`)

`app/google-services.json` Android yapılandırmasını içerir. Bu dosyadaki Firebase
istemci API anahtarı uygulama yapılandırmasıdır; servis hesabı anahtarı değildir.
Servis hesabı anahtarlarını, CLI oturum bilgilerini ve özel API anahtarlarını
depoya eklemeyin.

## Uygulama akışı

Uygulama açılışında Firebase oturumu kontrol edilir. Kullanıcı kayıt olabilir,
giriş yapabilir, şifresini e-posta ile yenileyebilir ve çıkış yapabilir. Kayıtta
ad bilgisi Firebase Auth'a yazılır; doğrulama e-postası gönderilmeye çalışılır.
Doğrulama e-postası başarısız olursa hesap kullanılabilir ve durum kullanıcıya
bildirilir. Özel profile erişim için e-posta doğrulaması şart değildir.

Girişten sonra `users/{uid}` profil dokümanı bir Firestore transaction'ı ile
oluşturulur veya güncellenir. Alanlar `uid`, `displayName`, `email`, `createdAt`
ve `updatedAt` ile sınırlıdır. E-posta oturum token'ından alınır; zamanlar sunucu
tarafından yazılır. Profil kaydı başarısızsa uygulama hata ve yeniden deneme
seçeneği gösterir. Profil önbelleği yalnız bellekte tutulur.

İlanlar, teklifler, sohbetler ve ödeme simülasyonları mevcut Room demo verisi
olarak bu cihazda kalır. Bu veriler aynı cihazdaki hesaplar arasında ortaktır;
uygulamanın hesap başlığında bu durum belirtilir. Gerçek ödeme işlenmez.
Hesap değişiminde ekran/ViewModel durumu ve bekleyen işlemler temizlenir.
Ekran döndürülünce aynı hesabın ViewModel durumu korunur.

## Kurallar ve dağıtım

`firestore.rules` yalnız oturum sahibinin kendi profilini okuyup yazmasına izin
verir; alan tiplerini, boyutlarını, sahipliği ve zamanları kontrol eder. Profil
silme, ek alanlar ve diğer doküman yolları kapalıdır. Kurallar ilk sürümdür;
uygulamayı geniş kitleye açmadan önce gözden geçirilmelidir.

Firebase CLI ile:

```sh
npx --yes firebase-tools@latest deploy --only auth
npx --yes firebase-tools@latest deploy --only firestore --dry-run
npx --yes firebase-tools@latest deploy --only firestore
```

`.firebaserc` bu projeyi seçer. Doğrudan UID ile doküman işlemleri yapıldığı için
özel indeks, koleksiyon sorgusu veya Enterprise pipeline kullanılmaz.

Kurulum sırasında Authentication etkinleştirildi, Firestore oluşturuldu,
kurallar sunucuda derlendi ve kurallar/indeks yapılandırması yayımlandı.
Android derlemesi ve cihaz üzerinde giriş akışı bu ortamda çalıştırılmadı.
