package com.softwarelecture.ticket.domain.repository;

import com.softwarelecture.ticket.domain.model.EventSeat;
import java.util.List;
import java.util.UUID;

public interface SeatRepository {
    void ensureSeats(Long eventId, int capacity);
    List<EventSeat> findByEvent(Long eventId);
    List<EventSeat> findAvailableForUpdate(Long eventId, int quantity);
    List<EventSeat> findSelectedForUpdate(Long eventId, List<Integer> numbers);
    void assign(List<EventSeat> selected, UUID reservationId);
    int release(UUID reservationId);
    /** Bir rezervasyona atanmış en büyük koltuk numarası; hiç dolu koltuk yoksa 0. */
    int highestAssignedNumber(Long eventId);
    /** Kapasite düşürülürken, sınırın üstündeki ve kimseye atanmamış koltuk satırlarını siler. */
    void trimUnassignedSeats(Long eventId, int capacity);
}
