package crm.ms.client;

import crm.ms.dto.BookingListResponseWrapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service")
public interface BookingClient {

    @GetMapping("/api/v1/bookings/by-user/{keycloakUserId}")
    BookingListResponseWrapper getBookingsByUserId(@PathVariable("keycloakUserId") String keycloakUserId);
}
