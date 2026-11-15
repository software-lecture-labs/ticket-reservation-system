package com.softwarelecture.ticket.domain.model;

import com.softwarelecture.ticket.domain.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity(name = "TicketUser")
@Table(name = "users")
public class User {
    public enum Role { USER, ADMIN }

    private static final String ATOM = "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String LABEL = "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?";
    private static final Pattern EMAIL = Pattern.compile(ATOM + "(?:\\." + ATOM + ")*@" + LABEL + "(?:\\." + LABEL + ")+");

    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Role role;

    protected User() {
        // JPA constructor.
    }

    public static String normalizeEmail(String input) {
        String email = input == null ? "" : input.strip();
        if (email.length() > 254 || email.indexOf('@') > 64 || !EMAIL.matcher(email).matches()) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Geçerli bir e-posta adresi gerekli.");
        }
        return email.toLowerCase(Locale.ROOT);
    }

    public static User register(String email, String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank() || passwordHash.length() > 255) {
            throw new DomainException(DomainException.Kind.VALIDATION, "Parola özeti geçersiz.");
        }
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.role = Role.USER;
        return user;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
}
