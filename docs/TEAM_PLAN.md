# Altı kişilik geliştirme planı

Bu belge iş dağılımıdır; GitHub issue/assignee işlemi otomatik yapılmadı. Aşağıdaki kullanıcı adları ekip tarafından doldurulmalı. README'deki çekirdek hazırdır; aşağıdaki işler bunun üzerine geliştirilecek yeni özelliklerdir.

| Üye | Ad soyad | GitHub | Özellik dalı | İlk reviewer |
|---|---|---|---|---|
| 1 | Doldurulacak | @hesap | `feature/member-1-auth` | Üye 2 |
| 2 | Doldurulacak | @hesap | `feature/member-2-catalog` | Üye 3 |
| 3 | Doldurulacak | @hesap | `feature/member-3-reservation` | Üye 4 |
| 4 | Doldurulacak | @hesap | `feature/member-4-checkout` | Üye 5 |
| 5 | Doldurulacak | @hesap | `feature/member-5-web` | Üye 6 |
| 6 | Doldurulacak | @hesap | `feature/member-6-platform` | Üye 1 |

## İş paketleri ve denge

Her satır başlangıçta benzer büyüklükte **13 tahmini puan** içerir. Puanlar not değildir; ilk hafta gerçek süreye göre birlikte yeniden dengelenir. Her pakete ilgili Java kodu, test ve doküman dahildir.

| Üye | İlk paket (3) | İkinci paket (5) | Üçüncü paket (5) |
|---|---|---|---|
| 1 | Kullanıcı şeması ve kayıt | Giriş, session ve güvenli parola doğrulama | USER/ADMIN ve rezervasyon sahipliği + erişim testleri |
| 2 | Mekân/kategori modeli | Etkinlik CRUD ve yönetici doğrulaması | Arama/sayfalama + rezervasyonlu etkinlik değişiklik kuralları |
| 3 | Rezervasyon sorgu/sahiplik uyarlaması | Koltuk modeli ve benzersiz aktif seçim | Süre aşımı/iptal transaction'ı + yarış testleri |
| 4 | Ödeme portu ve sahte sağlayıcı | Idempotent checkout + bilet üretimi | İade/durum makinesi + başarısız ödeme senaryoları |
| 5 | Ortak Faces layout ve katalog | Giriş/hesap/rezervasyon ekranları | Yönetim/checkout/iptal ekranları + form/erişilebilirlik testleri |
| 6 | Sürümlü migration ve yükseltme testi | Raporlama Java servisi/API ve sorgu testleri | Sonar A kapısı, entegrasyon CI, yedekleme ve gerçek yayın |

## Dört aşamalı takvim

Takvim proje başlangıcına göredir; dersin gerçek teslim tarihiyle eşleştirilmelidir.

1. **1. hafta — sözleşme ve temel PR'lar:** herkes projeyi ayağa kaldırır; kullanıcı, koltuk, ödeme ve API şemaları üzerinde anlaşılır. Her üyeden en az bir çalışır kod/test PR'ı gelir. Üye 6 Sonar projesini bağlar.
2. **2. hafta — özellik geliştirme:** kayıt/giriş, katalog CRUD, koltuk rezervasyonu, sahte ödeme, ekranlar ve raporlama küçük PR'larla `dev` üzerinde birleşir. Migration çakışmaları aynı hafta çözülür.
3. **3. hafta — uçtan uca entegrasyon:** kullanıcı → etkinlik → koltuk → ödeme → bilet → iptal/iade akışı tamamlanır. Yetkisiz erişim, fazla satış ve tekrar ödeme testleri çalışır. İlk sunucu yayını yapılır.
4. **4. hafta — kalite ve sunum:** gerçek Sonar A sonuçları, deployment adresi, rollback/yedekten dönüş kanıtı, README ve 30 dakika prova tamamlanır. Son haftaya yeni ana özellik bırakılmaz.

## Bağımlılıklar

- Üye 1 kullanıcı kimliği/sahiplik sözleşmesini ilk hafta Üye 3 ve 5'e verir.
- Üye 2 etkinlik ve fiyat sözleşmesini Üye 3, 4 ve 5 ile sabitler.
- Üye 3 koltuk/rezervasyon durumlarını Üye 4 ile birlikte tasarlar; stok iadesinin tek sorumlusu netleşir.
- Üye 4 gerçek kart verisi toplamayan sahte sağlayıcıyla çalışır. Üye 5 önce stub sözleşmeye göre ekranları ilerletebilir.
- Üye 6 ortak şema değişikliklerine migration numarası verir; her özellik sahibi kendi migration/testini yazar.

## Her üyenin tamamlanma tanımı

- İlgili issue'nun kabul ölçütleri sağlanır; başarı ve hata senaryoları test edilir.
- Maven ve entegrasyon CI geçer; Sonar bulguları sorumlusu tarafından giderilir.
- API/şema/ekran değişikliği dokümana eklenir ve başka bir üye inceler.
- Katkı gerçek kişisel hesaptan, dönem içine yayılmış anlamlı commit/PR geçmişiyle görünür.
- Sunumda kişi kendi kodunu, en az bir testini ve tasarım kararını açıklayabilir.

Ortak dosyalarda tek kişi tüm işi üstlenmez. Haftalık PR listesi ve tamamlanan puanlar incelenerek görevler gerektiğinde paylaşılır.
