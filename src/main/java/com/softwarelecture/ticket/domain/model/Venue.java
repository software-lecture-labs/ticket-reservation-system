package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "venues")
public class Venue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(nullable = false, length = 300)
    private String address;

    protected Venue() {
        // JPA constructor.
    }

    private Venue(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }

    public static Venue create(String name, String city, String address) {
        if (isInvalid(name, 200) || isInvalid(city, 100) || isInvalid(address, 300)) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Mekân bilgileri geçersiz.");
        }
        return new Venue(name.trim(), city.trim(), address.trim());
    }

    private static boolean isInvalid(String value, int maxLength) {
        return value == null || value.isBlank() || value.trim().length() > maxLength;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
}
