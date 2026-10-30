# Üye 2 — Etkinlik kataloğu ve yönetim

**Sahiplik:** `Event`, `EventService`, `EventRepository`, `JpaEventRepository`, `EventResource`; yeni mekân ve kategori modelleri. Reviewer: Üye 3.

1. **Model (3 puan):** mekân/kategori tablolarını migration ile ekle, demo verileri ve sorgu sözleşmesini tanımla.
2. **Yönetim (5 puan):** etkinlik ekleme/güncelleme/pasife alma servislerini, DTO'larını ve ADMIN korumalı REST uçlarını geliştir. Geçmiş tarih, negatif fiyat ve geçersiz kapasiteyi reddet.
3. **Katalog (5 puan):** tarih/şehir/kategori/başlık filtreleri ve sayfalama ekle. Rezervasyonu bulunan etkinliğin kapasitesini satılan adedin altına indirmeyi engelle; iptal/iade ilişkisini Üye 3/4 ile kararlaştır.

**Kabul ölçütleri:** yönetici CRUD yapar; normal kullanıcı yapamaz; filtre/sayfalama deterministiktir; güncelleme kontenjanı bozmaz; sıfır fiyatlı etkinlik desteklenir; şema yükseltmesi eski rezervasyonları korur. Başarı/hata ve sorgu testleri ekle.

**Doküman/sunum:** API örnekleri, güncel ER diyagramı ve 4 dakikalık yönetim/katalog demosu. Üye 5'e ekranların ihtiyaç duyduğu DTO sözleşmesini ilk hafta ver.
