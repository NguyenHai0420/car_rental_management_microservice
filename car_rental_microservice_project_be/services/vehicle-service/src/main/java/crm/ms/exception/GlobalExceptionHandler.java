package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final Map<String, String> DUPLICATE_MESSAGES = Map.of(
            "uk_vehicles_vin", "VIN already exists",
            "uk_vehicles_license_plate", "License plate already exists",
            "uk_categories_name", "Vehicle category name already exists",
            "uk_models_brand_name_year", "Vehicle model already exists for this brand, name and year"
    );

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
        HttpStatus status = HttpStatus.resolve(code);
        return status != null && status.isError() ? status : HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseApi> handleUploadSize(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the upload size limit");
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ResponseApi> handleInvalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid or missing request parameter");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseApi> handleInvalidBody(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldError();
        String message = field == null ? "Invalid request body" : field.getField() + " " + field.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseApi> handleIntegrityViolation(DataIntegrityViolationException exception) {
        if (exception.getMostSpecificCause() instanceof SQLException sql
                && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
            String constraint = exception.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null ? violation.getConstraintName().toLowerCase(Locale.ROOT) : "";
            String message = DUPLICATE_MESSAGES.entrySet().stream()
                    .filter(entry -> constraint.contains(entry.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse("A record with the same unique fields already exists");
            return error(HttpStatus.CONFLICT, message);
        }
        log.error("Unexpected database integrity violation", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to save data");
    }

    private ResponseEntity<ResponseApi> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ResponseApi.builder().code(status).message(message).data(null).build());
    }
}
