package com.ray.handler;

import cn.dev33.satoken.exception.NotLoginException;
import com.ray.exception.BusinessException;
import com.ray.result.ErrorResult;
import com.ray.result.FieldErrorDetail;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
    public void notLoggedIn(NotLoginException exception, HttpServletResponse response) throws IOException {
        // 鉴权异常可能来自图片、SSE 等只接受非 JSON 的请求；直接写入固定 JSON，避免再次触发内容协商异常。
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"登录已失效，请重新登录\",\"fieldErrors\":[]}");
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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResult> invalidMethodParameter(
            HandlerMethodValidationException exception) {
        List<FieldErrorDetail> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream().map(error -> {
                    String parameterName = result.getMethodParameter().getParameterName();
                    return new FieldErrorDetail(
                            Objects.requireNonNullElse(parameterName, "parameter"),
                            Objects.requireNonNullElse(error.getDefaultMessage(), "参数格式错误"));
                }))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResult("VALIDATION_FAILED", "请求参数校验失败", errors));
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
                .body(ErrorResult.of("MEDIA_TOO_LARGE", "单张图片不能超过10MB"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResult> resourceNotFound(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResult.of("RESOURCE_NOT_FOUND", "资源不存在"));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void asyncRequestTimeout(
            AsyncRequestTimeoutException exception, HttpServletResponse response) throws IOException {
        String contentType = response.getContentType();
        if (response.isCommitted()
                || (contentType != null && contentType.startsWith("text/event-stream"))) {
            // SSE 响应可能已经提交，超时后不能再交给消息转换器写入普通 JSON 错误体。
            log.debug("[实时连接] 异步请求已超时并断开");
            return;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"ASYNC_REQUEST_TIMEOUT\",\"message\":\"请求处理超时，请稍后重试\",\"fieldErrors\":[]}");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResult> unexpected(Exception exception) {
        log.error("[全局异常] 请求处理失败", exception);
        return ResponseEntity.internalServerError().body(ErrorResult.of("INTERNAL_ERROR", "服务器内部错误"));
    }
}
