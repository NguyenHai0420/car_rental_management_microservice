package crm.ms.dto;

import java.time.Instant;

public record DocumentUrlResponse(String url, Instant expiresAt) {
}
