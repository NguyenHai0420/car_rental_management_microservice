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
        log.error("Business error [type={}, code={}]: {}", e.getType(), e.getCode(), e.getMessage());

        HttpStatus status = mapToHttpStatus(e.getCode());

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
                        .message("Invalid request parameter or payload: " + e.getMessage())
                        .data(null)
                        .build());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ResponseApi> handleOptimisticLock(OptimisticLockingFailureException e) {
        log.warn("Optimistic locking conflict: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ResponseApi.builder()
                        .code(HttpStatus.CONFLICT)
                        .message("Maintenance schedule was modified concurrently. Please reload and retry.")
                        .data(null)
                        .build());
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 502 -> HttpStatus.BAD_GATEWAY;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
