package crm.ms.exception;

/**
 * Đại diện cho lỗi không xác định được kết quả từ service ngoài
 * (timeout, rớt mạng, HTTP 5xx, 503). Khi gặp ngoại lệ này trong luồng Saga,
 * không được rollback trạng thái trung gian một cách mù quáng để tránh mất đồng bộ.
 */
public class ExternalServiceUnavailableException extends BusinessException {

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(2, 503, message);
        initCause(cause);
    }

    public ExternalServiceUnavailableException(String message) {
        super(2, 503, message);
    }
}
