package crm.ms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSummaryResponse {
    private long total;
    private long pending;
    private long sending;
    private long sent;
    private long delivered;
    private long retry;
    private long failed;
    private long deadLetter;
}

