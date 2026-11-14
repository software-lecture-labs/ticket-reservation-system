# Üye 5 — Jakarta Faces web arayüzü

**Sahiplik:** `presentation/web`, `src/main/webapp`, backing bean ve form doğrulama. Reviewer: Üye 6.

1. **Katalog (3 puan):** ortak layout, etkinlik listesi/detayı, tarih/şehir filtreleri ve boş/hata/yükleme durumlarını geliştir.
2. **Kullanıcı akışı (5 puan):** kayıt/giriş, hesap, rezervasyonlarım ve koltuk seçimi ekranlarını servislerle bağla. Form bean'leri iş kuralını tekrar yazmasın; application servisini çağırsın.
3. **Yönetim ve checkout (5 puan):** rol bazlı menü, yönetici etkinlik formları, ödeme/bilet ve iptal ekranlarını tamamla. Başarısız formda girilen değerleri koru; mobil düzen ve klavye akışını test et.

**Kabul ölçütleri:** etkinlik seç → giriş → koltuk → ödeme → bilet akışı tarayıcıda tamamlanır. Hatalar anlaşılır gösterilir; HTML çıktısı escape edilir; çift form gönderimi yönetilir; sadece menüyü gizlemek yerine sunucu yetkisi de kullanılır. En az bir UI entegrasyon testi ekle.

**Doküman/sunum:** ekran görüntüleri, kullanıcı akışı ve 4 dakikalık arayüz demosu. Diğer üyelerin API/servis sözleşmeleri hazır değilken geçici test verisiyle layout ilerletilebilir.
