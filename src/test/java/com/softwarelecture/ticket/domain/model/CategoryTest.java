package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryTest {
    @Test
    void createTrimsName() {
        assertEquals("Konser", Category.create("  Konser ").getName());
    }

    @Test
    void rejectsBlankNullOrTooLongName() {
        assertThrows(DomainException.class, () -> Category.create(" "));
        assertThrows(DomainException.class, () -> Category.create(null));
        assertThrows(DomainException.class, () -> Category.create("a".repeat(101)));
    }
}
