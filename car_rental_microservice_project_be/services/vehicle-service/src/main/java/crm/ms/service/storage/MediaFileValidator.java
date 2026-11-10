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
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "application/pdf", "pdf");
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
        try {
            input.mark(12);
            byte[] signature = input.readNBytes(12);
            input.reset();
            if (!matches(type, signature)) {
                throw invalid("File content does not match its declared content type");
            }
        } catch (IOException exception) {
            throw new BusinessException(1, 400, "Unable to read uploaded file", exception);
        }
        return new ValidatedFile(input, file.size(), type, name, EXTENSIONS.get(type));
    }

    private boolean matches(String type, byte[] bytes) {
        return switch (type) {
            case "image/jpeg" -> startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "image/png" -> startsWith(bytes, new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/webp" -> bytes.length >= 12
                    && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case "application/pdf" -> startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
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
