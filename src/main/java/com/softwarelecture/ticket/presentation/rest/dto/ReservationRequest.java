package com.softwarelecture.ticket.presentation.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public class ReservationRequest {
    @NotNull
    @Positive
    public Long eventId;
    @NotBlank
    @Size(max = 100)
    public String customerName;
    @NotBlank
    @Email
    @Size(max = 254)
    public String customerEmail;
    @Min(1)
    @Max(10)
    public int quantity;
    @Size(max = 10)
    public List<@NotNull @Positive Integer> seatNumbers;
}
