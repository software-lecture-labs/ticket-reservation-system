# REST sözleşmesi

Taban: `http://localhost:8080/api`. JSON ve UTF-8 kullanılır. Tarihler ISO 8601 UTC'dir. Para birimi bu iskelette TL'dir.

| Metot | Yol | Başarılı yanıt |
|---|---|---|
| GET | `/health` | `200`, uygulama + veritabanı erişimi |
| GET | `/events` | `200`, gelecek en fazla 100 etkinlik, tarihe göre |
| GET | `/events/{id}` | `200`, etkinlik detayı |
| POST | `/reservations` | `201`, rezervasyon ve `Location` header |
| GET | `/reservations/{uuid}` | `200`, rezervasyon özeti |
| POST | `/reservations/{uuid}/cancel` | `200`, `CANCELLED` rezervasyon |

## Oluşturma

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

İstemciye stack trace/SQL/parola döndürülmez. Şu anda authentication yok; rezervasyon UUID'sini bilen kişi sorgulama ve iptal yapabilir. Kişisel sahiplik ve yönetici yetkisi ekip planında uygulanacaktır. `POST /reservations` henüz idempotency anahtarı desteklemez; ağ tekrarlarında aynı isteği otomatik yineleyen istemci yazılmamalıdır. Checkout için idempotency Üye 4'ün teslim ölçütüdür.
