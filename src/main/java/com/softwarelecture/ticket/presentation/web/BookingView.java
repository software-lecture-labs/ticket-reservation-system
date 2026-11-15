package com.softwarelecture.ticket.presentation.web;

import com.softwarelecture.ticket.application.dto.CreateReservation;
import com.softwarelecture.ticket.application.service.EventService;
import com.softwarelecture.ticket.application.service.ReservationService;
import com.softwarelecture.ticket.domain.exception.DomainException;
import com.softwarelecture.ticket.domain.model.Event;
import com.softwarelecture.ticket.domain.model.Reservation;
import com.softwarelecture.ticket.presentation.security.CurrentAccount;
import com.softwarelecture.ticket.presentation.security.SessionCsrf;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Named
@RequestScoped
public class BookingView {
    @Inject
    private EventService events;
    @Inject
    private ReservationService reservations;
    @Inject
    private CurrentAccount account;
    @Inject
    private HttpServletRequest request;

    private Long eventId;
    @Min(1)
    @Max(10)
    private int quantity = 1;
    @NotBlank
    @Size(max = 100)
    private String customerName;
    @NotBlank
    @Email
    @Size(max = 254)
    private String customerEmail;
    private String reservationId;
    private Reservation result;

    public void book() {
        try {
            result = reservations.create(formOwner(), new CreateReservation(eventId, customerName, customerEmail, quantity));
            reservationId = result.getId().toString();
            message(FacesMessage.SEVERITY_INFO, "Rezervasyon oluşturuldu. Numaranızı kaydedin: " + reservationId);
        } catch (DomainException exception) {
            message(FacesMessage.SEVERITY_ERROR, exception.getMessage());
        }
    }

    public void cancel() {
        try {
            result = reservations.cancel(formOwner(), UUID.fromString(reservationId));
            message(FacesMessage.SEVERITY_INFO, "Rezervasyon iptal edildi; kontenjan iade edildi.");
        } catch (IllegalArgumentException exception) {
            message(FacesMessage.SEVERITY_ERROR, "Geçerli bir rezervasyon numarası girin.");
        } catch (DomainException exception) {
            message(FacesMessage.SEVERITY_ERROR, exception.getMessage());
        }
    }

    public String getCsrfToken() {
        return SessionCsrf.token(request.getSession());
    }

    private UUID formOwner() {
        UUID owner = account.require().id();
        if (!SessionCsrf.accepts(request.getSession(false), request.getParameter("csrfToken"))) {
            throw new DomainException(DomainException.Kind.CSRF, "Oturum belirteci geçersiz.");
        }
        return owner;
    }

    private void message(FacesMessage.Severity severity, String text) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
    }

    public List<Event> getUpcomingEvents() { return events.listUpcoming(); }
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getResultId() { return result == null ? null : result.getId().toString(); }
    public String getResultStatus() { return result == null ? null : result.getStatus().name(); }
    public BigDecimal getResultTotalPrice() { return result == null ? null : result.getTotalPrice(); }
}
