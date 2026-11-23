package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.EventSeat;
import com.softwarelecture.ticket.domain.repository.SeatRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class JpaSeatRepository implements SeatRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public void ensureSeats(Long eventId, int capacity) {
        long count = entityManager.createQuery("SELECT COUNT(s) FROM EventSeat s WHERE s.eventId=:event", Long.class)
                .setParameter("event", eventId).getSingleResult();
        if (count == capacity) {
            return;
        }
        if (count > capacity) {
            throw new IllegalStateException("Event capacity must be reconciled with existing seat allocations");
        }
        Set<Integer> existing = new HashSet<>(entityManager.createQuery(
                "SELECT s.number FROM EventSeat s WHERE s.eventId=:event", Integer.class)
                .setParameter("event", eventId).getResultList());
        for (int number = 1; number <= capacity; number++) {
            if (!existing.contains(number)) {
                entityManager.persist(EventSeat.available(eventId, number));
            }
        }
        entityManager.flush();
    }

    @Override
    public List<EventSeat> findByEvent(Long eventId) {
        return entityManager.createQuery("SELECT s FROM EventSeat s WHERE s.eventId=:event ORDER BY s.number", EventSeat.class)
                .setParameter("event", eventId).getResultList();
    }

    @Override
    public List<EventSeat> findAvailableForUpdate(Long eventId, int quantity) {
        return JpaLock.translateConflict(() -> entityManager.createQuery(
                "SELECT s FROM EventSeat s WHERE s.eventId=:event AND s.reservationId IS NULL ORDER BY s.number", EventSeat.class)
                .setParameter("event", eventId).setMaxResults(quantity).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 5000).getResultList());
    }

    @Override
    public List<EventSeat> findSelectedForUpdate(Long eventId, List<Integer> numbers) {
        return JpaLock.translateConflict(() -> entityManager.createQuery(
                "SELECT s FROM EventSeat s WHERE s.eventId=:event AND s.number IN :numbers ORDER BY s.number", EventSeat.class)
                .setParameter("event", eventId).setParameter("numbers", numbers).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 5000).getResultList());
    }

    @Override
    public void assign(List<EventSeat> selected, UUID reservationId) {
        selected.forEach(seat -> seat.assignTo(reservationId));
        // Surface write failures inside the service invocation, before response construction and JTA completion.
        entityManager.flush();
    }

    @Override
    public int highestAssignedNumber(Long eventId) {
        return entityManager.createQuery(
                "SELECT COALESCE(MAX(s.number), 0) FROM EventSeat s WHERE s.eventId=:event AND s.reservationId IS NOT NULL",
                Integer.class).setParameter("event", eventId).getSingleResult();
    }

    @Override
    public void trimUnassignedSeats(Long eventId, int capacity) {
        entityManager.createQuery(
                "DELETE FROM EventSeat s WHERE s.eventId=:event AND s.number>:capacity AND s.reservationId IS NULL")
                .setParameter("event", eventId).setParameter("capacity", capacity).executeUpdate();
    }

    @Override
    public int release(UUID reservationId) {
        List<EventSeat> allocated = JpaLock.translateConflict(() -> entityManager.createQuery(
                "SELECT s FROM EventSeat s WHERE s.reservationId=:id ORDER BY s.number", EventSeat.class)
                .setParameter("id", reservationId).setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", 5000).getResultList());
        allocated.forEach(seat -> seat.release(reservationId));
        entityManager.flush();
        return allocated.size();
    }
}
