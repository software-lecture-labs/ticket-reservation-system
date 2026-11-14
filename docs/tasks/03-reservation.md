# Üye 3 — Rezervasyon ve koltuk

**Sahiplik:** `Reservation`, `ReservationService`, rezervasyon repository ve REST uçları; yeni koltuk/tutma süresi modeli. Reviewer: Üye 4.

1. **Kullanıcı ilişkisi (3 puan):** Üye 1 ile rezervasyon sahipliğini kur; kullanıcının kendi rezervasyonlarını sayfalı listele; mevcut anonim demo davranışını güncelle.
2. **Koltuk (5 puan):** etkinlik/koltuk eşlemesi, aktif koltuk benzersizliği, koltuk seçimi ve atomik kontenjan değişimini migration + servislerle geliştir. Kontenjan ve koltuk durumu aynı transaction içinde değişmeli.
3. **Yaşam döngüsü (5 puan):** süreli tutma, süresi dolan rezervasyonu bırakma ve ödeme/iptal durumlarını Üye 4 ile tasarla. Eşzamanlı son koltuk, aynı rezervasyona çift iptal ve süre aşımı yarışlarını gerçek DB üzerinde test et.

**Kabul ölçütleri:** aynı koltuk iki aktif rezervasyona atanamaz; mevcut son bilet testi geçer; iptal/süre aşımı yalnızca bir kez stok iade eder; ödeme tamamlanmış rezervasyonda kararlaştırılan iade akışı uygulanır; hata halinde kısmi veri kalmaz.

**Doküman/sunum:** transaction sırası ve kilit gerekçesi, durum diyagramı, 4 dakikalık eşzamanlılık/rollback demosu. Hazır çekirdeği silip yeniden yazmak yerine yeni kuralları testlerle genişlet.
