package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.RefundResult;
import com.softwarelecture.ticket.domain.model.RefundStatus;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundResponseTest {
    private static final UUID REFUND_ID = new UUID(0, 1);

    @ParameterizedTest
    @EnumSource(RefundStatus.class)
    void mapsEveryRefundStatusAndMarksOnlyCompletedRefundAsComplete(RefundStatus status) {
        Optional<String> reference = status == RefundStatus.PROVIDER_APPROVED || status == RefundStatus.COMPLETED
                ? Optional.of("internal-refund-reference") : Optional.empty();
        RefundResponse response = new RefundResponse(new RefundResult(REFUND_ID, status, false, reference));

        assertEquals(REFUND_ID.toString(), response.refundId);
        assertEquals(status.name(), response.status);
        assertEquals(status == RefundStatus.COMPLETED, response.completed);
    }

    @Test
    void exposesOnlyPublicRefundFields() {
        assertEquals(Set.of("refundId", "status", "completed"), Arrays.stream(RefundResponse.class.getFields())
                .map(Field::getName).collect(Collectors.toSet()));
    }

    @Test
    void rejectsMissingResultIdentityOrStatus() {
        assertThrows(IllegalArgumentException.class, () -> new RefundResponse(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RefundResponse(new RefundResult(null, RefundStatus.PENDING, true, Optional.empty())));
        assertThrows(IllegalArgumentException.class,
                () -> new RefundResponse(new RefundResult(REFUND_ID, null, true, Optional.empty())));
    }
}
