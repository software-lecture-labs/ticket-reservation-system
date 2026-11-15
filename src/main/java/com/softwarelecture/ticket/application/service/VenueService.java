package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.VenueRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
@Transactional
public class VenueService {
    private VenueRepository venues;

    protected VenueService() {
        // CDI proxy constructor.
    }

    @Inject
    public VenueService(VenueRepository venues) {
        this.venues = venues;
    }

    public List<Venue> listAll() {
        return venues.findAll();
    }
}
