package com.softwarelecture.ticket.infrastructure.payment;

import com.softwarelecture.ticket.domain.port.PaymentGateway;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Outcome;
import com.softwarelecture.ticket.domain.port.PaymentGateway.Result;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakePaymentGatewayTest {
    private static final UUID CHARGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REFUND_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BigDecimal AMOUNT = new BigDecimal("125.50");
    private static final String PAYMENT_REFERENCE = "fake-charge-" + CHARGE_ID;

    @Test
    void approvesChargeWithStableReference() {
        PaymentGateway gateway = new FakePaymentGateway();
        Result expected = new Result(Outcome.APPROVED, PAYMENT_REFERENCE);

        assertEquals(expected, gateway.charge(CHARGE_ID, AMOUNT));
        assertEquals(expected, gateway.charge(CHARGE_ID, AMOUNT));
    }

    @Test
    void approvesRefundWithStableRefundReference() {
        PaymentGateway gateway = new FakePaymentGateway();
        Result expected = new Result(Outcome.APPROVED, "fake-refund-" + REFUND_ID);

        assertEquals(expected, gateway.refund(REFUND_ID, PAYMENT_REFERENCE, AMOUNT));
        assertEquals(expected, gateway.refund(REFUND_ID, PAYMENT_REFERENCE, AMOUNT));
    }

    @Test
    void simulatesDeclineAndOutageIndependentlyForEachOperation() {
        PaymentGateway declinedCharge = new FakePaymentGateway(Outcome.DECLINED, Outcome.UNAVAILABLE);
        PaymentGateway unavailableCharge = new FakePaymentGateway(Outcome.UNAVAILABLE, Outcome.DECLINED);

        assertEquals(new Result(Outcome.DECLINED, null), declinedCharge.charge(CHARGE_ID, AMOUNT));
        assertEquals(new Result(Outcome.UNAVAILABLE, null), declinedCharge.refund(REFUND_ID, PAYMENT_REFERENCE, AMOUNT));
        assertEquals(new Result(Outcome.UNAVAILABLE, null), unavailableCharge.charge(CHARGE_ID, AMOUNT));
        assertEquals(new Result(Outcome.DECLINED, null), unavailableCharge.refund(REFUND_ID, PAYMENT_REFERENCE, AMOUNT));
    }

    @Test
    void rejectsMissingOperationDataAndNegativeAmounts() {
        PaymentGateway gateway = new FakePaymentGateway();

        assertThrows(IllegalArgumentException.class, () -> gateway.charge(null, AMOUNT));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(CHARGE_ID, null));
        assertThrows(IllegalArgumentException.class, () -> gateway.charge(CHARGE_ID, AMOUNT.negate()));
        assertThrows(IllegalArgumentException.class, () -> gateway.refund(REFUND_ID, " ", AMOUNT));
    }

    @Test
    void requiresProviderReferenceOnlyForApprovedResults() {
        assertThrows(IllegalArgumentException.class, () -> new Result(Outcome.APPROVED, null));
        assertThrows(IllegalArgumentException.class, () -> new Result(Outcome.DECLINED, PAYMENT_REFERENCE));
    }
}
