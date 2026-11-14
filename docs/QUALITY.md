# Kalite ve teslim kanıtları

`mvnw verify` şu kontrolleri çalıştırır: JDK/Maven sürümü, derleme, JUnit iş kuralı testleri, ArchUnit bağımlılık testleri, WAR paketleme, JaCoCo raporu, Checkstyle ve SpotBugs. Servis testlerinde bellek içi repository uygulamaları kullanılır; kilitleme/JTA davranışı gerçek sunucu testine ayrılır. Test kapsamı raporlanır; iskelette tüm projeye zorunlu bir coverage yüzdesi uygulanmaz. Eklenen özelliklerde anlamlı başarı ve hata testleri beklenir.

CI ayrıca temiz PostgreSQL + Payara üzerinde `scripts/smoke_test.py` çalıştırır. Bu test örnek event'i kendisi oluşturur; eşzamanlı son bilet ve zorlanmış persistence hatasında rollback kontrol eder. Payara logunda bu test sırasında beklenen bir SQL exception/500 kaydı oluşur.

## Sonar A notu

1. Organizasyon yetkilisi repository'yi SonarQube Cloud'a bağlar.
2. Repository **Variables**: `SONAR_PROJECT_KEY` ve `SONAR_ORGANIZATION`; **Secrets**: `SONAR_TOKEN` eklenir. Token dosyaya yazılmaz.
3. Başlangıç/baseline ve yeni kod tanımı ekipçe belirlenir. Hocanın kastettiği tarama aracı teyit edilir; farklı bir araçsa karşılığı eklenir.
4. Quality gate; reliability, security ve maintainability rating alanlarında **A** beklentisini karşılayacak şekilde ayarlanır. Sadece varsayılan gate'in yeşil olması, tüm projedeki her rating'in A olduğu anlamına gelmeyebilir. Security hotspot'lar incelenir, açık bulgular giderilir.
5. Push sonrası workflow `sonar.qualitygate.wait=true` ile sonucu bekler. Proje/gate bağlantısı ve üç rating ekran görüntüsü teslim edilir.

Sonar hesabı/secret verilmediği için bu iskelet tek başına Sonar sonucu veya A derecesi iddia etmez. Yerel Checkstyle/SpotBugs sonucu ile harici A raporu ayrı kanıtlardır.

## Teslim klasöründe tutulacak kanıtlar

| Kanıt | Sorumlu |
|---|---|
| Altı kişinin GitHub hesapları ve görevleri | Herkes |
| Döneme yayılmış commit, PR ve review bağlantıları | Herkes |
| Son yeşil Actions çalışması ve test raporları | Üye 6 koordine eder |
| Sonar A/gate raporu ve commit kimliği | Herkes bulgusunu çözer, Üye 6 toplar |
| Mimari/ER diyagramları ve transaction demosu | Üye 2/3/4 |
| Gerçek Payara yayın adresi ve sürüm commit'i | Üye 6 |
| Kalıcı veri, backup/restore testi | Üye 6 |
| 30 dakikalık sunum ve kişi bazlı kod anlatımı | Herkes |

Branch protection: `dev` için başarılı verify işi, en az bir review ve doğrudan push sınırlaması repository yöneticisi tarafından etkinleştirilmelidir. Bu ayarlar dosya eklemekle otomatik etkinleşmez.
