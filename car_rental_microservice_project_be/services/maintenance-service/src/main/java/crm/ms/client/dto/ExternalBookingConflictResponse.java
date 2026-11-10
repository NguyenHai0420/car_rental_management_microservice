package crm.ms.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalBookingConflictResponse {

    private boolean hasConflict;

    @Builder.Default
    private List<ConflictingBookingSummary> conflictingBookings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConflictingBookingSummary {
        private String bookingCode;
        private Instant pickupDatetime;
        private Instant returnDatetime;
        private String status;
    }
}

