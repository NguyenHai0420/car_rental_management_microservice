package crm.ms.files.application.port;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorage {
    void put(String bucket, String key, InputStream input, long size, String contentType);
    void delete(String bucket, String key);
    String publicUrl(String bucket, String key);
    String presignedGetUrl(String bucket, String key, Duration expiry);
}
