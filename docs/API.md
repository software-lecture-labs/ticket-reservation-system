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
| GET | `/events` | `200`, gelecek en fazla 100 etkinlik, tarihe göre |
| GET | `/events/{id}` | `200`, etkinlik detayı |
| POST | `/reservations` | `201`, rezervasyon ve `Location` header |
| GET | `/reservations/{uuid}` | `200`, rezervasyon özeti |
| POST | `/reservations/{uuid}/cancel` | `200`, `CANCELLED` rezervasyon |

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
3. Sayfa açılırken `GET /api/auth/me` ile durumu okuyun. Sunucu, döndürdüğü UUID ve rolü Jakarta Security bağlamında kontrol eder. Anonim kullanıcı HTTP 401 alır. Üye 3, ileride kaynak sahipliğini denetlerken sunucudaki caller principal adını (`users.id` UUID) kullanabilir. Container, veritabanından gelen `USER` veya `ADMIN` grubunu aynı isimli role eşler.
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
  "createdAt": "2030-01-01T12:00:00Z"
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
| NOT_FOUND | 404 | Kayıt yok |
| CONFLICT | 409 | Kontenjan, tarih, durum veya eşzamanlılık çakışması |
| HTTP_ERROR | 4xx | Adres, medya tipi veya JSON biçimi hatası |
| INTERNAL_ERROR | 500 | Beklenmeyen hata; yanıtta log ile eşleşen referans bulunur |

İstemciye stack trace/SQL/parola döndürülmez. Rezervasyon UUID'sini bilen kişi sorgulama ve iptal yapabilir. Kişisel sahiplik ve yönetici yetkisi ekip planında uygulanacaktır. `POST /reservations` henüz idempotency anahtarı desteklemez; ağ tekrarlarında aynı isteği otomatik yineleyen istemci yazılmamalıdır. Checkout için idempotency Üye 4'ün teslim ölçütüdür.
