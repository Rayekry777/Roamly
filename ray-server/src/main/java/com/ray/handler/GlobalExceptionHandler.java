package com.ray.handler;

import cn.dev33.satoken.exception.NotLoginException;
import com.ray.exception.BusinessException;
import com.ray.result.ErrorResult;
import com.ray.result.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将业务、鉴权和校验异常统一转换为稳定的 API 错误结构。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResult> business(BusinessException exception) {
        return ResponseEntity.status(exception.status()).body(ErrorResult.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ErrorResult> notLoggedIn(NotLoginException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResult.of("UNAUTHORIZED", "登录已失效，请重新登录"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResult> invalidBody(MethodArgumentNotValidException exception) {
        List<FieldErrorDetail> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(
                        error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "参数格式错误")))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResult("VALIDATION_FAILED", "请求参数校验失败", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResult> invalidParameter(ConstraintViolationException exception) {
        List<FieldErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(error -> new FieldErrorDetail(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ErrorResult("VALIDATION_FAILED", "请求参数校验失败", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResult> unreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ErrorResult.of("INVALID_REQUEST_BODY", "请求体格式错误"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ServletRequestBindingException.class})
    public ResponseEntity<ErrorResult> invalidRequestParameter(Exception exception) {
        return ResponseEntity.badRequest().body(ErrorResult.of("INVALID_PARAMETER", "请求参数格式错误或缺失"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResult> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResult.of("IMAGE_TOO_LARGE", "单张图片不能超过10MB"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResult> resourceNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResult.of("RESOURCE_NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult> unexpected(Exception exception) {
        log.error("[全局异常] 请求处理失败", exception);
        return ResponseEntity.internalServerError().body(ErrorResult.of("INTERNAL_ERROR", "服务器内部错误"));
    }
}
