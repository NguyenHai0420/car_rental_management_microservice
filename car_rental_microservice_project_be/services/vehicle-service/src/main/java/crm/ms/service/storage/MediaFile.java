package crm.ms.service.storage;

import java.io.InputStream;

public record MediaFile(InputStream input, long size, String contentType, String fileName) {
}
