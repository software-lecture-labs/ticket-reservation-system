package com.softwarelecture.ticket.presentation.rest.dto;

import java.math.BigDecimal;

/**
 * Yönetici etkinlik isteği. Doğrulama (boş ad, geçmiş tarih, negatif fiyat, geçersiz kapasite)
 * domain modelinde yapılır ve ayrıntılı Türkçe hata mesajıyla 400 döner.
 */
public class EventRequest {
    public String title;
    public Long venueId;
    public Long categoryId;
    /** ISO-8601 UTC zamanı, örn. 2030-01-01T18:00:00Z. */
    public String startsAt;
    public int capacity;
    public BigDecimal ticketPrice;
}
