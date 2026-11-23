package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.repository.CategoryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryServiceTest {
    private static CategoryRepository categories(List<Category> values) {
        return new CategoryRepository() {
            @Override
            public List<Category> findAll() {
                return values;
            }

            @Override
            public Optional<Category> findById(Long id) {
                return values.stream().filter(category -> id.equals(category.getId())).findFirst();
            }
        };
    }

    @Test
    void listsCategoriesFromRepository() {
        Category category = Category.create("Konser");
        CategoryService service = new CategoryService(categories(List.of(category)));
        assertEquals(List.of(category), service.listAll());
    }

    @Test
    void returnsEmptyListWhenNoCategoryExists() {
        assertTrue(new CategoryService(categories(List.of())).listAll().isEmpty());
    }
}
