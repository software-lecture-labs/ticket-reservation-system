package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryServiceTest {
    @Test
    void listsCategoriesFromRepository() {
        Category category = Category.create("Konser");
        CategoryService service = new CategoryService(() -> List.of(category));
        assertEquals(List.of(category), service.listAll());
    }

    @Test
    void returnsEmptyListWhenNoCategoryExists() {
        assertTrue(new CategoryService(List::of).listAll().isEmpty());
    }
}
