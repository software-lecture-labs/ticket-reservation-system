package com.softwarelecture.ticket.infrastructure.persistence;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.util.function.Supplier;

final class JpaLock {
    private JpaLock() {
    }

    static <T> T translateConflict(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (LockTimeoutException | PessimisticLockException exception) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Kayıt şu anda başka işlemde. Tekrar deneyin.");
        }
    }
}
