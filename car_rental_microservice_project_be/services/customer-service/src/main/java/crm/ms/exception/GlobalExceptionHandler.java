package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseApi> handleBusinessException(BusinessException e) {
        log.error("Business error [type={}, code={}]: {}", e.getType(), e.getCode(), e.getMessage());

        HttpStatus status = mapToHttpStatus(e.getCode());

        ResponseApi response = ResponseApi.builder()
                .code(status)
                .message(e.getMessage())
                .data(null)
                .build();

        return ResponseEntity.status(status).body(response);
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
