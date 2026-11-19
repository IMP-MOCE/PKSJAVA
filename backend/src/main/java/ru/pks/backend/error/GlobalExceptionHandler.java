package ru.pks.backend.error;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.pks.backend.dto.ApiError;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception ex, HttpServletRequest request) {
        return response(404, "NOT_FOUND", "Запись или страница не найдена", request, Map.of());
    }

    @ExceptionHandler({ConflictException.class, OptimisticLockException.class,
            ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> conflict(Exception ex, HttpServletRequest request) {
        return response(409, "CONFLICT", ex instanceof ConflictException ? ex.getMessage()
                : "Данные изменены или конфликтуют с существующими записями", request, Map.of());
    }

    @ExceptionHandler({BadRequestException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> invalid(Exception ex, HttpServletRequest request) {
        return response(400, "BAD_REQUEST", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformed(Exception ex, HttpServletRequest request) {
        return response(400, "BAD_REQUEST", "Некорректный формат запроса", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (var error : ex.getBindingResult().getFieldErrors())
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        return response(400, "VALIDATION_FAILED", "Проверьте заполнение полей", request, fields);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiError> authentication(AuthenticationFailedException ex, HttpServletRequest request) {
        return response(401, "AUTHENTICATION_FAILED", ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return response(403, "ACCESS_DENIED", "Недостаточно прав", request, Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> fileSize(Exception ex, HttpServletRequest request) {
        return response(413, "FILE_TOO_LARGE", "Файл должен быть не больше 5 МБ", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Ошибка запроса {}", responseId(), ex);
        return response(500, "INTERNAL_ERROR", "Внутренняя ошибка, сообщите администратору код запроса", request, Map.of());
    }

    private ResponseEntity<ApiError> response(int status, String code, String message,
            HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store")
                .body(new ApiError(Instant.now(), status, code, message,
                        request.getRequestURI(), responseId(), fields));
    }

    private String responseId() { return org.slf4j.MDC.get("correlationId"); }
}
