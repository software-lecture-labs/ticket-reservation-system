package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.Refund;
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

class JpaRefundRepositoryTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final UUID RESERVATION_ID = new UUID(0L, 1L);
    private static final UUID USER_ID = new UUID(0L, 2L);
    private static final UUID MISSING_ID = new UUID(0L, 3L);
    private static final UUID BLOCKED_ID = new UUID(0L, 4L);
    private static final String KEY = "refund-key";

    @Test
    void persistsAndFlushesNewRefund() throws ReflectiveOperationException {
        Refund refund = refund(payment());
        List<String> calls = new ArrayList<>();
        JpaRefundRepository repository = repository(stub(EntityManager.class, (proxy, method, args) -> {
            calls.add(method.getName());
            if ("persist".equals(method.getName())) {
                assertSame(refund, args[0]);
            }
            return null;
        }));

        repository.save(refund);
        assertEquals(List.of("persist", "flush"), calls);
    }

    @Test
    void findsRefundByIdOrReturnsEmpty() throws ReflectiveOperationException {
        Refund refund = refund(payment());
        JpaRefundRepository repository = repository(stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("find", method.getName());
            assertEquals(2, args.length);
            assertSame(Refund.class, args[0]);
            return refund.getId().equals(args[1]) ? refund : null;
        }));

        assertEquals(Optional.of(refund), repository.findById(refund.getId()));
        assertEquals(Optional.empty(), repository.findById(MISSING_ID));
    }

    @Test
    void bindsRequesterAndKeyAndHandlesMissingRefund() throws ReflectiveOperationException {
        Refund refund = refund(payment());
        String jpql = """
                SELECT r FROM Refund r
                WHERE r.requestedByUserId = :userId AND r.idempotencyKey = :idempotencyKey
                """.strip();
        JpaRefundRepository found = repository(queryManager(
                jpql, Map.of("userId", USER_ID, "idempotencyKey", KEY), "getSingleResultOrNull", refund));
        JpaRefundRepository missing = repository(queryManager(
                jpql, Map.of("userId", USER_ID, "idempotencyKey", "missing-key"), "getSingleResultOrNull", null));

        assertEquals(Optional.of(refund), found.findByRequestedByUserIdAndIdempotencyKey(USER_ID, KEY));
        assertEquals(Optional.empty(), missing.findByRequestedByUserIdAndIdempotencyKey(USER_ID, "missing-key"));
    }

    @Test
    void bindsPaymentIdAndReturnsAllAttemptsIncludingDeclines() throws ReflectiveOperationException {
        Payment payment = payment();
        Refund declined = Refund.request(payment, USER_ID, "earlier-key", NOW);
        declined.decline(NOW);
        List<Refund> attempts = List.of(declined, refund(payment));
        String jpql = "SELECT r FROM Refund r WHERE r.paymentId = :paymentId";
        JpaRefundRepository found = repository(queryManager(
                jpql, Map.of("paymentId", payment.getId()), "getResultList", attempts));
        JpaRefundRepository missing = repository(queryManager(
                jpql, Map.of("paymentId", MISSING_ID), "getResultList", List.of()));

        assertEquals(attempts, found.findByPaymentId(payment.getId()));
        assertEquals(List.of(), missing.findByPaymentId(MISSING_ID));
    }

    @Test
    void requestsPessimisticWriteAndTranslatesLockTimeout() throws ReflectiveOperationException {
        Refund refund = refund(payment());
        JpaRefundRepository repository = repository(stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("find", method.getName());
            assertEquals(4, args.length);
            assertSame(Refund.class, args[0]);
            assertSame(LockModeType.PESSIMISTIC_WRITE, args[2]);
            assertEquals(Map.of("jakarta.persistence.lock.timeout", 5000), args[3]);
            if (BLOCKED_ID.equals(args[1])) {
                throw new LockTimeoutException();
            }
            return refund.getId().equals(args[1]) ? refund : null;
        }));

        assertEquals(Optional.of(refund), repository.findByIdForUpdate(refund.getId()));
        assertEquals(Optional.empty(), repository.findByIdForUpdate(MISSING_ID));
        DomainException conflict = assertThrows(DomainException.class, () -> repository.findByIdForUpdate(BLOCKED_ID));
        assertEquals(DomainException.Kind.CONFLICT, conflict.getKind());
    }

    private static EntityManager queryManager(String jpql, Map<String, ?> expectedParameters,
                                              String resultMethod, Object result) {
        return stub(EntityManager.class, (proxy, method, args) -> {
            assertEquals("createQuery", method.getName());
            assertEquals(jpql, ((String) args[0]).strip());
            assertSame(Refund.class, args[1]);
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

    private static JpaRefundRepository repository(EntityManager manager) throws ReflectiveOperationException {
        JpaRefundRepository repository = new JpaRefundRepository();
        var field = JpaRefundRepository.class.getDeclaredField("entityManager");
        field.setAccessible(true);
        field.set(repository, manager);
        return repository;
    }

    private static <T> T stub(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Payment payment() {
        Payment payment = Payment.create(RESERVATION_ID, USER_ID, "payment-key", BigDecimal.TEN, NOW);
        payment.approve("charge-reference", NOW);
        return payment;
    }

    private static Refund refund(Payment payment) {
        return Refund.request(payment, USER_ID, KEY, NOW);
    }
}
