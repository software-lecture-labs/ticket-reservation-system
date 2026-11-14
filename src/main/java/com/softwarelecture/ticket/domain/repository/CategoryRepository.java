package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Category;
import java.util.List;

public interface CategoryRepository {
    List<Category> findAll();
}
