package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.User;
import com.softwarelecture.ticket.domain.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class JpaUserRepository implements UserRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public Optional<User> findByEmail(String normalizedEmail) {
        return entityManager.createQuery("SELECT u FROM TicketUser u WHERE u.email = :email", User.class)
                .setParameter("email", normalizedEmail).getResultList().stream().findFirst();
    }

    @Override
    public boolean insertIfEmailAvailable(User user) {
        return entityManager.createNativeQuery("""
                INSERT INTO users (id, email, password_hash, role) VALUES (CAST(? AS uuid), ?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """)
                .setParameter(1, user.getId().toString())
                .setParameter(2, user.getEmail())
                .setParameter(3, user.getPasswordHash())
                .setParameter(4, user.getRole().name())
                .executeUpdate() == 1;
    }
}
