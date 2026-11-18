package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.Reservation;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface ReservationRepository {
    Optional<Reservation> findOwned(UUID id, UUID userId, boolean forUpdate);
    boolean exists(UUID id);
    void save(Reservation reservation);
    Optional<Long> findOwnedEventId(UUID id, UUID userId);
    Optional<Long> findEventId(UUID id);
    Optional<Reservation> findByIdForUpdate(UUID id);
    List<Reservation> findDueForEventForUpdate(Long eventId, Instant now);
    List<UUID> findDueIds(Instant now, int limit);
    List<Reservation> findPageByOwner(UUID userId, int offset, int size);
    long countByOwner(UUID userId);
}
