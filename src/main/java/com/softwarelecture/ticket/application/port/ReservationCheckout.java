package com.softwarelecture.ticket.application.port;

import com.softwarelecture.ticket.domain.model.Reservation;
import java.math.BigDecimal;
import java.util.UUID;

/** Server-side contract for the payment module; these callbacks are deliberately not REST endpoints. */
public interface ReservationCheckout {
    Reservation confirmPayment(UUID reservationId, String paymentReference, BigDecimal paidAmount);
    Reservation completeRefund(UUID reservationId, String paymentReference);
}
