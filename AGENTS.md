# Mahallem çalışma kuralları

Bu dosya repo genelinde geçerlidir. Kullanıcının güncel talimatları önceliklidir.

## Her oturumun başlangıcı
1. Önce bu `AGENTS.md` ve `PROGRESS.md` dosyalarını oku. Değiştirilecek dizinde ek `AGENTS.md` varsa onu da oku.
2. `git status --short`, `git branch --show-current` ve `git log -5 --oneline` ile dalı, commit'i ve yerel değişiklikleri incele.
3. PROGRESS'teki commit ile mevcut commit'i karşılaştır. Gerekirse uzak dalı ve son CI sonuçlarını kontrol et; eski sonuçları yeni commit'e aitmiş gibi kullanma.
4. Sıradaki görevi ve açık engelleri belirle. Başka oturumların veya kullanıcının değişikliklerini silme; kirli checkout'ta gerekirse ayrı worktree kullan.

## Uygulama ve doğrulama
- Tamamlanmış ve kanıtlanmış işleri gereksiz yere yeniden yapma. Yeni değişiklik, başarısızlık veya kanıt eksikliği varsa ilgili kontrolü tekrarla.
- Her değişikliği tek amaca yönelik, küçük ve test edilebilir parçalara ayır. Çalışan özellikleri ve mevcut kodu koru; görev dışı refactor yapma.
- Önce mevcut uygulamayı ve testleri incele. Testi atlayarak, zayıflatarak veya CI hatasını gizleyerek başarı üretme.
- Değişikliğe uygun test/build/lint çalıştır. Çalıştırılamayan kontrolleri ve somut nedenini kaydet.
- APK/AAB oluşması, test geçmesi, merge ve deployment ayrı aşamalardır. İmzasız veya eksik servis yapılandırmalı build'i üretime hazır sayma.
- Secrets, servis hesabı dosyaları, imzalama anahtarları ve token'ları kaynak koda, rapora veya loga koyma.
- Paylaşılan dala yazmadan hemen önce güncel head'i kontrol et. Normal fast-forward/expected-head kontrolü kullan; başka değişiklikleri ezme.
- P0 entegrasyonu için PR hedefi `qa/mahallem-test-suite` ve durum Draft olmalı. `main` push, otomatik merge, deploy ve secret değişikliği yapma. Issue #11'de kapsamı ve sonucu kaydet.
- Gradle çalıştırmadan önce `python3 scripts/verify-gradle-wrapper.py` ile pinleri kontrol et; build için `./gradlew` kullan. QA hedefli PR'da CodeQL ve Secret scan yoksa P0 tamamlandı deme.
- QA ve canonical main kanıtlarını ayrı kaynak SHA'larıyla kaydet. `qa/RELEASE_READINESS_20261008.md` ve Issue #11'deki son sonuçları kontrol et; eski green workflow'u yeni birleşik ağacın veya canlı production'ın kanıtı sayma.
- QA ruleset #24741291 aktiftir: sekiz GitHub Actions kontrolü, güncel taban ve bypass yok. Bekleyen kontrolleri geçirmek için korumayı zayıflatma; Draft engeli veya eksik check kanıtını başarısız CI ile yapılmış merge denemesi olarak sunma.

## Her görevin sonunda
- `PROGRESS.md` içindeki kısa özeti güncelle: tamamlanan işler, kanıtlı testler, açık sorun/engeller ve tek sıradaki görev.
- Testlerde commit, komut veya CI bağlantısı ve sonucu belirt. Bekleyen, iptal edilen ve çalıştırılmayan testler başarı değildir.
- Kullanıcıya gerçekten tamamlananları, değişikliklerin yerel/uzak dal durumunu ve kalan engelleri kısa raporla. Kanıtsız yüzde veya hazır olma iddiası kullanma.
- Kullanıcı devam talimatı vermişse yetki kapsamındaki işi sürdür; gerekli erişim/cihaz engellerini açıkça belirt. Oturum bittikten sonra arka planda çalışma gerçekleşmiş gibi konuşma.
