package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.CheckoutResult;
import com.softwarelecture.ticket.application.dto.CheckoutResult.TicketInfo;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Payment;
import com.softwarelecture.ticket.domain.model.PaymentStatus;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.domain.model.ReservationStatus;
import com.softwarelecture.ticket.domain.model.Ticket;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Result;
import com.softwarelecture.ticket.domain.repository.PaymentRepository;
import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import com.softwarelecture.ticket.domain.repository.TicketRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
@Transactional(value = Transactional.TxType.REQUIRES_NEW, rollbackOn = Exception.class)
public class CheckoutTransactions {
    private ReservationRepository reservations;
    private PaymentRepository payments;
    private TicketRepository tickets;
    private Clock clock;

    protected CheckoutTransactions() {
        // CDI proxy constructor.
    }

    @Inject
    public CheckoutTransactions(ReservationRepository reservations, PaymentRepository payments,
                                TicketRepository tickets, Clock clock) {
        this.reservations = reservations;
        this.payments = payments;
        this.tickets = tickets;
        this.clock = clock;
    }

    /**
     * Through the CDI proxy, a new attempt is committed before control returns.
     * Write conflicts propagate so the failed transaction can roll back.
     */
    public CheckoutResult begin(UUID userId, UUID reservationId, String idempotencyKey) {
        Reservation reservation = ownedReservation(userId, reservationId, idempotencyKey);
        Optional<CheckoutResult> existing = readExisting(userId, reservationId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Rezervasyon ödeme için aktif değil.");
        }
        if (payments.findByReservationId(reservationId).stream()
                .anyMatch(payment -> payment.getStatus() != PaymentStatus.DECLINED)) {
            throw new DomainException(DomainException.Kind.CONFLICT, "Rezervasyon için başka bir ödeme girişimi var.");
        }
        Payment payment = Payment.create(reservationId, userId, idempotencyKey, reservation.getTotalPrice(), clock.instant());
        payments.save(payment);
        return new CheckoutResult(payment.getId(), payment.getStatus(), true, Optional.empty());
    }

    /**
     * A caller handling a uniqueness conflict can use this CDI entry point
     * after begin has rolled back. An empty result does not authorize charging.
     */
    public Optional<CheckoutResult> findExisting(UUID userId, UUID reservationId, String idempotencyKey) {
        ownedReservation(userId, reservationId, idempotencyKey);
        return readExisting(userId, reservationId, idempotencyKey);
    }

    public CheckoutResult recordProviderResult(UUID paymentId, Result result) {
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(() ->
                new DomainException(DomainException.Kind.NOT_FOUND, "Ödeme girişimi bulunamadı."));
        var now = clock.instant();
        switch (result.outcome()) {
            case DECLINED -> payment.decline(now);
            case UNAVAILABLE -> payment.markUnknown(now);
            // The reference needs to survive, but reservation confirmation is not available yet.
            case APPROVED -> payment.requireCompensation(result.reference(), now);
        }
        return new CheckoutResult(payment.getId(), payment.getStatus(), true, Optional.empty());
    }

    private Optional<CheckoutResult> readExisting(UUID userId, UUID reservationId, String idempotencyKey) {
        return payments.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(payment -> {
            if (!reservationId.equals(payment.getReservationId())) {
                throw new DomainException(DomainException.Kind.CONFLICT, "Ödeme anahtarı başka bir rezervasyona ait.");
            }
            Optional<TicketInfo> ticket = payment.getStatus() == PaymentStatus.APPROVED
                    ? tickets.findByPaymentId(payment.getId())
                            .filter(Ticket::isValid)
                            .map(found -> new TicketInfo(found.getId(), found.getCode(), found.getIssuedAt()))
                    : Optional.empty();
            return new CheckoutResult(payment.getId(), payment.getStatus(), false, ticket);
        });
    }

    private Reservation ownedReservation(UUID userId, UUID reservationId, String idempotencyKey) {
        if (userId == null) {
            throw new DomainException(DomainException.Kind.UNAUTHENTICATED, "Oturum gerekli.");
        }
        if (reservationId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 255) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Rezervasyon kimliği ve geçerli ödeme anahtarı gerekli.");
        }
        return reservations.findOwned(reservationId, userId, false).orElseThrow(() -> reservations.exists(reservationId)
                ? new DomainException(DomainException.Kind.FORBIDDEN, "Bu işleme erişim izniniz yok.")
                : new DomainException(DomainException.Kind.NOT_FOUND, "Rezervasyon bulunamadı."));
    }
}
