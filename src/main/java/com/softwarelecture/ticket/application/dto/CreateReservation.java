package com.softwarelecture.ticket.application.dto;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.util.List;

public record CreateReservation(Long eventId, String customerName, String customerEmail, int quantity,
                                List<Integer> seatNumbers) {
    public CreateReservation {
        if (seatNumbers != null && seatNumbers.stream().anyMatch(n -> n == null)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Koltuk numarası boş olamaz.");
        }
        seatNumbers = seatNumbers == null ? List.of() : List.copyOf(seatNumbers);
    }

    public CreateReservation(Long eventId, String customerName, String customerEmail, int quantity) {
        this(eventId, customerName, customerEmail, quantity, List.of());
    }
}
