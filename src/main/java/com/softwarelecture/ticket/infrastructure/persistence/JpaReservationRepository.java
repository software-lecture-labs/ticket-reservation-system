package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.ReservationStatus;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@ApplicationScoped
public class JpaReservationRepository implements ReservationRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public Optional<Reservation> findOwned(UUID id, UUID userId, boolean forUpdate) {
        var query = entityManager.createQuery(
                "SELECT r FROM Reservation r WHERE r.id = :id AND r.userId = :owner", Reservation.class)
                .setParameter("id", id).setParameter("owner", userId);
        if (forUpdate) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout", 5000);
        }
        return JpaLock.translateConflict(() -> query.getResultList().stream().findFirst());
    }

    @Override
    public boolean exists(UUID id) {
        return entityManager.createQuery("SELECT COUNT(r) FROM Reservation r WHERE r.id = :id", Long.class)
                .setParameter("id", id).getSingleResult() > 0;
    }

    @Override
    public void save(Reservation reservation) {
        entityManager.persist(reservation);
        entityManager.flush();
    }

    @Override
    public Optional<Long> findOwnedEventId(UUID id, UUID userId) {
        return entityManager.createQuery(
                "SELECT r.eventId FROM Reservation r WHERE r.id=:id AND r.userId=:owner", Long.class)
                .setParameter("id", id).setParameter("owner", userId).getResultList().stream().findFirst();
    }

    @Override
    public Optional<Long> findEventId(UUID id) {
        return entityManager.createQuery("SELECT r.eventId FROM Reservation r WHERE r.id=:id", Long.class)
                .setParameter("id", id).getResultList().stream().findFirst();
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return JpaLock.translateConflict(() -> entityManager.createQuery(
                "SELECT r FROM Reservation r WHERE r.id=:id", Reservation.class).setParameter("id", id)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).setHint("jakarta.persistence.lock.timeout", 5000)
                .getResultList().stream().findFirst());
    }

    @Override
    public List<Reservation> findDueForEventForUpdate(Long eventId, Instant now) {
        return JpaLock.translateConflict(() -> entityManager.createQuery(
                "SELECT r FROM Reservation r WHERE r.eventId=:event AND r.status=:status AND r.expiresAt<=:now ORDER BY r.id",
                Reservation.class).setParameter("event", eventId)
                .setParameter("status", ReservationStatus.ACTIVE)
                .setParameter("now", now).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 5000).getResultList());
    }

    @Override
    public List<UUID> findDueIds(Instant now, int limit) {
        return entityManager.createQuery(
                "SELECT r.id FROM Reservation r WHERE r.status=:status AND r.expiresAt<=:now ORDER BY r.expiresAt, r.id", UUID.class)
                .setParameter("status", ReservationStatus.ACTIVE)
                .setParameter("now", now).setMaxResults(limit).getResultList();
    }

    @Override
    public List<Reservation> findPageByOwner(UUID userId, int offset, int size) {
        return entityManager.createQuery(
                "SELECT r FROM Reservation r WHERE r.userId=:owner ORDER BY r.createdAt DESC, r.id DESC", Reservation.class)
                .setParameter("owner", userId).setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @Override
    public long countByOwner(UUID userId) {
        return entityManager.createQuery("SELECT COUNT(r) FROM Reservation r WHERE r.userId=:owner", Long.class)
                .setParameter("owner", userId).getSingleResult();
    }
}
