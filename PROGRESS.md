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
