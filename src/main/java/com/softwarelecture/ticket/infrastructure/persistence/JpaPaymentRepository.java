package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaPaymentRepository implements PaymentRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public void save(Payment payment) {
        entityManager.persist(payment);
        entityManager.flush();
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(Payment.class, id));
    }

    @Override
    public Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return Optional.ofNullable(entityManager.createQuery("""
                SELECT p FROM Payment p WHERE p.userId = :userId AND p.idempotencyKey = :idempotencyKey
                """, Payment.class)
                .setParameter("userId", userId)
                .setParameter("idempotencyKey", idempotencyKey)
                .getSingleResultOrNull());
    }

    @Override
    public List<Payment> findByReservationId(UUID reservationId) {
        return entityManager.createQuery("SELECT p FROM Payment p WHERE p.reservationId = :reservationId", Payment.class)
                .setParameter("reservationId", reservationId)
                .getResultList();
    }

    @Override
    public Optional<Payment> findByIdForUpdate(UUID id) {
        return JpaLock.translateConflict(() -> Optional.ofNullable(entityManager.find(Payment.class, id,
                LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000))));
    }
}
