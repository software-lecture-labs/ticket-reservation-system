package com.softwarelecture.ticket.application.dto;

public record CreateReservation(Long eventId, String customerName, String customerEmail, int quantity) {
}
