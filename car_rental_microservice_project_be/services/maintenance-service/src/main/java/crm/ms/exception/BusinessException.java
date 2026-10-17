package crm.ms.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private int type;
    private final int code;
    private final String message;

    public BusinessException(int type, int code, String message) {
        super(message);
        this.type = type;
        this.code = code;
        this.message = message;
    }
}
