package crm.ms.files.service;

import crm.ms.files.exception.FileStorageException;
import crm.ms.files.service.storage.ObjectStorage;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Storage use cases. Ownership of vehicle/customer metadata stays with the caller. */
public final class FileService {
    private final ObjectStorage storage;
    private final StorageRules rules;

    public FileService(ObjectStorage storage, StorageRules rules) {
        this.storage = storage;
        this.rules = rules;
    }

    public void put(ClientScope client, String bucket, String key, InputStream source, long size, String contentType) {
        authorize(client, bucket, key);
        if (size < 0) throw error(411, "Content-Length is required");
        if (size == 0) throw error(400, "File must not be empty");
        if (size > rules.maxFileSize()) throw error(413, "File exceeds the upload size limit");
        String type = contentType == null ? "" : contentType.strip().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(type) || "image/pjpeg".equals(type)) {
            type = "image/jpeg";
        } else if ("image/x-png".equals(type)) {
            type = "image/png";
        }
        try (var input = new BufferedInputStream(source)) {
            input.mark(12);
            byte[] signature = input.readNBytes(12);
            input.reset();
            if (!matches(type, signature)) throw error(400, "Unsupported file type or mismatched file signature");
            storage.put(bucket, key, input, size, type);
        } catch (IOException exception) {
            throw new FileStorageException(400, "Unable to read uploaded file", exception);
        }
    }

    public void delete(ClientScope client, String bucket, String key) {
        authorize(client, bucket, key);
        storage.delete(bucket, key);
    }

    public String publicUrl(ClientScope client, String bucket, String key) {
        authorize(client, bucket, key);
        if (!rules.publicBuckets().contains(bucket)) throw error(403, "Bucket is private; request a signed download URL");
        return storage.publicUrl(bucket, key);
    }

    public DownloadLink downloadUrl(ClientScope client, String bucket, String key, long expirySeconds) {
        authorize(client, bucket, key);
        if (expirySeconds < 1 || expirySeconds > rules.maxPresignExpiry().toSeconds()) {
            throw error(400, "Invalid download URL expiry");
        }
        Duration expiry = Duration.ofSeconds(expirySeconds);
        Instant expiresAt = Instant.now().plus(expiry);
        return new DownloadLink(storage.presignedGetUrl(bucket, key, expiry), expiresAt);
    }

    private void authorize(ClientScope client, String bucket, String key) {
        if (client == null) throw error(401, "Service authentication required");
        if (!client.buckets().contains(bucket)) throw error(403, "Bucket is outside the caller's scope");
        if (key == null || key.isBlank() || key.length() > 500 || key.indexOf('\\') >= 0
                || key.chars().anyMatch(Character::isISOControl)) throw error(400, "Invalid object key");
        for (String segment : key.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) throw error(400, "Invalid object key");
        }
        if (!key.startsWith(client.keyPrefix())) throw error(403, "Object key is outside the caller's scope");
    }

    private boolean matches(String type, byte[] bytes) {
        if (bytes == null || bytes.length < 2) return false;
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8})) {
            return type.equals("image/jpeg") || type.startsWith("image/");
        }
        if (startsWith(bytes, new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47, (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a})) {
            return type.equals("image/png") || type.startsWith("image/");
        }
        if (bytes.length >= 12
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return type.equals("image/webp") || type.startsWith("image/");
        }
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return type.equals("application/pdf");
        }
        return false;
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private FileStorageException error(int status, String message) {
        return new FileStorageException(status, message);
    }

    public record DownloadLink(String url, Instant expiresAt) {
    }
}
