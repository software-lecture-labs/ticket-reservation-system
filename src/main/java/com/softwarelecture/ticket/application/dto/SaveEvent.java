package com.softwarelecture.ticket.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Yönetici tarafından etkinlik oluşturma/güncelleme komutu; alan doğrulaması domain modelinde yapılır. */
public record SaveEvent(String title, Long venueId, Long categoryId, Instant startsAt, int capacity,
                        BigDecimal ticketPrice) {
}
