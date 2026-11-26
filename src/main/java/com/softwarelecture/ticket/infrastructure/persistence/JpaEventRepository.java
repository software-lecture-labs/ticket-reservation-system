package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventCriteria;
import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = cb.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);
        Map<ParameterExpression<String>, String> texts = new HashMap<>();
        query.select(event).where(predicates(cb, query, event, criteria, texts))
                .orderBy(cb.asc(event.get("startsAt")), cb.asc(event.get("id")));
        return prepared(query, texts).setFirstResult(offset).setMaxResults(size).getResultList();
    }

    @Override
    public long count(EventCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Event> event = query.from(Event.class);
        Map<ParameterExpression<String>, String> texts = new HashMap<>();
        query.select(cb.count(event)).where(predicates(cb, query, event, criteria, texts));
        return prepared(query, texts).getSingleResult();
    }

    private <T> TypedQuery<T> prepared(CriteriaQuery<T> query, Map<ParameterExpression<String>, String> texts) {
        TypedQuery<T> typed = entityManager.createQuery(query);
        for (Map.Entry<ParameterExpression<String>, String> text : texts.entrySet()) {
            typed.setParameter(text.getKey(), text.getValue());
        }
        return typed;
    }

    // Kullanıcı girdisi hiçbir zaman sorgu metnine eklenmez; metinler parametre olarak bağlanır. Küçük harfe
    // çevirme iki tarafta da veritabanında yapılır: Java'nın "İ" -> "i̇" çevirisi veritabanının "İ" -> "i"
    // çevirisinden farklıdır ve "İstanbul" aramasını bozardı.
    private static Predicate[] predicates(CriteriaBuilder cb, CriteriaQuery<?> query, Root<Event> event,
                                          EventCriteria criteria, Map<ParameterExpression<String>, String> texts) {
        List<Predicate> list = new ArrayList<>();
        list.add(cb.isTrue(event.<Boolean>get("active")));
        list.add(cb.greaterThan(event.<Instant>get("startsAt"), criteria.now()));
        if (criteria.from() != null) {
            list.add(cb.greaterThanOrEqualTo(event.<Instant>get("startsAt"), criteria.from()));
        }
        if (criteria.to() != null) {
            list.add(cb.lessThan(event.<Instant>get("startsAt"), criteria.to()));
        }
        if (criteria.categoryId() != null) {
            list.add(cb.equal(event.get("categoryId"), criteria.categoryId()));
        }
        if (criteria.city() != null) {
            Subquery<Long> venues = query.subquery(Long.class);
            Root<Venue> venue = venues.from(Venue.class);
            ParameterExpression<String> city = cb.parameter(String.class);
            texts.put(city, criteria.city());
            venues.select(venue.<Long>get("id")).where(cb.equal(cb.lower(venue.<String>get("city")), cb.lower(city)));
            list.add(event.<Long>get("venueId").in(venues));
        }
        if (criteria.title() != null) {
            ParameterExpression<String> title = cb.parameter(String.class);
            texts.put(title, "%" + escapeLike(criteria.title()) + "%");
            list.add(cb.like(cb.lower(event.<String>get("title")), cb.lower(title), '!'));
        }
        return list.toArray(new Predicate[0]);
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
