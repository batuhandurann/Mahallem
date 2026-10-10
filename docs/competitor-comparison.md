# Yakıno: güncel ürün karşılaştırması

İnceleme: 10 Ekim 2026, `main` kaynağı `4995bd0` ve bu denetimin düzeltmeleri.
Görünen marka Yakıno; Mahallem depo/veritabanı ve Android paket kimliği uyumluluk
için korunur. Resmi, herkese açık sayfalar incelendi. Rakip hesaplarına giriş veya
sunucularına güvenlik testi yapılmadı. İç mimarileri ve açıkları hakkında sonuç
üretilmez. Uygulama mahalle hizmetleri içindir; ikinci el ürün pazaryeri değildir.

## Resmi kaynaklarla karşılaştırma

| Ürün | Doğrulanan akış | Yakıno’da karşılığı ve kalan fırsat |
| --- | --- | --- |
| Armut | Kısa sorularla ihtiyaç, fiyat/yorum/işe özel mesajla teklif karşılaştırma. [Nasıl çalışır](https://info.armut.com/nasil-calisir) | Bulut talepleri, teklifler, sohbet ve tamamlanan işten yorum var. Kategori sorularını sadeleştirme ve karşılaştırılabilir teklif özeti geliştirilebilir. Rakibin garanti ifadeleri Yakıno’nun güvencesi değildir. |
| Fiverr | Platform içinde ödeme, mesaj/dosya, sipariş yorumları, çözüm ve iptal talepleri. [Güvenlik](https://www.fiverr.com/trust_safety), [yorum kuralları](https://help.fiverr.com/hc/en-us/articles/360049982353-Leaving-and-managing-reviews-on-Fiverr), [Resolution Center](https://help.fiverr.com/hc/en-us/articles/27274045277713-Using-the-Resolution-Center) | Özel sohbet/fotoğraf, müşteri onaylı bitirme, revizyon, karşılıklı iptal ve doğrulanmış yorum var. Yakıno yorumu yalnız uygun tamamlanan işin müşterisine açar. Gerçek ödeme, iade ve personelli uyuşmazlık çözümü eksik. |
| Bionluk | Özel teklif/satın alma, teslim, revizyon, müşteri onayı ve ardından ücret aktarımı. Bazı koşullarda otomatik onay. [Sipariş süreci](https://bionluk.com/destek/surec-nasil-isler-35?category=baslangic-34) | Teslim bildirimi → müşteri onayı/revizyon var; otomatik tamamlama veya ödeme havuzu yok. Evde hizmetin bitiş kanıtı dijital dosya tesliminden farklı olmalı. |
| Taskrabbit | İş/konum, saatlik fiyat ve yorumlarla seçim, zaman, sohbet ve uygulamada ödeme. [Nasıl çalışır](https://www.taskrabbit.com/how-it-works) | Tarih/saat, takvim görünümü ve iş durumları var. Çifte rezervasyonu önleyen ortak zaman dilimi rezervasyonu, yeniden planlama ve ödeme eksik. |
| Thumbtack | Yerel profil, doğrulanmış yorum, fiyat görüşme ve hizmet veren seçme. [Nasıl çalışır](https://www.thumbtack.com/how-it-works) | Profil/teklif/sohbet/iş yorumları var. Saatlik, başlangıç ve toplam fiyat ayrımı daha belirgin olabilir. Telefon doğrulaması mesleki belge veya genel güvenlik doğrulaması değildir. |
| Nextdoor | Local Faves ile doğrulanmış komşuların işletme tavsiyeleri; kapsam ülkeye göre değişir. [Resmi duyuru](https://blog.nextdoor.com/small-business-improvements) | Kayıtlı mahalleyle keşif var. Adres/mahalle sakinliği doğrulaması yok. Komşu tavsiyesi eklenecekse tamamlanmış iş yorumundan ayrı gösterilmeli. |
| Almundo | Uçuş karşılaştırma, rezervasyon ayrıntıları ve satın alma durumu. [Resmi Android kaydı](https://play.google.com/store/apps/details?hl=es&id=com.almundo.almundo_app) | Hizmet pazaryeri rakibi değildir; birleşik rezervasyon özeti için örnek. İşin fiyat, zaman, taraf, durum ve destek yolu bir özette sunulabilir. |
| Lediboss | Bu yazımla kastedilen ürün güvenilir biçimde belirlenemedi. | Doğru bağlantı olmadan özellik veya güvenlik iddiası üretilemez. |

Bu öneriler kaynaklardan çıkarılan ürün değerlendirmesidir. Rakibin garanti,
kimlik incelemesi veya ödeme hizmeti Yakıno’ya kendiliğinden aktarılmaz.

## Güncel kaynak kodunun durumu

| Alan | Uygulanmış | Kalan sınır |
| --- | --- | --- |
| Hesap | Firebase Auth, özel profil, UID geçiş koruması, e-posta gönderme/kontrol, mevcut hesaba telefon bağlama | Gerçek SMS ve üretim ayarları cihazda doğrulanmalı. Telefon doğrulaması meslek/mahalle sakinliği kanıtı değildir. |
| İlan/teklif | Firestore ortak ilan/talep, yayımlanmış keşif, özel iletişim, atomik tek teklif kabulü | Yerel Room pazaryeri değildir. Üretim Rules/Functions dağıtımı ayrıca kanıtlanmalı. |
| Sohbet/fotoğraf | Katılımcıya özel mesaj, özel fotoğraf, engel/şikâyet. Kimlik bağlama, taslak koruma ve tekrar gönderim kimliği düzeltildi | Ses/aramanın gerçek servisi yok; kontroller devre dışı. Taslaklar UID belleğinde; süreç öldürülünce geri yüklenmez. |
| Teklif doğruluğu | Ad/unvan/puan işlem içindeki güncel hizmet veren kaydından alınır; Rules eşitlik ve görünürlüğü kontrol eder | Mesleki belge incelemesi sağlamaz. Gizli veya kapalı ilan için yeni teklif reddedilir. |
| İş | Sunucu kontrollü başlatma, teslim, müşteri onayı/revizyon, karşılıklı iptal, özel olay geçmişi/idempotans | Ortak randevu rezervasyonu, iş olaylarına özel push ve destek hakemliği eksik. |
| Yorum | Yetkili tamamlanma olayı, müşteri için 30 günlük tek yorum, sunucu toplamları, raporlama/moderatör kaldırma/audit | Ayrı moderatör ekranı, gizlenen yorumu geri getirme ve hizmet veren yanıtı yok. |
| Moderasyon | Merkezi rapor, hedef/üyelik doğrulaması, sunucu yetkisi ve yorum kuyruğu | Personelli destek, kullanıcıya takip edilebilir bilet ve kapsamlı uyuşmazlık yönetimi eksik. |
| Ödeme | Kullanılamadığı açık; teklif kabulü ödeme sayılmaz | Sağlayıcı tahsilatı/aktarım/webhook/iade/mutabakat bağlı değil. |
| Coğrafya | Buca Adatepe, Efeler, İnönü, Şirinkapı, Yiğitler pilotu; gerçek bulut ilanlarını mahalleyle gruplama | Türkiye geneli katalog, GPS, gerçek harita ve mesafe yok. Liste görünümü konum kanıtı değildir. |

Kod dayanakları: `MarketplaceRepository.kt`, `MarketplaceViewModel.kt`,
`ChatScreen.kt`, `MarketplaceMapView.kt`, `LocationModels.kt`,
`PhoneVerificationViewModel.kt`, `ModerationRepository.kt`, `firestore.rules`,
`functions/job-handler.js`, `functions/reviews.js`. Politikalar:
`JOB_LIFECYCLE.md`, `VERIFIED_REVIEWS.md`.

## Kalan ürün işleri ve kabul koşulları

1. Üretim pilotu: imzalı Android sürümü, gerçek cihaz, doğru Firebase kaydı,
   App Check ret kontrolü, dağıtım, SMS ve FCM teslimi. Emulator başarısı bunların
   yerine geçmez.
2. Randevu: ortak zaman dilimi, çifte rezervasyon engeli, yeniden planlama ve
   bildirim. Yarışan iki rezervasyondan yalnızca biri kesinleşmeli.
3. Destek: yetkili kuyruk ekranı, başvuru durumu, karar ve itiraz. Yetkisi kaldırılan
   moderatör işlem yapamamalı; kullanıcı başvurusunun gerçek durumunu görmeli.
4. Ödeme: sağlayıcı bağlantısı, imzalı/idempotent olaylar, iade ve mutabakat.
   Mağaza sözleşmesi, gizli anahtarlar ve gerçek operasyon olmadan tahsilat/garanti
   sunulduğu belirtilmemeli.
5. Coğrafya/iletişim: doğrulanmış katalog, isteğe bağlı konum/harita, kullanılacaksa
   gerçek ses/arama. İzin reddinde elle mahalle seçimi çalışmalı; özel adres/telefon
   yetkisiz kullanıcıya açılmamalı.
6. Akış kalitesi: kısa kategori formu, karşılaştırılabilir teklif, yükleme/tekrar
   deneme; küçük ekran, büyük yazı ve TalkBack incelemesi.

Bu belge kodda bulunan özelliklerle resmi ürün açıklamalarını karşılaştırır.
Entegre sürümün gerçek test/CI sonuçları revizyonlarıyla `PROGRESS.md` ve denetim
raporunda ayrıca tutulur. Önceki test sonraki değişikliğin kanıtı değildir.
Kaynaklar Yakıno’nun rakiplerden daha güvenli veya daha iyi olduğunu ölçmez.
