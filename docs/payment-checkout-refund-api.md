# Checkout ve iade REST API sözleşmesi

> **Bu API uçları henüz uygulanmadı ve kullanıma hazır değil.** Aşağıdaki alanlar mevcut istek ve yanıt DTO’larıyla uyumlu planı gösterir. JSON örnekleri çalışan bir endpoint çıktısı değildir.

## Kullanıma açılma koşulları

Üye 3’ün `ReservationCheckout` entegrasyonu tamamlanana kadar checkout ve iade uçları etkinleştirilmemeli. Mevcut checkout akışı, sağlayıcının onayladığı tahsilatı `COMPENSATION_REQUIRED` olarak kaydediyor. Normal rezervasyon onayı ve bilet üretimi henüz bağlı değil.

Normal iadede rezervasyonun **`REFUND_PENDING`** durumunda olduğunu sağlayıcı çağrısından önce doğrulayan kontrol henüz eksik. Sağlayıcı onayından sonra rezervasyon iadesi, biletin geçersizleştirilmesi ve iadenin `COMPLETED` durumuna alınması da entegrasyon aşamasında bağlanmalı.

## Ortak istek kuralları

- İki uç da JSON gövdesi alır. `Content-Type` değeri `application/json` olmalı.
- Idempotency anahtarı yalnızca JSON gövdesindeki `idempotencyKey` alanından alınır. `Idempotency-Key` HTTP üstbilgisi bu sözleşmede kullanılmaz. Anahtar zorunludur, yalnızca boşluklardan oluşamaz ve en fazla 255 karakter olabilir.
- Kimlik, mevcut oturum çerezi ve doğrulanmış container principal üzerinden `CurrentAccount` ile çözülür. İstek gövdesinden `userId` alınmaz. Checkout rezervasyon sahibine, normal iade ödeme sahibine açıktır. `ADMIN` rolü sahiplik kontrolünü kaldırmaz.
- Planlanan `POST` uçları mevcut `@ProtectedOperation` filtresini kullanır. İstemci aktif oturuma ait belirteci `X-CSRF-Token` üstbilgisinde göndermeli. Girişten sonra `GET /api/auth/csrf` ile güncel belirteç alınabilir.
- Ödeme tutarı rezervasyon kaydından, iade tutarı ödeme kaydından alınır. İstemci tutar, para birimi veya sağlayıcı referansı belirleyemez. Sistem telafisi için istemciye işlem türü veya ayrı bir REST ucu sunulmaz.

## Planlanan POST /api/checkout

İstek alanları `reservationId` (standart UUID metni) ve `idempotencyKey` (metin).

```json
{
  "reservationId": "00000000-0000-0000-0000-000000000001",
  "idempotencyKey": "checkout-order-1"
}
```

Yanıt `paymentId` (UUID metni), `status` (ödeme durumu), `completed` (boolean) ve varsa `ticket` içerir. `ticket`, `id`, `code` ve UTC ISO 8601 biçiminde `issuedAt` alanlarından oluşur. Yalnızca `APPROVED` durumunda kullanılabilir bilet bilgisi mevcutsa `completed` değeri `true` olur. Diğer durumlarda arayüz `ticket` alanının bulunmamasını veya `null` olmasını kabul etmeli.

Bekleyen ödeme örneği

```json
{
  "paymentId": "00000000-0000-0000-0000-000000000002",
  "status": "PENDING",
  "completed": false
}
```

Tamamlanmış checkout için planlanan yanıt örneği

```json
{
  "paymentId": "00000000-0000-0000-0000-000000000002",
  "status": "APPROVED",
  "completed": true,
  "ticket": {
    "id": "00000000-0000-0000-0000-000000000003",
    "code": "0123456789abcdef0123456789abcdef",
    "issuedAt": "2030-01-01T12:00:00Z"
  }
}
```

| Ödeme durumu | Anlamı |
| --- | --- |
| `PENDING` | Ödeme girişimi kaydedildi, kesin sonuç henüz yok. |
| `UNKNOWN` | Sağlayıcı sonucu belirsiz. Tahsilat gerçekleşmiş olabileceğinden yeni bir anahtarla tekrar ödeme başlatılmamalı. |
| `DECLINED` | Sağlayıcı kesin red verdi. |
| `APPROVED` | Ödeme onaylandı. Checkout’un tamamlanması için yanıtta kullanılabilir bilet bilgisi de bulunmalı. |
| `COMPENSATION_REQUIRED` | Sağlayıcı tahsilatı onayladı ancak checkout tamamlanamadı ve telafi gerekiyor. Arayüz bilet veya başarı göstermemeli. |

## Planlanan POST /api/refunds

Normal iade isteğinin alanları `paymentId` (standart UUID metni) ve `idempotencyKey` (metin).

```json
{
  "paymentId": "00000000-0000-0000-0000-000000000002",
  "idempotencyKey": "refund-order-1"
}
```

Yanıt `refundId` (UUID metni), `status` (iade durumu) ve `completed` (boolean) içerir.

```json
{
  "refundId": "00000000-0000-0000-0000-000000000004",
  "status": "PROVIDER_APPROVED",
  "completed": false
}
```

| İade durumu | Anlamı |
| --- | --- |
| `PENDING` | İade girişimi kaydedildi, kesin sonuç henüz yok. |
| `UNKNOWN` | Sağlayıcı sonucu belirsiz. Yeni bir anahtarla ikinci iade başlatılmamalı. |
| `DECLINED` | Sağlayıcı iadeyi kesin olarak reddetti. |
| `PROVIDER_APPROVED` | Sağlayıcı iadeyi onayladı. Rezervasyon ve bilet tarafındaki yerel tamamlama henüz gerçekleşmedi. `completed` değeri `false` kalır. |
| `COMPLETED` | Sağlayıcı iadesi ve yerel tamamlama kaydedildi. `completed` değeri `true` olur. |

Ödeme ve iade sağlayıcısının dahili referansları yanıtlarda bulunmaz.

## Planlanan HTTP durumları

| Kod | Kullanım |
| --- | --- |
| `200` | Yeni veya mevcut işlem durumu DTO ile döndürülür. Kesin red ve bekleyen sonuç da bu kodla dönebilir. |
| `400` | Eksik veya bozuk JSON, geçersiz UUID ya da eksik, boş veya uzun idempotency anahtarı. |
| `401` | Oturum yok veya oturumun kimliği doğrulanamıyor. |
| `403` | Rol ya da sahiplik kontrolü başarısız veya `X-CSRF-Token` eksik ya da geçersiz. |
| `404` | İstenen rezervasyon veya ödeme bulunamadı. |
| `409` | Anahtar başka rezervasyon veya ödeme için kullanılmış, başka bir girişim işlemi engelliyor ya da mevcut durum işleme izin vermiyor. |
| `500` | Beklenmeyen sunucu veya kayıt hatası. Mevcut hata eşleyicisi genel mesaj ve tanılama referansı döndürür. |

Uygulama hatalarında mevcut `code` ve `message` alanları kullanılacak. Bozuk JSON veya JSON-B kurucu doğrulama hatalarının gerçek HTTP yanıtı, uçlar bağlandıktan sonra Payara üzerinde doğrulanmalı.

## Üye 5 için entegrasyon notları

- Her yeni işlem niyeti için bir anahtar üret ve aynı rezervasyon ya da ödeme için yeniden gönderimlerde bu anahtarı aynen kullan. Aynı kullanıcı, aynı kaynak ve aynı anahtar kaydedilmiş sonucu tekrar okumayı sağlar.
- Ağ hatası, `500`, `PENDING` veya `UNKNOWN` sonrasında yeni anahtar üretip işlemi yeniden başlatma. Tekrar istekte aynı anahtarı kullan. `COMPENSATION_REQUIRED` durumunu destek veya uzlaştırma gerektiren ayrı bir durum olarak göster.
- İşlem tamamlanmasını `completed` üzerinden belirle. Checkout’ta bilet ancak alan mevcutsa gösterilmeli. İadede `PROVIDER_APPROVED` için yerel tamamlama bekleniyor durumu gösterilmeli.
- Uçlar etkinleştirilene kadar bu sözleşmeyi arayüz örnek verileri ve testleri için kullan. Örnek JSON yanıtlarına dayanarak ödeme veya iade çağrısı başlatma.
