package crm.ms.service.storage;

import crm.ms.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
public class MediaFileValidator {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private final MediaStorageSettings settings;

    public MediaFileValidator(MediaStorageSettings settings) {
        this.settings = settings;
    }

    public ValidatedFile validate(MediaFile file, boolean document) {
        if (file == null || file.input() == null || file.size() <= 0) {
            throw invalid("File must not be empty");
        }
        if (file.size() > settings.maxFileSize()) {
            throw new BusinessException(1, 413, "File exceeds the upload size limit");
        }
        String type = file.contentType() == null ? "" : file.contentType().strip().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(type) || "image/pjpeg".equals(type)) {
            type = "image/jpeg";
        } else if ("image/x-png".equals(type)) {
            type = "image/png";
        }

        if (!EXTENSIONS.containsKey(type) || (!document && type.equals("application/pdf"))) {
            throw invalid("Unsupported file type");
        }
        String name = file.fileName() == null ? "upload." + EXTENSIONS.get(type)
                : file.fileName().replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isBlank() || name.length() > 255) {
            throw invalid("File name must contain between 1 and 255 characters");
        }

        BufferedInputStream input = new BufferedInputStream(file.input());
        String detectedType;
        try {
            input.mark(16);
            byte[] signature = input.readNBytes(16);
            input.reset();
            detectedType = detectActualType(signature);
        } catch (IOException exception) {
            throw new BusinessException(1, 400, "Unable to read uploaded file", exception);
        }

        if (detectedType == null) {
            throw invalid("File content does not match its declared content type");
        }

        if (document) {
            if (!detectedType.equals("application/pdf") && !detectedType.startsWith("image/")) {
                throw invalid("File content does not match its declared content type");
            }
        } else {
            if (!detectedType.startsWith("image/")) {
                throw invalid("File content does not match its declared content type");
            }
        }

        // Use the actual detected image/doc format so webp/png/jpeg files disguised by extension upload cleanly
        String finalType = detectedType;
        String finalExt = EXTENSIONS.getOrDefault(finalType, EXTENSIONS.get(type));

        return new ValidatedFile(input, file.size(), finalType, name, finalExt);
    }

    private String detectActualType(byte[] bytes) {
        if (bytes == null || bytes.length < 2) {
            return null;
        }
        // JPEG starts with SOI: 0xFF, 0xD8
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8})) {
            return "image/jpeg";
        }
        // PNG starts with 8-byte header: 89 50 4E 47 0D 0A 1A 0A
        if (startsWith(bytes, new byte[]{(byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47, (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a})) {
            return "image/png";
        }
        // WebP starts with RIFF....WEBP
        if (bytes.length >= 12
                && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return "image/webp";
        }
        // PDF starts with %PDF-
        if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
            return "application/pdf";
        }
        return null;
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) return false;
        }
        return true;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(1, 400, message);
    }

    public record ValidatedFile(InputStream input, long size, String contentType, String fileName, String extension) {
    }
}
