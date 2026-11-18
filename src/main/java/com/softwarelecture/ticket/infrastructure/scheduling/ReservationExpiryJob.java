package com.softwarelecture.ticket.infrastructure.scheduling;

import com.softwarelecture.ticket.application.service.ReservationExpiryService;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ReservationExpiryJob {
    @Inject
    private ReservationExpiryService expiry;

    @Schedule(hour = "*", minute = "*", second = "*/10", persistent = false)
    public void releaseExpiredHolds() {
        expiry.sweep();
    }
}
