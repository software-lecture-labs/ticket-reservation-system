package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Refund;
import com.softwarelecture.ticket.domain.repository.RefundRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaRefundRepository implements RefundRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public void save(Refund refund) {
        entityManager.persist(refund);
        entityManager.flush();
    }

    @Override
    public Optional<Refund> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(Refund.class, id));
    }

    @Override
    public Optional<Refund> findByRequestedByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return Optional.ofNullable(entityManager.createQuery("""
                SELECT r FROM Refund r
                WHERE r.requestedByUserId = :userId AND r.idempotencyKey = :idempotencyKey
                """, Refund.class)
                .setParameter("userId", userId)
                .setParameter("idempotencyKey", idempotencyKey)
                .getSingleResultOrNull());
    }

    @Override
    public List<Refund> findByPaymentId(UUID paymentId) {
        return entityManager.createQuery("SELECT r FROM Refund r WHERE r.paymentId = :paymentId", Refund.class)
                .setParameter("paymentId", paymentId)
                .getResultList();
    }

    @Override
    public Optional<Refund> findByIdForUpdate(UUID id) {
        return JpaLock.translateConflict(() -> Optional.ofNullable(entityManager.find(Refund.class, id,
                LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000))));
    }
}
