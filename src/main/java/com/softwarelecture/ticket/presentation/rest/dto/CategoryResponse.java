package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.domain.model.Category;

public class CategoryResponse {
    public final Long id;
    public final String name;

    public CategoryResponse(Category category) {
        id = category.getId();
        name = category.getName();
    }
}
