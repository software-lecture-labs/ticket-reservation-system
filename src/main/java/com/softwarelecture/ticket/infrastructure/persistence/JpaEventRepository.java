package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class JpaEventRepository implements EventRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public List<Event> findUpcoming(Instant now) {
        return entityManager.createQuery("SELECT e FROM Event e WHERE e.startsAt > :now ORDER BY e.startsAt", Event.class)
                .setParameter("now", now).setMaxResults(100).getResultList();
    }

    @Override
    public Optional<Event> findById(Long id) {
        return Optional.ofNullable(entityManager.find(Event.class, id));
    }

    @Override
    public Optional<Event> findByIdForUpdate(Long id) {
        return JpaLock.translateConflict(() -> Optional.ofNullable(entityManager.find(Event.class, id,
                LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000))));
    }
}
