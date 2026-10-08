# Mahallem ilerleme kaydı

## 2026-10-08 — Acil talep ve güvenli çıkış düzeltmesi
- Kaynak: QA `10422de`; düzeltme dalı `fix/urgent-date-logout-20261008`, uygulama head'i `0481e2e9a172f69760419206083d06a14533ff40`. [Draft PR #37](https://github.com/batuhandurann/Mahallem/pull/37) QA dalına açıldı; merge/deploy yapılmadı.
- Acil talep gönderim anında gerçek `YYYY-MM-DD` / `HH:mm` kullanır. Eksik başlık giriş alanı eklendi. Boş başlıklar, geçersiz takvim günleri ve saatler formda, kayıt yolunda ve callable sunucusunda reddedilir.
- Room temizliği suspend + `Dispatchers.IO`; DAO/Flow referanslarını bozan DB kapatma kaldırıldı. Temizlik hatasında oturum korunur ve tekrar deneme mesajı gösterilir. Token kaldırma 5 saniye ile sınırlı; iptal yutulmaz. Yerel temizlik + signOut iptal edilemez tek sınır olarak tamamlanır.
- Hesap değişimi/çıkış ViewModelStore'u temizler; aynı hesapta ekran döndürme durumunu korur. Room altı tablo temizleme, yeniden kullanım, hata/iptal/zaman aşımı, hesap A→B→çıkış→A ve Compose gönderim regresyonları eklendi.
- Doğrulandı: `functions/` içinde `npm test` (26/26 başarılı; gerçek callable handler üzerinde 21 negatif/auth senaryosu dahil), `git diff --check` başarılı. Yerel kaynak ağacı uzak uygulama commit'iyle aynı tree SHA'sına sahip (`6ea08e64a28703c7978e81d715160837f54f3265`).
- Yerel Android test/build/lint denemesi ayar aşamasında durdu: `foojay-resolver-convention:1.0.0` Gradle plugin deposundan indirilemedi. Bu bir test başarısı veya kod derleme kanıtı değildir.
- [QA #714](https://github.com/batuhandurann/Mahallem/actions/runs/37778929651) uygulama head'i için başlatıldı; bu kayıt anında bekliyor. Bu doküman commit'inden sonra yeni koşu oluşursa yeni head'in sonucunu kontrol et.
- Açık: yeni Android test/build/lint/emülatör kanıtı ve gerçek cihaz + gerçek Firebase ile iki hesap uçtan uca doğrulaması. Önceki CI başarıları bu yeni kodun başarısı olarak kullanılmaz.
- Sıradaki tek görev: PR #37'nin son head'i için CI sonuçlarını incele, varsa yeni regresyonu düzelt; ardından güvenli staging erişimiyle gerçek iki hesap smoke testi yap.

## Önceki oturum kaydı (yalnız belirtilen eski commit için)

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
