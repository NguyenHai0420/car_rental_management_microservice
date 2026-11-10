package crm.ms.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int type;
    private final int code;
    private final String message;

    public BusinessException(int type, int code, String message) {
        super(message);
        this.type = type;
        this.code = code;
        this.message = message;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(1, 400, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(1, 404, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(1, 409, message);
    }
}
