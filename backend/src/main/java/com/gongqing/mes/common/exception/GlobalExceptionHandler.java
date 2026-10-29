package com.gongqing.mes.common.exception;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.web.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return response(errorCode.getStatus(), errorCode.getCode(), exception.getMessage(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有执行此操作的权限", null);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(Exception exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException validationException) {
            validationException.getBindingResult().getFieldErrors()
                    .forEach(error -> errors.putIfAbsent(error.getField(), validationMessage(error)));
            validationException.getBindingResult().getGlobalErrors()
                    .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        } else if (exception instanceof BindException bindException) {
            bindException.getBindingResult().getFieldErrors()
                    .forEach(error -> errors.putIfAbsent(error.getField(), validationMessage(error)));
            bindException.getBindingResult().getGlobalErrors()
                    .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        }
        return response(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(), "请求参数校验失败", errors);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "请求的资源不存在", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不支持", null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(), "请求参数校验失败", null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return response(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(), "请求参数校验失败", errors);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST.getCode(), "请求格式不正确", null);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.DATABASE_ERROR.getCode(),
                ErrorCode.DATABASE_ERROR.getDefaultMessage(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Database constraint rejected a request", exception);
        return response(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT.getCode(),
                ErrorCode.DATA_CONFLICT.getDefaultMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(), null);
    }

    private String validationMessage(FieldError error) {
        return error.getDefaultMessage() == null ? "字段值不合法" : error.getDefaultMessage();
    }

    private <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, String code, String message, T data) {
        String traceId = TraceIdFilter.currentTraceId();
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, data, traceId));
    }
}
