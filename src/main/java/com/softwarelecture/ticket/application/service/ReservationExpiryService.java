package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.repository.ReservationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ReservationExpiryService {
    private static final Logger LOGGER = Logger.getLogger(ReservationExpiryService.class.getName());
    @Inject
    private ReservationRepository reservations;
    @Inject
    private ReservationService service;
    @Inject
    private Clock clock;

    public void sweep() {
        for (UUID id : reservations.findDueIds(clock.instant(), 100)) {
            try {
                service.expire(id);
            } catch (RuntimeException failure) {
                LOGGER.log(Level.WARNING, "Reservation expiry failed for " + id, failure);
            }
        }
    }
}
