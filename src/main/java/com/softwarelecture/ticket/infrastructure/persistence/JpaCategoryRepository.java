package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class JpaCategoryRepository implements CategoryRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public List<Category> findAll() {
        return entityManager.createQuery("SELECT c FROM Category c ORDER BY c.name, c.id", Category.class)
                .getResultList();
    }
}
