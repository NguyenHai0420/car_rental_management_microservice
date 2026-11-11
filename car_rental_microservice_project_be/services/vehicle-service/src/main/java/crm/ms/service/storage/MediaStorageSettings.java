package crm.ms.service.storage;

import java.time.Duration;

public record MediaStorageSettings(String imageBucket, String documentBucket,
                                   Duration presignExpiry, long maxFileSize) {
}
