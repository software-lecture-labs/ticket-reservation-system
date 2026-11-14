# Üye 4 — Ödeme, iade ve bilet

**Sahiplik:** yeni Payment/Ticket modelleri, `PaymentGateway` portu, sahte sağlayıcı adaptörü, checkout/iade servisleri ve REST DTO'ları. Reviewer: Üye 5.

1. **Port ve sahte sağlayıcı (3 puan):** onay/red üretebilen deterministik bir test sağlayıcısı yaz; gerçek kart bilgisi toplama. Application sağlayıcı implementasyonuna değil arayüze bağımlı olsun.
2. **Checkout (5 puan):** rezervasyon, ödeme kaydı ve bilet üretimini uygun durum geçişleriyle bağla. İstemci idempotency anahtarını DB unique constraint ile uygula; aynı ödeme tekrarında ikinci tahsilat/bilet oluşmasın.
3. **İade (5 puan):** iptal/iade durum makinesini Üye 3 ile kur; sağlayıcı hatasında telafi/retry sınırını belirle. Elektronik bilet için tahmin edilmesi güç benzersiz kod üret; ödeme olmadan geçerli bilet üretme.

**Kabul ölçütleri:** başarı, red, tekrar istek, eşzamanlı aynı anahtar, iade ve sağlayıcı kesintisi testleri geçer. Sahte ödeme iskeletinde JTA'nın harici ödeme sağlayıcısını atomik geri alamadığı açıkça belgelenir; gerçek sağlayıcı eklenirse outbox/telafi tasarlanır.

**Doküman/sunum:** sağlayıcı sözleşmesi, durum diyagramı ve 4 dakikalık ödeme/bilet/iade demosu. Üye 5'in checkout ekranına stabil DTO ver.
