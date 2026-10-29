# Ticket Reservation System

Biletix / Biletinial benzeri mevcut bir ürünün **etkinlik listeleme → bilet rezervasyonu → iptal** akışını örnek alan Java dönem projesi iskeleti. Ana geliştirme dalı: `dev`.

Java 21, Jakarta EE 11, Payara Micro 7.2026.10 ve PostgreSQL kullanılır. Tek Maven WAR içinde katmanlı mimari uygulanır; altı kişinin aynı projede bağımsız özellik geliştirmesi hedeflenir.

## Hızlı başlangıç

Docker Desktop / Docker Engine ve Docker Compose gerekir. İlk çalıştırmada görüntüler ve Maven bağımlılıkları indirilir.

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f app
```

Linux/macOS üzerinde ilk komut: `cp .env.example .env`. `.env` içindeki geliştirme parolasını ekibinize göre değiştirin. Payara açıldıktan sonra:

- Uygulama: [http://localhost:8080/](http://localhost:8080/)
- Etkinlik API: [http://localhost:8080/api/events](http://localhost:8080/api/events)
- Veritabanını da kontrol eden sağlık ucu: [http://localhost:8080/api/health](http://localhost:8080/api/health)

Üç örnek etkinlik, veritabanı ilk oluşturulurken yüklenir. Veriler `ticket-data` volume'ünde saklanır. `docker compose down` hizmetleri durdurur ve verileri korur. Şema dosyaları yalnızca **boş bir veritabanında** otomatik uygulanır; sonraki şema değişiklikleri için sürümlü migration geliştirilmesi ekip planındadır.

Docker dışında Java 21 ile geliştirme, WAR dağıtımı ve sunucuya yayınlama: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Hazır olan çekirdek

- Yaklaşan etkinlikleri listeleyen Jakarta Faces ekranı ve REST API.
- Ad, e-posta ve adet doğrulaması; fiyatın sunucuda `BigDecimal` ile hesaplanması.
- Rezervasyon oluşturma, UUID ile sorgulama ve iptal.
- JTA transaction: kontenjan azaltma + rezervasyon kaydı birlikte başarılı olur veya geri alınır.
- Aynı etkinlik için `PESSIMISTIC_WRITE` kilidi; son bilete eşzamanlı taleplerde fazla satışın önlenmesi.
- Tekrarlanan iptalde ikinci kez kontenjan iadesinin engellenmesi.
- PostgreSQL şeması, örnek veri, Docker ve Payara dağıtım yapılandırması.
- JUnit testleri, ArchUnit katman testleri, Checkstyle, SpotBugs, JaCoCo ve GitHub Actions.

Bu sürüm bir başlangıç iskeletidir. Üyelik/yetkilendirme, yönetici CRUD ekranları, numaralı koltuk seçimi, ödeme/iade ve elektronik bilet henüz uygulanmadı. Bu geliştirmeler aşağıdaki altı kişilik planda ayrıldı. Mevcut rezervasyon kimliği, demo içinde sorgulama/iptal anahtarıdır; kullanıcı sahipliği kontrolü Üye 1 ve Üye 3'ün ortak teslimidir.

## Altı kişilik iş bölümü

İsimler verilmediği için Üye 1–6 kullanıldı; GitHub kullanıcıları [ekip planına](docs/TEAM_PLAN.md) yazılmalı. Her kişi kendi özelliğinin Java kodunu, testini, dokümantasyonunu ve sunum bölümünü teslim eder.

| Kişi | Ana sorumluluk | Geliştirilecek başlıca işler | Görev belgesi |
|---|---|---|---|
| Üye 1 | Kullanıcı ve güvenlik | Kayıt/giriş, parola özeti, USER/ADMIN rolleri, rezervasyon sahipliği | [01-identity.md](docs/tasks/01-identity.md) |
| Üye 2 | Etkinlik kataloğu ve yönetim | Etkinlik/mekân/kategori CRUD, arama, sayfalama, yönetici API | [02-catalog.md](docs/tasks/02-catalog.md) |
| Üye 3 | Rezervasyon ve kontenjan | Çekirdeği geliştirme, koltuk modeli, rezervasyon süresi, eşzamanlılık | [03-reservation.md](docs/tasks/03-reservation.md) |
| Üye 4 | Ödeme ve bilet | Sahte ödeme adaptörü, idempotent checkout, iade, bilet kodu | [04-checkout.md](docs/tasks/04-checkout.md) |
| Üye 5 | Java web arayüzü | Faces katalog/detay/hesap/yönetim ekranları, form doğrulama, erişilebilirlik | [05-web.md](docs/tasks/05-web.md) |
| Üye 6 | Veri, kalite ve dağıtım | Migration, raporlama servisi, entegrasyon testleri, Sonar, yayınlama | [06-platform.md](docs/tasks/06-platform.md) |

Ayrıntılı bağımlılıklar, dört aşamalı takvim ve kişi bazında teslim ölçütleri: [docs/TEAM_PLAN.md](docs/TEAM_PLAN.md).

## Mimari

```text
src/main/java/com/softwarelecture/ticket/
├── domain/          # Entity, iş kuralları, küçük repository arayüzleri
├── application/     # Kullanım senaryoları, servisler ve transaction sınırı
├── infrastructure/  # JPA repository uygulamaları, Clock üretimi
└── presentation/    # REST kaynakları, API DTO'ları ve Faces backing bean
src/main/resources/META-INF/persistence.xml
src/main/webapp/      # Faces ekranı, CSS ve Payara/Jakarta yapılandırması
src/test/java/        # Domain, servis ve mimari testleri
docker/postgres/     # Başlangıç şeması ve demo veriler
config/              # Statik analiz kuralları
docs/                # Mimari, ekip görevleri, API ve teslim kanıtları
scripts/             # Yerel başlatma ve gerçek sunucu entegrasyon testleri
```

Bağımlılık yönü `presentation → application → domain` ve `infrastructure → domain` şeklindedir. Uygulama servisleri JPA sınıflarına değil repository arayüzlerine bağımlıdır. Domain entity'lerinde JPA anotasyonları pragmatik bir tercihtir; ayrı persistence modelleri eklenmedi. [Mimari ve SOLID gerekçeleri](docs/ARCHITECTURE.md).

## Test ve kalite

Java 21 kurulu olmalıdır. Maven Wrapper Maven'i otomatik indirir:

```powershell
.\mvnw.cmd verify
```

Linux/macOS: `sh mvnw verify`. Raporlar `target/surefire-reports`, `target/site/jacoco`, `target/checkstyle-result.xml` ve `target/spotbugsXml.xml` altında oluşur.

Docker Compose uygulaması çalışırken, Python 3.10+ ile gerçek HTTP/PostgreSQL kontrolleri:

```powershell
python scripts/smoke_test.py
```

Bu test kendi geçici etkinliğini oluşturur; doğrulama hatası, fiyat, kayıt, iptal, son bilet için eşzamanlı iki istek ve kasıtlı veritabanı hatasında rollback senaryolarını çalıştırır. Geçici kayıt ve trigger'larını temizler. Geliştirme/test veritabanında çalıştırın.

GitHub Actions `verify` işi hem Maven doğrulamasını hem gerçek Payara/PostgreSQL testlerini çalıştırır. Sonar işi, repository variables `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION` ve secret `SONAR_TOKEN` ayarlandıktan sonra çalışır ve quality gate sonucunu bekler. **A notu yapılandırma dosyasıyla garanti edilmez; gerçek analiz raporu ve geçerli quality gate sonucu alınmalıdır.** [Kalite ve teslim kanıtları](docs/QUALITY.md).

## Örnek REST kullanımı

```powershell
$body = @{ eventId = 1; customerName = 'Ada Yılmaz'; customerEmail = 'ada@example.com'; quantity = 2 } | ConvertTo-Json
$reservation = Invoke-RestMethod http://localhost:8080/api/reservations -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
Invoke-RestMethod "http://localhost:8080/api/reservations/$($reservation.id)"
Invoke-RestMethod "http://localhost:8080/api/reservations/$($reservation.id)/cancel" -Method Post
```

Uçlar, durum kodları ve hata biçimi: [docs/API.md](docs/API.md).

## Hocanın ölçütleriyle eşleştirme

| Ölçüt | İskelette karşılığı / ekipte tamamlanacak kanıt |
|---|---|
| 2–6 kişi ve mevcut ürün klonu | 6 kişilik görev planı, Biletix/Biletinial benzeri sınırlı kapsam |
| Belirli mimari | Dört katman, repository arayüzleri, ArchUnit testleri |
| Kök README | Bu belge ve bağlantılı teknik dokümanlar |
| SOLID | Küçük servis/repository sorumlulukları, constructor injection, adaptör sınırları |
| Statik taramada A | Yerel statik analiz kapıları + yapılandırılacak gerçek Sonar A raporu |
| Düzenli ve dengeli commit | Kişisel feature dalı, küçük anlamlı commit, PR/review akışı; dönem boyunca ekip uygular |
| Çalışan ve application server üzerinde yayınlanan proje | WAR + Payara Micro + Docker; gerçek hosting adresi ve yayın kanıtını ekip tamamlar |
| Veritabanı | Kalıcı PostgreSQL, SQL şeması ve örnek veri |
| Transaction | `ReservationService.create/cancel`, JTA ve gerçek rollback kontrolü |
| En fazla 30 dakika sunum | [Sunum akışı](docs/PRESENTATION.md), kişi başı 4 dakika + 6 dakika ortak demo |
| Java | Java 21 / Jakarta EE; arayüz Java Faces ile sunulur |

## Katkı akışı

```text
dev → feature/<konu> → Pull Request → test + başka üyenin incelemesi → dev
```

Örnek dal adları ve commit kuralları: [CONTRIBUTING.md](CONTRIBUTING.md). Her üye kendi hesabından gerçek katkısını commit etmelidir; iskeletin tek seferde eklenmesi düzenli dönem katkısının yerine geçmez.

Teknoloji kaynakları: [Payara sürümleri](https://github.com/payara/Payara/releases), [desteklenen JDK'lar](https://docs.azul.com/payara-community/general-info/supported-platforms.html), [Maven Wrapper](https://maven.apache.org/tools/wrapper/).
