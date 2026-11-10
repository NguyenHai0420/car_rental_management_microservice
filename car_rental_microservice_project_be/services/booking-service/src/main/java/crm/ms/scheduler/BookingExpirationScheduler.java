package crm.ms.scheduler;

import crm.ms.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingExpirationScheduler {

    private final BookingService bookingService;

    @Scheduled(fixedDelay = 60000)
    public void runExpirationSweep() {
        bookingService.expireOverduePendingBookings();
    }
}

