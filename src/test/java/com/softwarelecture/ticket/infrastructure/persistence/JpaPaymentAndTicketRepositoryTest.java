package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Ticket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JpaPaymentAndTicketRepositoryTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID RESERVATION_ID = new UUID(0L, 1L);
    private static final UUID USER_ID = new UUID(0L, 2L);
    private static final String KEY = "checkout-key";

    @Test
    void savesPaymentAndTicketByPersistingAndFlushing() throws ReflectiveOperationException {
        Payment payment = payment();
        Ticket ticket = ticket(payment);

        JpaPaymentRepository payments = new JpaPaymentRepository();
        assertSave(payments, payment, () -> payments.save(payment));
        JpaTicketRepository tickets = new JpaTicketRepository();
        assertSave(tickets, ticket, () -> tickets.save(ticket));
    }

    @Test
    void findsExistingAndMissingEntitiesById() throws ReflectiveOperationException {
        Payment payment = payment();
        Ticket ticket = ticket(payment);
        EntityManager manager = stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("find", method.getName());
            assertEquals(2, args.length);
            if (args[0] == Payment.class) {
                return payment.getId().equals(args[1]) ? payment : null;
            }
            assertSame(Ticket.class, args[0]);
            return ticket.getId().equals(args[1]) ? ticket : null;
        });
        JpaPaymentRepository payments = withManager(new JpaPaymentRepository(), manager);
        JpaTicketRepository tickets = withManager(new JpaTicketRepository(), manager);

        assertEquals(Optional.of(payment), payments.findById(payment.getId()));
        assertEquals(Optional.empty(), payments.findById(new UUID(0L, 3L)));
        assertEquals(Optional.of(ticket), tickets.findById(ticket.getId()));
        assertEquals(Optional.empty(), tickets.findById(new UUID(0L, 3L)));
    }

    @Test
    void requestsPessimisticWriteAndTranslatesLockTimeout() throws ReflectiveOperationException {
        Payment payment = payment();
        EntityManager manager = stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("find", method.getName());
            assertEquals(4, args.length);
            assertSame(Payment.class, args[0]);
            assertSame(LockModeType.PESSIMISTIC_WRITE, args[2]);
            assertEquals(Map.of("jakarta.persistence.lock.timeout", 5000), args[3]);
            if (payment.getId().equals(args[1])) {
                return payment;
            }
            throw new LockTimeoutException();
        });
        JpaPaymentRepository repository = withManager(new JpaPaymentRepository(), manager);

        assertEquals(Optional.of(payment), repository.findByIdForUpdate(payment.getId()));
        DomainException conflict = assertThrows(DomainException.class,
                () -> repository.findByIdForUpdate(new UUID(0L, 3L)));
        assertEquals(DomainException.Kind.CONFLICT, conflict.getKind());
    }

    @Test
    void bindsPaymentFiltersAndReturnsAllReservationAttempts() throws ReflectiveOperationException {
        Payment payment = payment();
        Payment declined = Payment.create(RESERVATION_ID, USER_ID, "earlier-key", BigDecimal.TEN, NOW);
        declined.decline(NOW);
        List<Payment> attempts = List.of(declined, payment);
        JpaPaymentRepository byKey = withManager(new JpaPaymentRepository(), queryManager(Payment.class,
                "SELECT p FROM Payment p WHERE p.userId = :userId AND p.idempotencyKey = :idempotencyKey",
                Map.of("userId", USER_ID, "idempotencyKey", KEY), "getSingleResultOrNull", payment));
        JpaPaymentRepository byReservation = withManager(new JpaPaymentRepository(), queryManager(Payment.class,
                "SELECT p FROM Payment p WHERE p.reservationId = :reservationId",
                Map.of("reservationId", RESERVATION_ID), "getResultList", attempts));

        assertEquals(Optional.of(payment), byKey.findByUserIdAndIdempotencyKey(USER_ID, KEY));
        assertEquals(attempts, byReservation.findByReservationId(RESERVATION_ID));
    }

    @Test
    void bindsTicketFiltersAndMapsMissingTicketToEmptyOptional() throws ReflectiveOperationException {
        Ticket ticket = ticket(payment());
        JpaTicketRepository byPayment = withManager(new JpaTicketRepository(), queryManager(Ticket.class,
                "SELECT t FROM Ticket t WHERE t.paymentId = :paymentId",
                Map.of("paymentId", ticket.getPaymentId()), "getSingleResultOrNull", ticket));
        JpaTicketRepository byReservation = withManager(new JpaTicketRepository(), queryManager(Ticket.class,
                "SELECT t FROM Ticket t WHERE t.reservationId = :reservationId",
                Map.of("reservationId", RESERVATION_ID), "getSingleResultOrNull", null));

        assertEquals(Optional.of(ticket), byPayment.findByPaymentId(ticket.getPaymentId()));
        assertEquals(Optional.empty(), byReservation.findByReservationId(RESERVATION_ID));
    }

    private static void assertSave(Object repository, Object entity, Runnable save) throws ReflectiveOperationException {
        List<String> calls = new ArrayList<>();
        withManager(repository, stub(EntityManager.class, (proxy, method, args) -> {
            calls.add(method.getName());
            if ("persist".equals(method.getName())) {
                assertSame(entity, args[0]);
            }
            return null;
        }));
        save.run();
        assertEquals(List.of("persist", "flush"), calls);
    }

    private static EntityManager queryManager(Class<?> entityType, String jpql, Map<String, ?> expectedParameters,
                                              String resultMethod, Object result) {
        return stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("createQuery", method.getName());
            assertEquals(jpql, ((String) args[0]).strip());
            assertSame(entityType, args[1]);
            Map<Object, Object> parameters = new HashMap<>();
            return stub(TypedQuery.class, (query, queryMethod, queryArgs) -> {
                if ("setParameter".equals(queryMethod.getName())) {
                    parameters.put(queryArgs[0], queryArgs[1]);
                    return query;
                }
                assertEquals(resultMethod, queryMethod.getName());
                assertEquals(expectedParameters, parameters);
                return result;
            });
        });
    }

    private static <T> T withManager(T repository, EntityManager manager) throws ReflectiveOperationException {
        var field = repository.getClass().getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, manager);
        return repository;
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Payment payment() {
        return Payment.create(RESERVATION_ID, USER_ID, KEY, BigDecimal.TEN, NOW);
    }

    private static Ticket ticket(Payment payment) {
        payment.approve("charge-1", NOW);
        return Ticket.issue(payment, NOW);
    }
}
