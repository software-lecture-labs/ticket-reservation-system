package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;

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
}
