package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseApi> handleBusinessException(BusinessException e) {
        log.error("Business error [type={}, code={}]: {}", e.getType(), e.getCode(), e.getMessage());
        HttpStatus status = mapToHttpStatus(e.getCode());
        return ResponseEntity.status(status).body(ResponseApi.builder()
                .code(status)
                .message(e.getMessage())
                .data(null)
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ResponseApi.builder()
                .code(HttpStatus.BAD_REQUEST)
                .message(message)
                .data(null)
                .build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseApi> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Constraint violation (conflict): {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ResponseApi.builder()
                .code(HttpStatus.CONFLICT)
                .message("Xe đã được đặt trong khoảng thời gian này, vui lòng chọn khung giờ hoặc xe khác.")
                .data(null)
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseApi> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ResponseApi.builder()
                .code(HttpStatus.FORBIDDEN)
                .message("Bạn không có quyền thực hiện thao tác này.")
                .data(null)
                .build());
    }

    private HttpStatus mapToHttpStatus(int code) {
        return switch (code) {
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
