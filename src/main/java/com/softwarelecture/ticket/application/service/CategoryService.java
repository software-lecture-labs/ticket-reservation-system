package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class CategoryService {
    private CategoryRepository categories;

    protected CategoryService() {
        // CDI proxy constructor.
    }

    @Inject
    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    public List<Category> listAll() {
        return categories.findAll();
    }
}
