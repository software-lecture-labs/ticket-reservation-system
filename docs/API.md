# REST sözleşmesi

Taban: `http://localhost:8080/api`. JSON ve UTF-8 kullanılır. Tarihler ISO 8601 UTC'dir. Para birimi bu iskelette TL'dir.

| Metot | Yol | Başarılı yanıt |
|---|---|---|
| GET | `/health` | `200`, uygulama + veritabanı erişimi |
| POST | `/auth/register` | `201`, yeni kullanıcının kimliği, e-postası ve rolü |
| GET | `/auth/csrf` | `200`, mevcut veya yeni oturuma ait CSRF belirteci |
| POST | `/auth/login` | `200`, doğrulanmış kullanıcı |
| GET | `/auth/me` | `200`, aktif kullanıcı |
| POST | `/auth/logout` | `204`, boş gövde |
| GET | `/events` | `200`, gelecekteki **aktif** etkinlikler (varsayılan en fazla 100), tarihe göre; filtre ve sayfalama için aşağıya bakın. Toplam kayıt sayısı `X-Total-Count` başlığındadır |
| GET | `/events/{id}` | `200`, etkinlik detayı; pasif etkinlik için `404` |
| GET | `/events/{id}/seats` | `200`, numaralı koltukların uygunluğu; rezervasyon/kişi kimliği içermez |
| GET | `/venues` | `200`, mekânlar (şehir, ad, id sırasıyla) |
| GET | `/categories` | `200`, kategoriler (ad, id sırasıyla) |
| POST | `/events` | **ADMIN**, `201`, yeni etkinlik ve `Location` header |
| PUT | `/events/{id}` | **ADMIN**, `200`, güncellenen etkinlik |
| POST | `/events/{id}/deactivate` | **ADMIN**, `200`, etkinlik pasife alındı (`active:false`) |
| POST | `/events/{id}/activate` | **ADMIN**, `200`, etkinlik yeniden aktif |
| POST | `/reservations` | `201`, rezervasyon ve `Location` header |
| GET | `/reservations?page=0&size=20` | `200`, yalnız oturum sahibinin sayfalı rezervasyonları |
| GET | `/reservations/{uuid}` | `200`, rezervasyon özeti |
| POST | `/reservations/{uuid}/cancel` | `200`, `CANCELLED`, `EXPIRED` veya ödeme iadesi bekleyen `REFUND_PENDING` |

Numaralı koltuk isteği için oluşturma gövdesine `"seatNumbers":[1,2]` ekleyin; liste uzunluğu `quantity` ile aynı olmalı ve tekrar içermemelidir. Alan verilmezse sunucu uygun ilk koltukları atar. Yanıtlar `seatNumbers` ve `expiresAt` alanlarını da içerir. `ACTIVE` ödeme bekleyen süreli tutmadır; son tarih etkinlik başlangıcından ileriye geçmez. Ayrıntılı örnekler ve Üye 4 ödeme/iade sözleşmesi: [Rezervasyon modülü](RESERVATIONS.md).

## Kullanıcı kaydı

`POST /api/auth/register` iki JSON alanı kabul eder. E-posta başındaki ve sonundaki boşluklar temizlenir ve adres küçük harfe çevrilir. Noktayla ayrılmış alan adına sahip ASCII e-posta adresleri desteklenir. Parola boşluklardan ibaret olamaz ve 15–128 Unicode kod noktası içermelidir. Parola olduğu gibi işlenir.

```json
{"email":" Ada@Example.com ","password":"sadece örnek için parola"}
```

Başarılı yanıt (`201 Created`) aşağıdaki biçimdedir.

```json
{"id":"9d034072-0dbc-4e4b-b4d5-ee29241eff8d","email":"ada@example.com","role":"USER"}
```

Eksik veya geçersiz alanlar `400 VALIDATION`, aynı normalize adrese sonraki veya eşzamanlı kayıt `409 CONFLICT` döndürür. Rolü sunucu atar. Üye 5 kayıt ekranında bu yanıtı kullanabilir. Kayıt oturum açmaz.

### Üye 5 için giriş ve çıkış akışı

1. Aynı origin üzerinden `GET /api/auth/csrf` çağırın. Yanıt `{"token":"…"}` biçimindedir. Tarayıcı `JSESSIONID` çerezini yönetir. Belirteci sayfa belleğinde tutun.
2. `POST /api/auth/login` isteğinde `Content-Type: application/json` ve `X-CSRF-Token` başlığını gönderin. Gövde örneği `{"email":"ada@example.com","password":"sadece örnek için parola"}`. Başarı yanıtı kayıt örneğindeki `id`, `email` ve `role` alanlarıyla HTTP 200 döner. Başarılı giriş eski oturumu ve CSRF belirtecini geçersiz kılar.
3. Sayfa açılırken `GET /api/auth/me` ile durumu okuyun. Sunucu, döndürdüğü UUID ve rolü Jakarta Security bağlamında kontrol eder. Anonim kullanıcı HTTP 401 alır. Üye 3, rezervasyon sahipliğini denetlerken sunucudaki caller principal adını (`users.id` UUID) kullanabilir. Container, veritabanından gelen `USER` veya `ADMIN` grubunu aynı isimli role eşler.
4. Çıkış öncesi yeni oturum için tekrar `GET /api/auth/csrf` çağırın. Belirteci başlığa koyarak boş gövdeli `POST /api/auth/logout` gönderin. Başarı ve geçerli oturumu kalmamış bir istemcinin tekrar çıkışı HTTP 204 döner. Var olan anonim oturumu da sonlandırmak için o oturumun belirteci gerekir.

Geçerli CSRF belirteciyle yapılan girişte eksik veya biçimsiz alanlar HTTP 400 döner. Yanlış parola ve bilinmeyen e-posta için ortak yanıt HTTP 401 ve `{"code":"UNAUTHENTICATED","message":"Kimlik doğrulanamadı."}` olur. Bilinmeyen hesapta aynı maliyetle bir PBKDF2 doğrulaması yapılır. Veritabanı ve çalışma zamanı nedeniyle yanıt süreleri farklılık gösterebilir. Eksik veya başka oturuma ait CSRF belirteci HTTP 403 ve `{"code":"CSRF","message":"Oturum belirteci geçersiz."}` döndürür.

Oturum 20 dakika hareketsizlikten sonra sona erer. Bu süre kullanılmayan tarayıcı oturumlarını sınırlar. Oturum yalnızca çerezle izlenir. `HttpOnly` ve `SameSite=Lax` çerezine ek olarak giriş ve çıkış için oturuma bağlı CSRF belirteci kullanılır. Kimlik yanıtları `Cache-Control: no-store` taşır. Yerel HTTP geliştirmesinde loopback üzerinden erişilir. HTTPS yayınında projenin Caddy yapılandırması dışarı verilen `JSESSIONID` çerezine `Secure` ekler. Başka bir TLS sonlandırıcı kullanılıyorsa aynı çerez politikasını orada uygulayın.

Oturum açıkken başka hesaba geçmek için aynı giriş ucunu mevcut oturumun CSRF belirteciyle çağırın. Yeni hesabın bilgileri doğrulanırsa eski oturum kapatılır, yeni kimlikle yeni oturum oluşturulur. Yanlış parola veya geçersiz istek mevcut hesabı ve çerezi korur. Arayüz yalnızca başarılı girişten sonra kullanıcı bilgisini değiştirmeli; belirsiz bir ağ sonucunda `GET /api/auth/me` ile etkin hesabı okuyabilir.

### Kullanıcı tablosu ve yükseltme

| Alan | PostgreSQL türü | Amaç ve kısıt |
|---|---|---|
| `id` | UUID | Birincil anahtar, e-postadan bağımsız ve uygulama tarafından üretilir |
| `email` | VARCHAR(254) | Zorunlu, küçük harfli, boşluksuz, benzersiz. C collation ile normalizasyon veritabanı dilinden bağımsızdır |
| `password_hash` | VARCHAR(255) | Zorunlu. Algoritma, tur sayısı, Base64 salt ve Base64 özet sırasıyla iki noktayla ayrılır |
| `role` | VARCHAR(5) | Zorunlu, `USER` veya `ADMIN`. Herkese açık kayıt yalnızca `USER` oluşturur |

Her parolada Java SecureRandom ile 16 bayt salt üretilir. Java'nın PBKDF2-HMAC-SHA256 uygulaması 600.000 turla 256 bit özet üretir. Ayrı salt aynı parolalara farklı özetler verir. Tur sayısı her tahminin hesaplama maliyetini artırır. [OWASP parola saklama rehberi](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

Boş Compose volume üzerinde `004-users.sql` başlangıçta sırayla uygulanır. Daha önce `003` uygulanmış bir volume için aşağıdaki komutu bir kez çalıştırın. İşlem mevcut katalog ve rezervasyon tablolarını korur.

```powershell
docker compose exec -T db sh -c 'psql -X -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/004-users.sql'
```

## Erişim matrisi

| Yöntem ve yol (/api altında) | Anonim | USER | ADMIN | CSRF |
|---|---|---|---|---|
| GET /health, /events, /events/{id}, /venues, /categories | Açık | Açık | Açık | Yok |
| POST /events, PUT /events/{id}, POST /events/{id}/deactivate, POST /events/{id}/activate | 401 | 403 | İzinli | Gerekli |
| POST /auth/register, GET /auth/csrf | Açık | Açık | Açık | Yok |
| POST /auth/login | Açık | Hesap geçişi | Hesap geçişi | Oturuma bağlı |
| GET /auth/me | 401 | Kendi hesabı | Kendi hesabı | Yok |
| POST /auth/logout | Oturum yoksa 204 | 204 | 204 | Mevcut oturum için gerekli |
| POST /reservations | 401 | Kendi adına | Kendi adına | Gerekli |
| GET /reservations/{uuid} | 401 | Yalnız sahibi | Yalnız sahibi | Yok |
| POST /reservations/{uuid}/cancel | 401 | Yalnız sahibi | Yalnız sahibi | Gerekli |

Giriş veya hesap geçişinden sonra aynı çerezle `GET /api/auth/csrf` çağırın. Oluşturma ve iptal POST isteklerinde güncel belirteci `X-CSRF-Token` başlığıyla gönderin. Üye 5, etkin kimliği `/auth/me` üzerinden okur. 401 durumunda giriş ekranına yönlendirir. 403 için hata kodu `CSRF` ise güncel belirteci alır; `FORBIDDEN` ise erişim reddini gösterir.

Rezervasyon API yanıtları `Cache-Control: no-store` taşır. Rezervasyon sahibi container principal UUID’sidir. JSON isteğinde `userId`, `ownerId` veya `role` göndermeyin. İletişim adı ve e-postası bilet bilgisi olarak kalır. Farklı iletişim bilgileri sahipliği değiştirmez. Başarılı oluşturma 201 ve Location, sorgu/iptal 200 döndürür. Mevcut olmayan rezervasyon 404, farklı sahibin veya eski sahipsiz kaydın UUID’si 403, tekrarlı iptal 409 döndürür. Bu 403/404 ayrımı görev kabul ölçütünün gereğidir. Her iki rol yalnızca kendi rezervasyonları üzerinde işlem yapabilir.

Faces formunun oturumu sona erdiğinde veya hesap değiştiğinde eski görünümün gönderilmesi HTTP 410 döndürür. Güncel formu almak için sayfayı yeniden açın.

### Yönetim işlemleri

Etkinlik yazma uçları `@ProtectedOperation(adminOnly = true)` ile korunur. Filtre, kimliği ve container rolünü denetler; POST/PUT/PATCH/DELETE için oturum CSRF başlığını da kontrol eder (`X-CSRF-Token`). HTTP dışı sunum girişinde aynı kimlik denetimi `CurrentAccount.requireAdmin()` üzerinden uygulanır. Yönetim ekranını gizlemek bu denetimin yerini tutmaz. Anonim istek 401, USER 403 (`FORBIDDEN`), geçersiz CSRF belirteci 403 (`CSRF`) alır.

#### Katalog filtreleri ve sayfalama (Üye 2)

`GET /api/events` herkese açıktır. Gövde, eski sözleşmedeki gibi **düz bir JSON dizisidir**; toplam kayıt sayısı `X-Total-Count` yanıt başlığında gelir. Parametre verilmezse davranış değişmez: gelecekte başlayan aktif etkinliklerin ilk 100’ü, tarihe göre.

| Parametre | Anlam |
|---|---|
| `title` | Başlıkta geçen metin; büyük/küçük harf duyarsız. `%` ve `_` düz karakterdir, joker değildir |
| `city` | Mekânın şehri; büyük/küçük harf duyarsız tam eşleşme |
| `categoryId` | `/api/categories` yanıtındaki kimlik |
| `from`, `to` | ISO-8601 UTC. Başlangıç zamanı `from` dahil, `to` hariç aralıktadır. `from` > `to` ise `400` |
| `page` | 0’dan başlar, varsayılan `0` |
| `size` | 1–100, varsayılan `100` |

Filtreler birlikte **VE** ile uygulanır. Sıralama sabittir: başlangıç zamanı, eşitlikte etkinlik kimliği; bu yüzden aynı istek aynı sırayı verir. Geçersiz sayfa/boyut, tarih, sayısal olmayan `categoryId` veya 100 karakterden uzun `title`/`city` `400` `VALIDATION` döner. Aralık dışı sayfa `200` ve boş dizi verir (toplam sayı yine döner).

```
GET /api/events?city=ankara&categoryId=2&from=2030-05-01T00:00:00Z&to=2030-06-01T00:00:00Z&page=0&size=20
```

#### İptal/iade ve kapasite ilişkisi

Kapasite kuralı rezervasyon durumuna bakmaz, **koltuk stokuna** bakar: `satılan/tutulan = capacity - availableSeats`. Stok yalnızca koltuk gerçekten serbest kalınca artar:

| Rezervasyon durumu | Koltuk | Kapasiteyi sınırlar mı |
|---|---|---|
| `ACTIVE` (süreli tutma), `CONFIRMED` | Atanmış | Evet |
| `REFUND_PENDING` (ödenmiş iptal, iade bekliyor) | Atanmış kalır | Evet |
| `CANCELLED`, `EXPIRED`, `REFUNDED` | Serbest bırakıldı | Hayır |

Yani iade tamamlanmadan kapasite düşürülemez; iade tamamlanıp koltuk dönünce düşürülebilir. Bu, Üye 3’ün stok iadesini tek noktada (`releaseInventory`) yapması ve Üye 4’ün iadeyi bu çağrı üzerinden tamamlamasıyla tutarlıdır; katalog tarafında ek bir iptal/iade bağımlılığı yoktur.

#### Etkinlik oluşturma/güncelleme (Üye 2)

`POST /api/events` ve `PUT /api/events/{id}` aynı gövdeyi kabul eder. `venueId` ve `categoryId`, `/api/venues` ve `/api/categories` yanıtlarındaki kimliklerdir; ikisi de zorunludur. Etkinliğin görünen `venue` metni seçilen mekânın adından sunucuda türetilir.

```json
{"title":"Java Konseri","venueId":1,"categoryId":1,"startsAt":"2030-05-01T18:00:00Z","capacity":100,"ticketPrice":350.00}
```

Başarılı yanıt (`201` veya `200`):

```json
{"id":4,"title":"Java Konseri","venue":"İstanbul Kongre Merkezi","venueId":1,"categoryId":1,
 "startsAt":"2030-05-01T18:00:00Z","capacity":100,"availableSeats":100,"ticketPrice":350.00,"active":true}
```

| Kural | Sonuç |
|---|---|
| Boş/uzun başlık (en çok 150), `startsAt` geçmişte veya ISO-8601 UTC değil | `400` `VALIDATION` |
| Fiyat negatif veya 2 haneden fazla kesirli (`0` ücretsiz etkinlik için geçerlidir) | `400` `VALIDATION` |
| Kapasite 1’den küçük veya 100.000’den büyük | `400` `VALIDATION` |
| Mekân/kategori eksik veya bulunamadı | `400` `VALIDATION` |
| Güncellenecek etkinlik yok | `404` `NOT_FOUND` |
| Yeni kapasite satılan/tutulan koltuk sayısının altında | `409` `CONFLICT` |
| Yeni kapasite dışında kalan numarada dolu koltuk var | `409` `CONFLICT` |
| Başlamış etkinliği güncelleme | `409` `CONFLICT` |

Kapasite güncellemesinde satılan/tutulan koltuk sayısı (`capacity - availableSeats`) korunur; `availableSeats` yeni kapasiteye göre yeniden hesaplanır. Kapasite düşerse kimseye atanmamış fazla koltuk satırları silinir, artarsa yeni koltuklar ilk kullanımda üretilir. Güncelleme, rezervasyonlarla aynı etkinlik satırını kilitler; eşzamanlı rezervasyon stoku bozamaz. Fiyat değişikliği mevcut rezervasyonların toplam tutarını etkilemez.

#### Silme yerine pasife alma

Etkinlik silinmez. Rezervasyonu, ödemesi veya bileti olan bir etkinliğin silinmesi veritabanı yabancı anahtarlarıyla engellenir ve geçmişi bozardı. `POST /api/events/{id}/deactivate` etkinliği pasife alır: katalogdan ve `GET /api/events/{id}` yanıtından kalkar, yeni rezervasyon `409` (`Etkinlik satışa kapalı.`) alır. Mevcut rezervasyonlar okunabilir, iptal ve iade akışları çalışmaya devam eder. `POST /api/events/{id}/activate` etkinliği geri açar. İki işlem de tekrarlansa aynı sonucu verir. Şema: [`009-event-management.sql`](../docker/postgres/009-event-management.sql) (`events.active BOOLEAN NOT NULL DEFAULT TRUE`).

Daha önce `008` uygulanmış bir volume için yükseltme:

```powershell
docker compose exec -T db sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/009-event-management.sql'
```

Üye 3’ün sayfalı rezervasyon listesi sorgusu, sayfalama öncesi doğrulanmış kullanıcı UUID’siyle sınırlanmıştır.

Public kayıt daima USER oluşturur. Yetkili veritabanı işletmeni, kimliği doğrulanmış belirli bir hesabın `users.id` değerini kontrol ederek rolünü kontrollü bir transaction içinde ADMIN yapabilir. Rol ataması ve geri alma işlemlerini operasyon kaydına geçirin. Container rolü oturum açılırken alınır. Rol değişikliğinin yürürlüğe girmesi için eski oturumlar sonlandırılmalı ve yeni giriş yapılmalıdır.

### 005 sahiplik migration’ı

Boş volume üzerinde başlangıç SQL dosyaları sırayla çalışır. 004 uygulanmış mevcut veritabanında bir kez çalıştırın:

```powershell
docker compose exec -T db sh -c 'psql -X -v ON_ERROR_STOP=1 --single-transaction -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /docker-entrypoint-initdb.d/005-reservation-owner.sql'
```

`reservations.user_id` UUID foreign key olarak `users.id` alanına bağlıdır. `idx_reservations_user` sahiplik sorgularını destekler. `CHECK (user_id IS NOT NULL) NOT VALID` mevcut satırları korur, yeni INSERT ve UPDATE işlemlerini denetler. Sahibi NULL kalan eski satırda yalnız durum veya iletişim bilgisini değiştiren UPDATE de reddedilir. Migration eski kayıtların sahibi hakkında çıkarım yapmaz. Bu satırlar API üzerinden okunamaz, iptal edilemez veya devralınamaz. İşletmen, sahipliği bağımsız olarak doğruladıktan sonra UUID’yi işlem kaydıyla atayabilir. Sahipsiz satır kalmadığında Üye 6 sonraki migration’da kısıtı doğrulayıp sütunu NOT NULL yapabilir.

## Rezervasyon oluşturma

```json
{
  "eventId": 1,
  "customerName": "Ada Yılmaz",
  "customerEmail": "ada@example.com",
  "quantity": 2
}
```

Örnek yanıt (kimlik ve zaman her istekte farklıdır):

```json
{
  "id": "2aeb5165-f9ec-46c3-942d-0f698ac7221e",
  "eventId": 1,
  "quantity": 2,
  "totalPrice": 700.00,
  "status": "ACTIVE",
  "createdAt": "2030-01-01T12:00:00Z",
  "expiresAt": "2030-01-01T12:15:00Z",
  "seatNumbers": [1, 2]
}
```

Ad 1–100, e-posta en fazla 254 karakter, adet 1–10 olmalı; etkinlik kimliği pozitif olmalıdır. Fiyat gönderilmez. Etkinlik başlamışsa, kontenjan yetmiyorsa veya iptal tekrarlanıyorsa `409` döner.

## Hatalar

```json
{ "code": "CONFLICT", "message": "Yeterli kontenjan yok." }
```

| Kod | HTTP | Anlam |
|---|---|---|
| VALIDATION | 400 | Geçersiz form/JSON alanı veya UUID |
| UNAUTHENTICATED | 401 | Kimliği doğrulanmamış veya oturumu geçersiz kullanıcı |
| FORBIDDEN | 403 | Rol veya sahiplik izni yetersiz |
| CSRF | 403 | Oturuma ait geçerli belirteç eksik |
| NOT_FOUND | 404 | Kayıt yok |
| CONFLICT | 409 | Kontenjan, tarih, durum veya eşzamanlılık çakışması |
| HTTP_ERROR | 4xx | Adres, medya tipi veya JSON biçimi hatası |
| INTERNAL_ERROR | 500 | Beklenmeyen hata; yanıtta log ile eşleşen referans bulunur |

İstemciye stack trace/SQL/parola döndürülmez. `POST /reservations` henüz idempotency anahtarı desteklemez; ağ tekrarlarında aynı isteği otomatik yineleyen istemci yazılmamalıdır. Checkout için idempotency Üye 4'ün teslim ölçütüdür.
