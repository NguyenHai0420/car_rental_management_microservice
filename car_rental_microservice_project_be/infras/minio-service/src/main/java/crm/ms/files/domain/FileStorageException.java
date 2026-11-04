package crm.ms.files.domain;

public class FileStorageException extends RuntimeException {
    private final int status;

    public FileStorageException(int status, String message) {
        super(message);
        this.status = status;
    }

    public FileStorageException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
