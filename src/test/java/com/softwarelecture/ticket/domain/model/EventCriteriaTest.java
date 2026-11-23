package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventCriteriaTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    @Test
    void trimsTextAndTreatsBlankAsNoFilter() {
        EventCriteria criteria = new EventCriteria(NOW, null, null, "  İstanbul ", 3L, " konser ");
        assertEquals("İstanbul", criteria.city());
        assertEquals("konser", criteria.title());
        EventCriteria blank = new EventCriteria(NOW, null, null, "   ", null, "");
        assertNull(blank.city());
        assertNull(blank.title());
    }

    @Test
    void rejectsReversedRangeOverlongTextAndBadCategory() {
        Instant later = NOW.plusSeconds(60);
        assertEquals(DomainException.Kind.VALIDATION, assertThrows(DomainException.class,
                () -> new EventCriteria(NOW, later, NOW, null, null, null)).getKind());
        String longText = "a".repeat(EventCriteria.MAX_TEXT_LENGTH + 1);
        assertThrows(DomainException.class, () -> new EventCriteria(NOW, null, null, longText, null, null));
        assertThrows(DomainException.class, () -> new EventCriteria(NOW, null, null, null, null, longText));
        assertThrows(DomainException.class, () -> new EventCriteria(NOW, null, null, null, 0L, null));
    }

    @Test
    void equalBoundsAreAllowed() {
        EventCriteria criteria = new EventCriteria(NOW, NOW, NOW, null, null, null);
        assertEquals(NOW, criteria.from());
    }
}
