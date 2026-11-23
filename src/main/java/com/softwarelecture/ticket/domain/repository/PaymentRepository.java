package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    void save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Returns all payment attempts for the reservation, including declines. */
    List<Payment> findByReservationId(UUID reservationId);

    /** Acquires a write lock on the payment until the caller's transaction ends. */
    Optional<Payment> findByIdForUpdate(UUID id);
}
