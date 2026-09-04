package com.ray.exception;

/** 可安全返回给客户端的业务异常。 */
public class BusinessException extends RuntimeException {
    private final int status;
    private final String code;

    public BusinessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static BusinessException badRequest(String code, String message) {
        return new BusinessException(400, code, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(404, code, message);
    }

    public static BusinessException forbidden(String code, String message) {
        return new BusinessException(403, code, message);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(409, code, message);
    }
}
