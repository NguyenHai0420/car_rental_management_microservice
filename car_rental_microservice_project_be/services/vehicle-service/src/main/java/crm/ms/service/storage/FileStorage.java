package crm.ms.service.storage;

import java.io.InputStream;
import java.time.Duration;

/** Application-owned port. No SDK or HTTP types cross this boundary. */
public interface FileStorage {
    void put(String bucket, String key, InputStream input, long size, String contentType);
    void delete(String bucket, String key);
    String publicUrl(String bucket, String key);
    String presignedGetUrl(String bucket, String key, Duration expiry);
}
