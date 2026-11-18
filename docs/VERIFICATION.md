# Yerel doğrulama kayıtları

## 19 Kasım 2026 — Üye 3 geliştirmesi

| Kontrol | Sonuç |
|---|---|
| Java 21 / Maven `-Pruntime verify` | Başarılı, WAR ve Payara çalışma dosyaları üretildi |
| JUnit domain/servis/güvenlik/mimari testleri | **76 test; 0 başarısızlık, 0 hata, 0 atlanan** |
| Checkstyle | 0 ihlal |
| SpotBugs | 0 bulgu / 0 analiz hatası |
| Dockerfile ile Linux üzerinde derleme ve aynı kalite kapıları | Başarılı |
| Gerçek PostgreSQL 17.6 + Payara Micro 7.2026.10 smoke paketi | **124 başarılı entegrasyon kontrolü**, süreç çıkış kodu 0 |
| XML/Python sözdizimi, yerel doküman bağlantıları, Git whitespace | Başarılı |

Tam smoke paketi mevcut kayıt/giriş/rol/CSRF/sahiplik kontrollerini de içerir. Üye 3 ek kontrolleri; sahipli ve anonim eski verilerin migration'ı, tutarsız stokta DDL rollback'i, sayfalama, aynı numaralı koltuğa yarış, gerçek Payara zamanlayıcısı, süre aşımı/iptal yarışı, iade beklerken stok koruma ve tahsis/bırakma sırasında zorlanan SQL hatalarının rollback'ini doğruladı.

Testler mevcut geliştirme volume'ü yerine `ticket-member3-20261118` Compose projesinde, 18083/15433 portlarında çalıştırıldı. Yerel çıktılar `target/member3-maven.log`, `target/member3-build.log` ve `target/member3-smoke.log` altında tutuldu; bunlar build çıktısı olduklarından commit edilmez. Smoke logundaki kasıtlı `Reservation inventory is inconsistent` hatası, başarısız migration'ın tamamen geri alındığı negatif testin beklenen çıktısıdır.

Ödeme sağlayıcısı çağrısı test edilmedi; Üye 4 için sunucu içi `ReservationCheckout` sözleşmesi ve durum/tekrar callback testleri eklendi. Bu doğrulama harici Sonar A derecesi iddia etmez. Kullanım, migration ve entegrasyon sınırları: [RESERVATIONS.md](RESERVATIONS.md).

## Geçmiş kayıt — 30 Ekim 2026 iskeleti

Tarih: 30 Ekim 2026. Bu kayıt gerçek çalıştırılan kontrolleri, CI'da çalışmak üzere eklenmiş kontrollerden ayırır.

| Kontrol | Sonuç |
|---|---|
| Java 21 kaynak ve test derlemesi | Başarılı |
| JUnit domain testleri | 11 başarılı |
| JUnit application servis testleri | 6 başarılı |
| ArchUnit katman testleri | 3 başarılı |
| Toplam | **20 test, 0 hata, 0 başarısızlık, 0 atlanan** |
| `docker compose --env-file .env.example config --quiet` | Başarılı |
| Yayın override'ı ile Compose config (`APP_DOMAIN` örnek değeriyle) | Başarılı |
| XML, Python, PowerShell sözdizimi ve Git whitespace kontrolü | Başarılı |

`mvn -o verify` sırasında derleme ve 20 test tamamlandı. Sonraki WAR paketleme aşaması, Maven War Plugin'in bazı bağımlılıkları yerel önbellekte bulunmadığı için çevrimdışı modda tamamlanamadı. Dolayısıyla bu bilgisayarda tam `verify`, Checkstyle/SpotBugs sonuçları veya tamamlanmış WAR iddia edilmiyor. Ağ erişimli bir geliştirme/CI ortamında `mvnw verify` tamamlanmalıdır.

Docker Desktop başlatma girişimi çalışan bir Linux engine üretmedi; bu nedenle Docker image build, Payara/PostgreSQL entegrasyon testleri ve gerçek hosting yayını bu oturumda doğrulanamadı. İlgili Docker dosyaları ve `scripts/smoke_test.py` eklendi. Çalışan Docker ortamında:

```sh
cp .env.example .env
docker compose up -d --build
python scripts/smoke_test.py
```

Sonar hesabı/token ayarlanmadı; gerçek A rating/gate raporu henüz alınmadı. Tamamlanacak teslim kanıtları `QUALITY.md` içindedir. Sonraki başarılı doğrulamada bu kayıt gerçek rapor bağlantılarıyla güncellenmelidir.
