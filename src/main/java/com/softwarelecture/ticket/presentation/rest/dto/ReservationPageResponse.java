package com.softwarelecture.ticket.presentation.rest.dto;

import com.softwarelecture.ticket.application.dto.ReservationPage;
import java.util.List;

public class ReservationPageResponse {
    public final List<ReservationResponse> items;
    public final int page;
    public final int size;
    public final long totalElements;
    public final long totalPages;

    public ReservationPageResponse(ReservationPage result) {
        items = result.items().stream().map(ReservationResponse::new).toList();
        page = result.page();
        size = result.size();
        totalElements = result.totalElements();
        totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }
}
