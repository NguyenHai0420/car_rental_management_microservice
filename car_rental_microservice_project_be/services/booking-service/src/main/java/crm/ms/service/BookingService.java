package crm.ms.service;

import crm.ms.dto.*;
import crm.ms.enums.BookingStatus;
import crm.ms.event.PaymentEventPayload;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BookingService {

    BookingResponse createBooking(BookingRequest request, String keycloakUserId, String jwtName, String jwtEmail);

    BookingResponse getBookingById(UUID bookingId, String keycloakUserId, boolean isStaffOrAdmin);

    List<BookingResponse> getMyBookings(String keycloakUserId);

    Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable);

    BookingResponse cancelBooking(UUID bookingId, CancelBookingRequest request, String keycloakUserId, boolean isStaffOrAdmin);

    BookingResponse pickupVehicle(UUID bookingId, PickupBookingRequest request, String actorUsername);

    BookingResponse returnVehicle(UUID bookingId, ReturnBookingRequest request, String actorUsername);

    void handlePaymentEvent(PaymentEventPayload event);

    int expireOverduePendingBookings();
}
