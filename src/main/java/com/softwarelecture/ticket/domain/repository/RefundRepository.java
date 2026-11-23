package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Refund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository {
    void save(Refund refund);

    Optional<Refund> findById(UUID id);

    /** Looks up a user-initiated refund by requester and key. */
    Optional<Refund> findByRequestedByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /** Returns all refund attempts for the payment, including declines. */
    List<Refund> findByPaymentId(UUID paymentId);

    /** Acquires a write lock on the refund until the caller's transaction ends. */
    Optional<Refund> findByIdForUpdate(UUID id);
}
