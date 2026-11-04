package crm.ms.files.application;

import java.util.Set;

public record ClientScope(String serviceName, Set<String> buckets, String keyPrefix) {
    public ClientScope {
        buckets = Set.copyOf(buckets);
    }
}
