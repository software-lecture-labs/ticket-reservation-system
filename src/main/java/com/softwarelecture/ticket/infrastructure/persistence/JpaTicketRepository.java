package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.model.Ticket;
import com.softwarelecture.ticket.domain.repository.TicketRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class JpaTicketRepository implements TicketRepository {
    @PersistenceContext(unitName = "ticketPU")
    private EntityManager entityManager;

    @Override
    public void save(Ticket ticket) {
        entityManager.persist(ticket);
        entityManager.flush();
    }

    @Override
    public Optional<Ticket> findById(UUID id) {
        return Optional.ofNullable(entityManager.find(Ticket.class, id));
    }

    @Override
    public Optional<Ticket> findByPaymentId(UUID paymentId) {
        return Optional.ofNullable(entityManager.createQuery(
                "SELECT t FROM Ticket t WHERE t.paymentId = :paymentId", Ticket.class)
                .setParameter("paymentId", paymentId)
                .getSingleResultOrNull());
    }

    @Override
    public Optional<Ticket> findByReservationId(UUID reservationId) {
        return Optional.ofNullable(entityManager.createQuery(
                "SELECT t FROM Ticket t WHERE t.reservationId = :reservationId", Ticket.class)
                .setParameter("reservationId", reservationId)
                .getSingleResultOrNull());
    }
}
