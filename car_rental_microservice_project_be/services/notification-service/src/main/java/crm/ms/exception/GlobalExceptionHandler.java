package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseApi> handleBusinessException(BusinessException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };

        log.warn("Business exception [code={}, status={}]: {}", ex.getCode(), status.value(), ex.getMessage());

        return ResponseEntity.status(status).body(ResponseApi.builder()
                .code(status)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        String summary = errors.isEmpty() ? "Validation failed" : errors.values().iterator().next();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseApi.builder()
                .code(HttpStatus.BAD_REQUEST)
                .message(summary)
                .data(errors)
                .build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseApi> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseApi.builder()
                .code(HttpStatus.BAD_REQUEST)
                .message(ex.getMessage())
                .build());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ResponseApi> handleBadPayload(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseApi.builder()
                .code(HttpStatus.BAD_REQUEST)
                .message("Invalid request format: " + ex.getMessage())
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseApi> handleGenericException(Exception ex) {
        log.error("Unhandled exception in notification-service", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseApi.builder()
                .code(HttpStatus.INTERNAL_SERVER_ERROR)
                .message("Internal server error")
                .build());
    }
}
