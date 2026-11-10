package crm.ms.client;

import crm.ms.client.dto.ExternalBookingConflictResponse;

import java.time.Instant;
import java.util.UUID;

public interface BookingClient {

    ExternalBookingConflictResponse checkBookingConflict(UUID vehicleId, Instant from, Instant to);
}

