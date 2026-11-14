package com.softwarelecture.ticket.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
public class ClockProducer {
    @Produces
    public Clock clock() {
        return Clock.systemUTC();
    }
}
