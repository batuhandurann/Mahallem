# Mahallem ilerleme kaydı

Güncelleme: 2026-10-08 (Europe/Istanbul). Bu özet doğrulanmış durumu gösterir; yeni oturumda git ve CI ile karşılaştır.

## Güncel P0/P1 durumu — 20:25 Europe/Istanbul
- Çalışma dalı `agent/qa-ci-p0-integration-20261008`, [Draft PR #36](https://github.com/batuhandurann/Mahallem/pull/36), hedef `qa/mahallem-test-suite`. `7c4bc0222221bbd6e61217aecf45a0b5ce094655` için [QA 37779520658](https://github.com/batuhandurann/Mahallem/actions/runs/37779520658) beş job PASS; [Security 37779520647](https://github.com/batuhandurann/Mahallem/actions/runs/37779520647) üç job PASS. Sekiz kontrolün tümü tamamlandı.
- PASS: 21 Python doğrulayıcı testi (üç gerçek imza fixture testi dahil); unit/lint/debug build; unsigned release APK+AAB; backend; Auth/Firestore/Storage negatif testleri; 13/13 gerçek üretim medya handler testi (0 skip); Android API 35 emülatöründe 9/9 instrumentation testi (gerçek HomeScreen için dört yeni vaka dahil, 0 skip/fail). XML artifacti ayrıca indirip okundu.
- PASS: sıfırdan clone edilen `7c4bc02` checkout'ta wrapper pin doğrulaması ve `./gradlew --version` (9.3.1). Gradle cache paylaşıldı; temiz makinede tam Android build kanıtı olarak sunulmaz.
- Artifactler: [debug APK/rapor](https://github.com/batuhandurann/Mahallem/actions/runs/37779520658/artifacts/11551534656), [release APK+unsigned AAB](https://github.com/batuhandurann/Mahallem/actions/runs/37779520658/artifacts/11550989190), [emülatör XML/HTML](https://github.com/batuhandurann/Mahallem/actions/runs/37779520658/artifacts/11551524450), [Firebase kanıtı](https://github.com/batuhandurann/Mahallem/actions/runs/37779520658/artifacts/11551163813). Bunların checkout SHA'sı GitHub'ın PR merge SHA'sı `0d625cf65de5c520a53e550278b759e806c40530`; kaynak head `7c4bc02` ile karıştırılmamalı.
- PR #35'te kullanıcı P0/6 kapsamındaki yalnızca HomeScreen ve FeedEmptyStatePolicyTest güncellendi: [f89973a](https://github.com/batuhandurann/Mahallem/commit/f89973ac21be6df04447e2b079bc400b58c827a3), güncel `9246d4c` parent ve expected-head kontrolüyle. Yedi test kopya karar yerine üretim helper'ını çağırır; timestamp değişiklikleri korundu. İki PR için `git merge-tree --write-tree` PASS (çakışma yok). [PR #35 yeni QA](https://github.com/batuhandurann/Mahallem/actions/runs/37815924501) bu kayıt yazılırken sürüyor; önceki PR #36 sonucu PR #35'in timestamp testlerine ait değildir.
- QA başka oturumlar tarafından `c260137328fa775c8d3f140a9713cc870559fa39` seviyesine ilerletildi; logout/persisted Room cache izolasyonu ve tarih/saat doğrulama değişiklikleri bu çalışma dalına çatışmasız alındı. Bu birleşik ağaç için yeni CI sonuçları [PR #36 kontrollerinde](https://github.com/batuhandurann/Mahallem/pull/36/checks) ve [Issue #11](https://github.com/batuhandurann/Mahallem/issues/11) son kanıt kaydında takip edilir; `7c4bc02` sonucu yeni ağacın başarısı değildir.
- FAIL: QA hedefi protected=false. Aktif ruleset #24702630 yalnızca main'i kapsar; sekiz strict check, bypass yok. QA'da başarısız kontrolün merge'i engellediği doğrulanamaz. Kullanıcı merge yasakladığı için merge denemesi NOT RUN. Mevcut GitHub bağlantısında administration yazma aracı yok.
- FAIL: production preflight yeniden çalıştırıldı; template namespace/applicationId, hukuki placeholder, onaylı sürüm ve gerçek privacy/account-deletion URL'leri eksik. Main başka oturumlar tarafından `85e86771394334372f91d8a3e26bcc50e8c960b8` seviyesine taşınmış; QA ve canonical main farklı applicationId/veritabanı mimarileridir. Bu görev main'e yazmadı.
- NOT RUN: onaylı production project/applicationId/upload sertifikası ile imzalı AAB, fiziksel cihaz Auth/offline/lifecycle/hesap izolasyonu, gerçek staging servisleri, Play Console. Erişim/cihaz/onaylı kimlik kanıtı mevcut değil; workflow_dispatch geçmişi boş. Yeni CI'daki fixture imza testi production sertifikası doğrulaması değildir. Staging deploy workflow'u çalıştırılmadı.
- NOT RUN: emülatörde tam kayıt/giriş/offline/process-death/rotation/account-switch uçtan uca kapsamı. Mevcut dokuz Compose testinin başarısı bu kapsamın tamamı değildir. Canlı eski token'lı medyanın migration/envanter doğrulaması da açık.
- Sonraki görev: birleşik kaynak SHA'nın sekiz CI kontrolünü ve PR #35'in yeni QA sonucunu doğrula; ardından Issue #11/PR açıklamalarına kesin SHA/run kanıtını yaz. P0/7 ve P1 erişim/cihaz engelleri çözülmeden Play-ready denmez.

Aşağıdaki kayıtlar önceki aşamaların tarihçesidir; güncel durum yukarıdadır.

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
- `2b30862` için [QA 37778359117](https://github.com/batuhandurann/Mahallem/actions/runs/37778359117) Firebase job FAIL: Auth, Firestore, Storage ve ilk 10 medya testi geçti; metadata'da token silmenin eski token'ı iptal ettiği kanıtlanamadı. Test atlanmadı: private kopya + kaynak nesne silme düzeltildi. `e18b943` CI'sında 12/13 medya testi geçti; kalan test 404 beklerken emülatörün 403 dönmesi nedeniyle FAIL oldu. Kabul koşulu kaynak nesnenin Admin SDK ile fiziksel silinmiş olduğunun doğrulanması + eski URL'nin 403/404 ile reddedilmesidir; yalnızca başarısız indirme yeterli sayılmaz. Yeni CI sonucu beklenir.
- Production workflow'a onaylı Firebase projectId/applicationId, Firebase Android client eşleşmesi, Play upload sertifikası SHA-256 eşleşmesi ve gerçek AAB signer doğrulaması eklendi. Protected production vars yoksa fail-closed. Secrets değiştirilmedi, workflow dispatch edilmedi.
- Yerelde 18 Python test PASS, 3 gerçek JAR imza testi NOT RUN (jarsigner yok); CI JDK 21'de bu üç test zorunlu ve araç eksikse skip yerine FAIL. Fixture anahtarı production key değildir.
- Güncel repo okumasında `main` protected=true, `qa/mahallem-test-suite` protected=false. Main ruleset yalnızca main'i kapsıyor. QA'da başarısız check varken merge engelini kanıtlayamayız; admin koruması eksik. Bu oturum hiçbir merge işlemi yapmaz.
- Fiziksel Android cihaz/ADB, production/staging Console yetkisi, onaylı kimlik ve upload sertifikası verisi yok. Signed/device/staging/Play doğrulamaları NOT RUN; eski mimarileri otomatik birleştirme yapılmadı.
