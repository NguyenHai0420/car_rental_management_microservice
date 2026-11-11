package crm.ms.files.service;

import java.time.Duration;
import java.util.Set;

public record StorageRules(long maxFileSize, Duration maxPresignExpiry, Set<String> publicBuckets) {
    public StorageRules {
        publicBuckets = Set.copyOf(publicBuckets);
    }
}
