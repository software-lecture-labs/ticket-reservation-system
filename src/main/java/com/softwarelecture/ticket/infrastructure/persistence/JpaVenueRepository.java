package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.VenueRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class JpaVenueRepository implements VenueRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public List<Venue> findAll() {
        // Sıralama sabit: önce şehir, sonra ad, eşitlikte id (deterministik sonuç).
        return entityManager.createQuery("SELECT v FROM Venue v ORDER BY v.city, v.name, v.id", Venue.class)
                .getResultList();
    }
}
