package com.softwarelecture.ticket.application.service;

import com.softwarelecture.ticket.application.dto.EventPage;
import com.softwarelecture.ticket.application.dto.EventSearch;
import com.softwarelecture.ticket.application.dto.SaveEvent;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Category;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.EventCriteria;
import com.softwarelecture.ticket.domain.model.EventSeat;
import com.softwarelecture.ticket.domain.model.Venue;
import com.softwarelecture.ticket.domain.repository.CategoryRepository;
import com.softwarelecture.ticket.domain.repository.EventRepository;
import com.softwarelecture.ticket.domain.repository.SeatRepository;
import com.softwarelecture.ticket.domain.repository.VenueRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant START = NOW.plusSeconds(86_400);

    private Venue venue;
    private Category category;
    private MemoryEvents events;
    private MemorySeats seats;
    private EventService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        venue = Venue.create("Ankara Sahne", "Ankara", "Kızılay");
        setId(venue, 1L);
        category = Category.create("Tiyatro");
        setId(category, 1L);
        events = new MemoryEvents();
        seats = new MemorySeats();
        service = serviceAt(NOW);
    }

    private EventService serviceAt(Instant now) {
        return new EventService(events, new MemoryVenues(venue), new MemoryCategories(category), seats,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static SaveEvent command(Long venueId, Long categoryId, Instant startsAt, int capacity, String price) {
        return new SaveEvent("Konser", venueId, categoryId, startsAt, capacity, new BigDecimal(price));
    }

    private static SaveEvent valid() {
        return command(1L, 1L, START, 10, "100.00");
    }

    private static DomainException.Kind kindOf(Executable action) {
        return assertThrows(DomainException.class, action).getKind();
    }

    @Test
    void createLinksVenueAndCategoryAndListsEvent() {
        Event created = service.create(valid());
        assertEquals(1L, created.getVenueId());
        assertEquals(1L, created.getCategoryId());
        assertEquals("Ankara Sahne", created.getVenue());
        assertEquals(List.of(created), service.listUpcoming());
    }

    @Test
    void createSupportsZeroPrice() {
        assertEquals(0, service.create(command(1L, 1L, START, 10, "0.00")).getTicketPrice().signum());
    }

    @Test
    void createRejectsInvalidInputWithoutSaving() {
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(1L, 1L, NOW, 10, "10"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(1L, 1L, START, 10, "-1"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(1L, 1L, START, 0, "10"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(99L, 1L, START, 10, "10"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(1L, 99L, START, 10, "10"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(null, 1L, START, 10, "10"))));
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.create(command(1L, null, START, 10, "10"))));
        assertTrue(events.values.isEmpty());
    }

    @Test
    void updateChangesFieldsAndPreservesSoldSeats() {
        Event event = service.create(valid());
        event.reserve(3, NOW);
        Event updated = service.update(event.getId(), command(1L, 1L, START.plusSeconds(60), 5, "80.00"));
        assertEquals(5, updated.getCapacity());
        assertEquals(2, updated.getAvailableSeats());
        assertEquals(new BigDecimal("80.00"), updated.getTicketPrice());
    }

    @Test
    void updateRefusesCapacityBelowSoldSeats() {
        Event event = service.create(valid());
        event.reserve(4, NOW);
        Long id = event.getId();
        assertEquals(DomainException.Kind.CONFLICT, kindOf(() -> service.update(id, command(1L, 1L, START, 3, "10"))));
        assertEquals(10, events.values.get(id).getCapacity());
    }

    @Test
    void shrinkingCapacityRemovesUnassignedSeatsAboveTheLimit() {
        Event event = service.create(valid());
        seats.ensureSeats(event.getId(), 10);
        seats.assignSeat(event.getId(), 3);
        event.reserve(1, NOW);
        service.update(event.getId(), command(1L, 1L, START, 5, "10"));
        assertEquals(5, seats.findByEvent(event.getId()).size());
    }

    @Test
    void shrinkingCapacityIsRefusedWhenAssignedSeatWouldFallOutside() {
        Event event = service.create(valid());
        seats.ensureSeats(event.getId(), 10);
        seats.assignSeat(event.getId(), 8);
        event.reserve(1, NOW);
        Long id = event.getId();
        assertEquals(DomainException.Kind.CONFLICT, kindOf(() -> service.update(id, command(1L, 1L, START, 5, "10"))));
        assertEquals(10, seats.findByEvent(id).size());
    }

    @Test
    void startedEventCannotBeUpdated() {
        Event event = service.create(valid());
        EventService afterStart = serviceAt(START.plusSeconds(1));
        Long id = event.getId();
        assertEquals(DomainException.Kind.CONFLICT,
                kindOf(() -> afterStart.update(id, command(1L, 1L, START.plusSeconds(3600), 10, "10"))));
    }

    @Test
    void updateAndDeactivateReportMissingEvent() {
        assertEquals(DomainException.Kind.NOT_FOUND, kindOf(() -> service.update(404L, valid())));
        assertEquals(DomainException.Kind.NOT_FOUND, kindOf(() -> service.deactivate(404L)));
    }

    @Test
    void deactivatedEventIsHiddenFromCatalogAndBlocksReservationsUntilReactivated() {
        Event event = service.create(valid());
        service.deactivate(event.getId());
        assertTrue(service.listUpcoming().isEmpty());
        Long id = event.getId();
        assertEquals(DomainException.Kind.NOT_FOUND, kindOf(() -> service.find(id)));
        assertEquals(DomainException.Kind.CONFLICT, kindOf(() -> event.requireReservationAllowed(NOW)));
        service.activate(id);
        assertEquals(List.of(event), service.listUpcoming());
        assertEquals(event, service.find(id));
    }

    @Test
    void searchRejectsInvalidPaging() {
        for (int[] paging : new int[][] {{-1, 10}, {0, 0}, {0, EventSearch.MAX_SIZE + 1}, {Integer.MAX_VALUE, 2}}) {
            EventSearch search = new EventSearch(null, null, null, null, null, paging[0], paging[1]);
            assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.search(search)));
        }
    }

    @Test
    void searchRejectsReversedDateRange() {
        EventSearch search = new EventSearch(START, NOW, null, null, null, 0, 10);
        assertEquals(DomainException.Kind.VALIDATION, kindOf(() -> service.search(search)));
    }

    @Test
    void searchBuildsCriteriaAndReturnsRequestedPage() {
        for (int i = 0; i < 3; i++) {
            service.create(command(1L, 1L, START.plusSeconds(i * 60L), 10, "10"));
        }
        EventPage page = service.search(new EventSearch(null, null, " Ankara ", 1L, " konser ", 1, 2));
        assertEquals(1, page.items().size());
        assertEquals(3, page.totalElements());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        EventCriteria used = events.lastCriteria;
        assertEquals("Ankara", used.city());
        assertEquals("konser", used.title());
        assertEquals(1L, used.categoryId());
        assertEquals(NOW, used.now());
    }

    private static void setId(Object target, Long id) throws ReflectiveOperationException {
        Field identity = target.getClass().getDeclaredField("id");
        identity.setAccessible(true);
        identity.set(target, id);
    }

    private static class MemoryEvents implements EventRepository {
        private final Map<Long, Event> values = new HashMap<>();
        private long next = 1;
        private EventCriteria lastCriteria;

        @Override
        public List<Event> search(EventCriteria criteria, int offset, int size) {
            lastCriteria = criteria;
            List<Event> all = findUpcoming(criteria.now());
            return all.subList(Math.min(offset, all.size()), Math.min(offset + size, all.size()));
        }

        @Override
        public long count(EventCriteria criteria) {
            return findUpcoming(criteria.now()).size();
        }

        @Override
        public List<Event> findUpcoming(Instant now) {
            return values.values().stream().filter(event -> event.isActive() && event.getStartsAt().isAfter(now))
                    .sorted(Comparator.comparing(Event::getStartsAt)).toList();
        }

        @Override
        public Optional<Event> findById(Long id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<Event> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public Event save(Event event) {
            try {
                setId(event, next++);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
            values.put(event.getId(), event);
            return event;
        }
    }

    private static class MemoryVenues implements VenueRepository {
        private final List<Venue> values;

        MemoryVenues(Venue... values) {
            this.values = List.of(values);
        }

        @Override
        public List<Venue> findAll() {
            return values;
        }

        @Override
        public Optional<Venue> findById(Long id) {
            return values.stream().filter(item -> id.equals(item.getId())).findFirst();
        }
    }

    private static class MemoryCategories implements CategoryRepository {
        private final List<Category> values;

        MemoryCategories(Category... values) {
            this.values = List.of(values);
        }

        @Override
        public List<Category> findAll() {
            return values;
        }

        @Override
        public Optional<Category> findById(Long id) {
            return values.stream().filter(item -> id.equals(item.getId())).findFirst();
        }
    }

    private static class MemorySeats implements SeatRepository {
        private final Map<Long, List<EventSeat>> values = new HashMap<>();

        void assignSeat(Long eventId, int number) {
            values.get(eventId).stream().filter(seat -> seat.getNumber() == number).findFirst()
                    .orElseThrow().assignTo(UUID.randomUUID());
        }

        @Override
        public void ensureSeats(Long eventId, int capacity) {
            List<EventSeat> created = new ArrayList<>();
            for (int number = 1; number <= capacity; number++) {
                created.add(EventSeat.available(eventId, number));
            }
            values.put(eventId, created);
        }

        @Override
        public List<EventSeat> findByEvent(Long eventId) {
            return values.getOrDefault(eventId, List.of());
        }

        @Override
        public int highestAssignedNumber(Long eventId) {
            return findByEvent(eventId).stream().filter(seat -> !seat.isAvailable())
                    .mapToInt(EventSeat::getNumber).max().orElse(0);
        }

        @Override
        public void trimUnassignedSeats(Long eventId, int capacity) {
            values.getOrDefault(eventId, new ArrayList<>())
                    .removeIf(seat -> seat.isAvailable() && seat.getNumber() > capacity);
        }

        @Override
        public List<EventSeat> findAvailableForUpdate(Long eventId, int quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<EventSeat> findSelectedForUpdate(Long eventId, List<Integer> numbers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void assign(List<EventSeat> selected, UUID reservationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int release(UUID reservationId) {
            throw new UnsupportedOperationException();
        }
    }
}
