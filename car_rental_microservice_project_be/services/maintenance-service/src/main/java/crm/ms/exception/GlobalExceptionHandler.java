package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseApi> handleBusinessException(BusinessException e) {
        HttpStatus status = mapToHttpStatus(e.getCode());
        if (status.is5xxServerError()) {
            log.error("Service error [type={}, code={}]: {}", e.getType(), e.getCode(), sanitize(e.getMessage()));
        } else {
            log.warn("Business error [type={}, code={}]: {}", e.getType(), e.getCode(), sanitize(e.getMessage()));
        }

        ResponseApi response = ResponseApi.builder()
                .code(status)
                .message(e.getMessage())
                .data(null)
                .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.builder()
                        .code(HttpStatus.BAD_REQUEST)
                        .message(message)
                        .data(null)
                        .build());
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ResponseApi> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ResponseApi.builder()
                        .code(HttpStatus.BAD_REQUEST)
                        .message("Invalid request parameter or payload")
                        .data(null)
                        .build());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ResponseApi> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Optimistic locking conflict: {}", sanitize(e.getMessage()));
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResponseApi.builder()
                        .code(HttpStatus.CONFLICT)
                        .message("Maintenance schedule was modified concurrently. Please reload and retry.")
                        .data(null)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi> handleUnexpectedException(Exception e) {
        log.error("Unhandled exception in maintenance-service", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResponseApi.builder()
                        .code(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("Internal server error")
                        .data(null)
                        .build());
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429, 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 502 -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private String sanitize(String msg) {
        return msg == null ? "" : msg.replace('\r', ' ').replace('\n', ' ');
    }
}
