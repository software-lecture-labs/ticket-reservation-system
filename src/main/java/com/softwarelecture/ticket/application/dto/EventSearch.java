package com.softwarelecture.ticket.application.dto;

import java.time.Instant;

/** Katalog arama isteği: filtreler ve sayfa bilgisi. Tüm filtreler isteğe bağlıdır. */
public record EventSearch(Instant from, Instant to, String city, Long categoryId, String title, int page, int size) {
    public static final int MAX_SIZE = 100;
}
