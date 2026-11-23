package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.time.Instant;

/**
 * Katalog arama ölçütleri. Her zaman "şimdiden sonra başlayan aktif etkinlikler" üzerinde çalışır;
 * boş (null) alanlar o filtreyi uygulamaz.
 */
public record EventCriteria(Instant now, Instant from, Instant to, String city, Long categoryId, String title) {
    public static final int MAX_TEXT_LENGTH = 100;

    public EventCriteria {
        if (now == null || (from != null && to != null && from.isAfter(to))) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Tarih aralığı geçersiz.");
        }
        city = normalize(city);
        title = normalize(title);
        if (categoryId != null && categoryId < 1) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Kategori kimliği geçersiz.");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new DomainException(DomainException.Kind.VALIDATION,
                    "Arama metni en çok " + MAX_TEXT_LENGTH + " karakter olabilir.");
        }
        return trimmed;
    }
}
