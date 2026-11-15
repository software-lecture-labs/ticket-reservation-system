package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Reservation;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository {
    Optional<Reservation> findOwned(UUID id, UUID userId, boolean forUpdate);
    boolean exists(UUID id);
    void save(Reservation reservation);
}
