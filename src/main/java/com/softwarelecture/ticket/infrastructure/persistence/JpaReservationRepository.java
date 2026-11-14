package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaReservationRepository implements ReservationRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public Optional<Reservation> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(Reservation.class, id));
    }

    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return JpaLock.translateConflict(() -> Optional.ofNullable(entityManager.find(Reservation.class, id,
                LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000))));
    }

    @Override
    public void save(Reservation reservation) {
        entityManager.persist(reservation);
        entityManager.flush();
    }
}
