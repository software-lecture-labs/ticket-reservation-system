package com.softwarelecture.ticket.presentation.rest;

import com.softwarelecture.ticket.application.service.CategoryService;
import com.softwarelecture.ticket.presentation.rest.dto.CategoryResponse;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/categories")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class CategoryResource {
    @Inject
    private CategoryService categories;

    @GET
    public List<CategoryResponse> list() {
        return categories.listAll().stream().map(CategoryResponse::new).toList();
    }
}
