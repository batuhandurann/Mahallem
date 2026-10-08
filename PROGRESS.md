# Mahallem ilerleme kaydı

Güncelleme: 2026-10-08 (Europe/Istanbul). Bu özet doğrulanmış durumu gösterir; yeni oturumda git ve CI ile karşılaştır.

## Dal ve kapsam
- İncelenen kaynak: `qa/mahallem-test-suite`, commit `3104078978b21c990b47895ede3426d422c70212`.
- `main`: `b2351563bbc358994267dbf9a261d7831a36adca`. [PR #1](https://github.com/batuhandurann/Mahallem/pull/1) açık, Draft ve birleştirilmemiş (bu oturumda doğrulandı).
- Bu görev: kök `AGENTS.md` çalışma kuralları ve bu kısa ilerleme kaydı oluşturuldu. Uygulama kodu değiştirilmedi.

## Tamamlananlar ve doğrulanmış testler
Kaynak commit `3104078` için [QA #705](https://github.com/batuhandurann/Mahallem/actions/runs/37750384340) başarılı:
- `unit-and-static`: unit test, lint, debug APK build, artifact doğrulama ve yükleme başarılı.
- `release-build`: imzasız release APK ve AAB build, artifact doğrulama ve yükleme başarılı.
- `ui-tests`: mevcut Compose testleri Android emülatöründe başarılı; rapor yüklemesi başarılı.
- `backend`: backend testleri ve production dependency audit başarılı.
- `firebase-rules`: Firebase emülatöründeki güvenlik kuralları testleri başarılı.

Aynı commit için [Security #572](https://github.com/batuhandurann/Mahallem/actions/runs/37750384412) başarılı: CodeQL JavaScript, CodeQL Java/Kotlin ve Secret scan.
Önceki Compose UI kırmızısı güncel CI'de görülmüyor; kanıt olmadan tekrar düzeltmeye çalışma.
Bu doküman görevi için içerik ve `git diff --check` kontrolü yapıldı. Uygulama testleri yeniden çalıştırılmadı; yukarıdaki kanıtlar doküman commit'inden önceki kaynak commit'e aittir.

## Açık sorunlar / kanıt eksikleri
- Gerçek cihaz ve gerçek Firebase/staging akışlarında kayıt/giriş, yanlış şifre, bağlantı kesilmesi, çıkış/tekrar giriş, process death, ekran döndürme ve background/foreground için uçtan uca kanıt bu oturumda doğrulanmadı. Mevcut Compose UI başarısı bu kapsamın tamamını kanıtlamaz.
- İki hesabın özel ilan/teklif/mesajlarının birbirine geçmediğine ilişkin Android uçtan uca kanıt eksik; Rules testleri tek başına yeterli değildir.
- Release çıktıları imzasızdır. Güvenli production config, imzalı yayın build'i ve staging/production servis doğrulaması ayrı işlerdir; deployment yapıldığı iddia edilmez.
- Main'in zorunlu status check/merge koruması bu doküman görevinde yeniden doğrulanmadı. Eski yetki hatasını güncel ayarın kesin yokluğu olarak yorumlama; mevcut ruleset'i kontrol et.
- Sıfırdan clone + güvenli config + tek komut build akışının tam kabul kanıtı ayrıca kontrol edilmeli; başarılı CI job'ları kapsamı aşan iddia için kullanılmamalı.

## Sıradaki tek görev
Mevcut staging/physical-smoke workflow ve testlerini incele; iki hesapla tüm auth/lifecycle senaryolarını çalıştırıp cihaz, commit ve test raporunu kaydet. Cihaz veya güvenli config yoksa engeli açıkça yaz; tamamlandı işaretleme.

## P0 entegrasyon çalışması — 2026-10-08
- Ayrı dal: `agent/qa-ci-p0-integration-20261008`, taban `10422de8d3b4bd53b97f8b24ae62a2bfa9a92b2e`. Eski başarılı CI bu yeni yamaların kanıtı değildir.
- Hazırlanan `59d45cc` wrapper yaması güncel QA'ya port edildi; QA/CodeQL build'leri wrapper kullanır. Security PR filtresine QA hedefi eklendi; JDK 21, wrapper JAR SHA-256 ve Gradle dağıtım SHA-256 pinleri doğrulanır.
- PR #14'ün build evidence doğrulayıcısı ve 6 testi yeniden kullanıldı; sahte/eksik APK/AAB, eksik DEX ve farklı checkout SHA reddedilir.
- PASS: `python3 -m unittest discover -s scripts/tests -v` 12/12, wrapper checksum ve Bash syntax, `git diff --check`. Wrapper pinleri resmi Gradle 9.3.1 checksum endpointleriyle karşılaştırıldı.
- NOT RUN: yeni commit'in Android CI, signed production, fiziksel cihaz ve gerçek staging doğrulamaları. CI sonuçları geldikten sonra bu kayıt güncellenecek.
- Main ruleset #24702630 aktif, sekiz check zorunlu, strict=true, bypass yok. QA hedef dalına aynı korumanın uygulandığı henüz kanıtlanmadı; başarısız merge denemesi yapılmadı (merge yasak).
- Sonraki iş: production feed helper bağlantısı ve güvenli sohbet medya erişim iptali için üretim kodu/negatif testler.

### Feed ve medya yaması
- HomeScreen artık PR #35'teki production `isSelectedFeedEmpty()` helper'ını çağırır. Kopya mantık yerine helper'ı sınayan 7 unit test ve gerçek HomeScreen'i render eden 4 Compose testi eklendi; bu testler yeni CI'da çalıştırılmalı.
- Chat Storage doğrudan okumaları kapalıdır. Yeni `readChatAttachment` callable'ı App Check, Auth disabled/verified, aktif profil, güncel sohbet üyeliği, iki yönlü blok, validated grant/path ve okuma kotasını kontrol eder; dosya I/O sonrası yetkiyi tekrar kontrol eder. Byte yanıtı verir, download URL üretmez. Android repository bu fonksiyonu kullanabilir; mevcut upload yolları korunur.
- Image validator yeni chat dosyalarını Admin SDK ile `privateChatAttachments` alanına taşıyıp token'lı yükleme nesnesini siler. Üretimde önceden oluşmuş token'lı dosyalar için sahip tarafından migration/envanter doğrulaması gereklidir; bu oturumda canlı dosyalara dokunulmadı.
- Auth emülatörü kayıt/aynı e-posta/yanlış şifre/çıkış/tekrar giriş testleri; üretim handler'larıyla 13 gerçek Auth/Firestore/Storage medya testi eklendi. Silinen üyelik, disabled/deleting/purging hesaplar, bloklar, grant iptali, path, kota, gerçek bearer URL iptali ve purge temizliği testleri var. Test projesi `demo-mahallem-rules-test`, canlı servis fallback'i yok.
- PASS (yerel): TypeScript build + mevcut 5 backend test entry; 12 Python test. NOT RUN: yeni emülatör testleri yerelde JDK 21 bulunmadığından CI'ya bağlı; yeni Android unit/Compose testleri Android SDK olmadığından CI'ya bağlı.
- Production preflight FAIL: QA hâlâ `com.example` namespace ve `com.aistudio.mahallemde.kxqrvz` applicationId kullanıyor; hukuki metin placeholder, onaylı yayın version/URL/config yok. PR #10'un farklı applicationId/veritabanı mimarisi bu QA dalına körlemesine taşınmadı.
- Sonraki iş: PR #36'nın son SHA QA/Security sonuçları ve APK/AAB/test artifactlerini doğrula; ardından P0/P1 durumlarını commit/run bağlarıyla kaydet.

### CI'da yakalanan token regresyonu ve P1 kapıları
- `2b30862` için [QA 37778359117](https://github.com/batuhandurann/Mahallem/actions/runs/37778359117) Firebase job FAIL: Auth, Firestore, Storage ve ilk 10 medya testi geçti; metadata'da token silmenin eski token'ı iptal ettiği kanıtlanamadı. Test atlanmadı: private kopya + kaynak nesne silme düzeltildi ve gerçek eski-token HTTP 200→404 testine dönüştürüldü. Yeni CI sonucu beklenir.
- Production workflow'a onaylı Firebase projectId/applicationId, Firebase Android client eşleşmesi, Play upload sertifikası SHA-256 eşleşmesi ve gerçek AAB signer doğrulaması eklendi. Protected production vars yoksa fail-closed. Secrets değiştirilmedi, workflow dispatch edilmedi.
- Yerelde 18 Python test PASS, 3 gerçek JAR imza testi NOT RUN (jarsigner yok); CI JDK 21'de bu üç test zorunlu ve araç eksikse skip yerine FAIL. Fixture anahtarı production key değildir.
- Güncel repo okumasında `main` protected=true, `qa/mahallem-test-suite` protected=false. Main ruleset yalnızca main'i kapsıyor. QA'da başarısız check varken merge engelini kanıtlayamayız; admin koruması eksik. Bu oturum hiçbir merge işlemi yapmaz.
- Fiziksel Android cihaz/ADB, production/staging Console yetkisi, onaylı kimlik ve upload sertifikası verisi yok. Signed/device/staging/Play doğrulamaları NOT RUN; eski mimarileri otomatik birleştirme yapılmadı.
