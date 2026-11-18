# Üye 3 — Rezervasyon ve koltuk

**Sahiplik:** `Reservation`, `ReservationService`, rezervasyon repository ve REST uçları; yeni koltuk/tutma süresi modeli. Reviewer: Üye 4.

1. **Kullanıcı ilişkisi (3 puan):** Üye 1 ile rezervasyon sahipliğini kur; kullanıcının kendi rezervasyonlarını sayfalı listele; mevcut anonim demo davranışını güncelle.
2. **Koltuk (5 puan):** etkinlik/koltuk eşlemesi, aktif koltuk benzersizliği, koltuk seçimi ve atomik kontenjan değişimini migration + servislerle geliştir. Kontenjan ve koltuk durumu aynı transaction içinde değişmeli.
3. **Yaşam döngüsü (5 puan):** süreli tutma, süresi dolan rezervasyonu bırakma ve ödeme/iptal durumlarını Üye 4 ile tasarla. Eşzamanlı son koltuk, aynı rezervasyona çift iptal ve süre aşımı yarışlarını gerçek DB üzerinde test et.

**Kabul ölçütleri:** aynı koltuk iki aktif rezervasyona atanamaz; mevcut son bilet testi geçer; iptal/süre aşımı yalnızca bir kez stok iade eder; ödeme tamamlanmış rezervasyonda kararlaştırılan iade akışı uygulanır; hata halinde kısmi veri kalmaz.

**Doküman/sunum:** transaction sırası ve kilit gerekçesi, durum diyagramı, 4 dakikalık eşzamanlılık/rollback demosu. Hazır çekirdeği silip yeniden yazmak yerine yeni kuralları testlerle genişlet.

## Uygulanan geliştirme

- [x] Üye 1'in `CurrentAccount`/CSRF ve sahiplik denetimleri korundu; kullanıcıya özel sayfalı liste eklendi.
- [x] Numaralı koltuk seçimi, otomatik atama ve tek canlı tahsis modeli `006` migration'ıyla eklendi.
- [x] 15 dakika/etkinlik başlangıcı sınırı, `EXPIRED` durumu, Payara zamanlayıcısı ve gecikmiş tutmaların uzlaştırılması eklendi.
- [x] Üye 4 için `ReservationCheckout` sunucu içi sözleşmesi; ödeme onayı, iade bekleme ve idempotent iade tamamlama eklendi.
- [x] Domain/servis testleri ve PostgreSQL/Payara üzerinde koltuk yarışı, süre aşımı ve rollback senaryoları eklendi.

Kullanım, durum diyagramı, kilit sırası, mevcut veritabanını yükseltme ve 4 dakikalık demo: [Rezervasyon modülü](../RESERVATIONS.md). Gerçek ödeme sağlayıcısı/bilet üretimi Üye 4, görsel koltuk haritası Üye 5 tarafından bu sözleşmelere bağlanacaktır.
