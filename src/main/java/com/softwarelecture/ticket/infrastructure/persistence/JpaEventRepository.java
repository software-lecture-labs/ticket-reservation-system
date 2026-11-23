package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventCriteria;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
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
        return entityManager.createQuery("SELECT e FROM Event e WHERE e.active = true AND e.startsAt > :now ORDER BY e.startsAt, e.id", Event.class)
                .setParameter("now", now).setMaxResults(100).getResultList();
    }

    @Override
    public Event save(Event event) {
        entityManager.persist(event);
        // Kimliği ve olası kısıt ihlallerini transaction bitmeden görmek için yazmayı hemen yap.
        entityManager.flush();
        return event;
    }

    @Override
    public List<Event> search(EventCriteria criteria, int offset, int size) {
        TypedQuery<Event> query = entityManager.createQuery(
                "SELECT e FROM Event e WHERE " + where(criteria) + " ORDER BY e.startsAt, e.id", Event.class);
        return bind(query, criteria).setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @Override
    public long count(EventCriteria criteria) {
        return bind(entityManager.createQuery("SELECT COUNT(e) FROM Event e WHERE " + where(criteria), Long.class),
                criteria).getSingleResult();
    }

    // Yalnız sabit JPQL parçaları birleştirilir; kullanıcı girdisi her zaman parametre olarak bağlanır.
    private static String where(EventCriteria criteria) {
        StringBuilder where = new StringBuilder("e.active = true AND e.startsAt > :now");
        if (criteria.from() != null) {
            where.append(" AND e.startsAt >= :from");
        }
        if (criteria.to() != null) {
            where.append(" AND e.startsAt < :to");
        }
        if (criteria.categoryId() != null) {
            where.append(" AND e.categoryId = :categoryId");
        }
        if (criteria.city() != null) {
            where.append(" AND e.venueId IN (SELECT v.id FROM Venue v WHERE LOWER(v.city) = LOWER(:city))");
        }
        if (criteria.title() != null) {
            where.append(" AND LOWER(e.title) LIKE LOWER(:title) ESCAPE '!'");
        }
        return where.toString();
    }

    private static <T> TypedQuery<T> bind(TypedQuery<T> query, EventCriteria criteria) {
        query.setParameter("now", criteria.now());
        if (criteria.from() != null) {
            query.setParameter("from", criteria.from());
        }
        if (criteria.to() != null) {
            query.setParameter("to", criteria.to());
        }
        if (criteria.categoryId() != null) {
            query.setParameter("categoryId", criteria.categoryId());
        }
        if (criteria.city() != null) {
            query.setParameter("city", criteria.city());
        }
        if (criteria.title() != null) {
            query.setParameter("title", "%" + escapeLike(criteria.title()) + "%");
        }
        return query;
    }

    /** LIKE joker karakterleri kullanıcı girdisinde düz metin sayılır ('!' kaçış karakteridir). */
    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
