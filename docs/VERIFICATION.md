# İskeletin yerel doğrulama kaydı

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
