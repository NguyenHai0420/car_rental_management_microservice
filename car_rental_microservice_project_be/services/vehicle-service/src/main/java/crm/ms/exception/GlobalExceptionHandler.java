package crm.ms.exception;

import crm.ms.dto.ResponseApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

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
        HttpStatus status = HttpStatus.resolve(code);
        return status != null && status.isError() ? status : HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ResponseApi> handleUploadSize(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the upload size limit");
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ResponseApi> handleInvalidRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid or missing request parameter");
    }

    private ResponseEntity<ResponseApi> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ResponseApi.builder().code(status).message(message).data(null).build());
    }
}
