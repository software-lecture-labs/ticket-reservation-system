package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected Category() {
        // JPA constructor.
    }

    private Category(String name) {
        this.name = name;
    }

    public static Category create(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Kategori adı geçersiz.");
        }
        return new Category(name.trim());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
