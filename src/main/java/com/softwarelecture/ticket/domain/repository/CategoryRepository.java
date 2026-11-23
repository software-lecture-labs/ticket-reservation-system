package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Category;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    List<Category> findAll();
    Optional<Category> findById(Long id);
}
