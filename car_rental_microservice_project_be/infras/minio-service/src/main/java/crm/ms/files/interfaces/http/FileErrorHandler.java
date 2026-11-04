package crm.ms.files.interfaces.http;

import crm.ms.files.domain.FileStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

@RestControllerAdvice
public class FileErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(FileErrorHandler.class);

    @ExceptionHandler(FileStorageException.class)
    ResponseEntity<ServiceAuthenticationFilter.ApiError> storageError(FileStorageException exception) {
        if (exception.status() >= 500) LOG.error("Object storage operation failed", exception);
        return ResponseEntity.status(exception.status()).body(
                new ServiceAuthenticationFilter.ApiError(exception.status(), exception.getMessage()));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class, IOException.class})
    ResponseEntity<ServiceAuthenticationFilter.ApiError> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ServiceAuthenticationFilter.ApiError(400, "Invalid request"));
    }
}
