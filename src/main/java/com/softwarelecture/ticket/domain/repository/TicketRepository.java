package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Ticket;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {
    void save(Ticket ticket);

    Optional<Ticket> findById(UUID id);

    Optional<Ticket> findByPaymentId(UUID paymentId);

    Optional<Ticket> findByReservationId(UUID reservationId);
}
