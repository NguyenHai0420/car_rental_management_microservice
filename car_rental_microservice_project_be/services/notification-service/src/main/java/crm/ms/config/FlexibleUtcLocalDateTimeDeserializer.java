package crm.ms.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Accepts both standard LocalDateTime strings (e.g. 2026-12-01T09:00:00) and ISO-8601 UTC/offset
 * timestamps (e.g. 2026-12-01T09:00:00Z or 2026-12-01T16:00:00+07:00), normalizing to UTC LocalDateTime.
 */
public class FlexibleUtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.endsWith("Z") || text.endsWith("z")) {
                return Instant.parse(text).atOffset(ZoneOffset.UTC).toLocalDateTime();
            }
            int tIndex = text.indexOf('T');
            if (tIndex > 0 && (text.indexOf('+', tIndex) > 0 || text.indexOf('-', tIndex) > 0)) {
                return OffsetDateTime.parse(text).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ex) {
            throw InvalidFormatException.from(
                    parser,
                    "Expected ISO-8601 UTC date-time (e.g. '2026-12-01T09:00:00' or '2026-12-01T09:00:00Z')",
                    text,
                    LocalDateTime.class
            );
        }
    }
}

