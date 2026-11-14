# Üye 1 — Kullanıcı ve güvenlik

**Sahiplik:** yeni `domain/model/User`, kullanıcı repository sözleşmesi, `application/service/IdentityService`, persistence adaptörü ve giriş/session entegrasyonu. Reviewer: Üye 2.

1. **Kayıt (3 puan):** kullanıcı tablosunu migration ile ekle; e-postaya normalize edilmiş benzersizlik kuralı koy; kullanıcı kayıt servisi ve API'sini yaz. Düz parola saklama; Java'nın güvenli parola özeti yaklaşımını salt ve maliyet parametresiyle uygula.
2. **Giriş (5 puan):** Jakarta Security/session ile giriş-çıkış, yanlış parola ve mevcut olmayan hesap için ortak hata, session yenileme, form korumalarını tamamla. Üye 5'e kullanıcı oturumu sözleşmesini ver.
3. **Yetkilendirme (5 puan):** USER/ADMIN rollerini uygula; katalog yönetimini ADMIN'e sınırla. Üye 3 ile rezervasyonlara `user_id` ekle ve başka kullanıcının rezervasyonuna erişimi engelle.

**Kabul ölçütleri:** tekrarlı e-posta reddedilir; yanlış giriş başarısızdır; parola response/log/DB içinde açık metin bulunmaz; anonim kullanıcı korunan işlemde 401, yanlış rol/sahip 403 alır; tüm kurallar servis/HTTP testleriyle doğrulanır. Varsayılan admin parolası kaynak koda konmaz.

**Doküman/sunum:** kullanıcı ER diyagramı, auth uçları ve 4 dakikalık kayıt/giriş/yetki demosu. Katalog ve rezervasyon ekiplerinin güvenlik testlerine review yap.
