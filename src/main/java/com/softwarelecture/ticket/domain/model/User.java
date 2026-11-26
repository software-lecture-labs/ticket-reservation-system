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

    private static final Pattern LOCAL_ATOM = Pattern.compile("[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+");
    private static final Pattern DOMAIN_LABEL = Pattern.compile("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?");

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
        if (email.length() > 254 || email.indexOf('@') > 64 || !isWellFormed(email)) {
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

    private static boolean isWellFormed(String email) {
        int at = email.indexOf('@');
        if (at < 1 || at != email.lastIndexOf('@')) {
            return false;
        }
        String[] domain = email.substring(at + 1).split("[.]", -1);
        return domain.length >= 2 && allMatch(email.substring(0, at).split("[.]", -1), LOCAL_ATOM)
                && allMatch(domain, DOMAIN_LABEL);
    }

    private static boolean allMatch(String[] parts, Pattern pattern) {
        for (String part : parts) {
            if (!pattern.matcher(part).matches()) {
                return false;
            }
        }
        return true;
    }
}
