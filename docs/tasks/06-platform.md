# Üye 6 — Veri, raporlama, kalite ve dağıtım

**Sahiplik:** `pom.xml`, CI, Docker, şema sürümleme; yeni raporlama application servisi/repository/REST. Reviewer: Üye 1.

1. **Migration (3 puan):** Flyway/Liquibase gibi sürümlü migration yaklaşımını seçip mevcut SQL başlangıcını güvenli biçimde taşı; temiz kurulum ve mevcut veriyle yükseltmeyi test et. Her özellik sahibi kendi migration'ını yazar.
2. **Raporlama (5 puan):** etkinlik doluluk, rezervasyon/iptal sayısı ve ödeme eklendikten sonra ciro raporlarını Java servis/API üzerinden üret. BüyükDecimal ve tarih aralığı kurallarını uygula; yetki ve sorgu testleri ekle.
3. **Kalite/yayın (5 puan):** Sonar projesini ve A rating kapısını bağla, build/entegrasyon CI'ını sürdür, application server üzerinde gerçek hosting adresi sağla. PostgreSQL backup/restore denemesi ve yayın sonrası smoke testi yap.

**Kabul ölçütleri:** CI yeşildir; gerçek Sonar sonuçlarında reliability/security/maintainability A kanıtlanır; raporlar iptal/iade kayıtlarını doğru işler; veri yeniden başlatmada korunur; yedek boş bir DB'ye yüklenip doğrulanır; yayın adresinde uçtan uca akış çalışır.

**Doküman/sunum:** quality gate bağlantısı, yayın adresi, migration/yedekleme adımları ve 4 dakikalık raporlama/dağıtım demosu. Diğer kişilerin testini tek başına yazma; test ve kalite sorumluluğu tüm ekibindir.
