package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String normalizedEmail);
    boolean insertIfEmailAvailable(User user);
}
