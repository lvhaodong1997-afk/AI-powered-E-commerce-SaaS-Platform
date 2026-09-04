package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.Getter;

@Getter
public class TkOpenApiException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public TkOpenApiException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static TkOpenApiException badRequest(String code, String message) {
        return new TkOpenApiException(code, message, 400);
    }

    public static TkOpenApiException unauthorized(String code, String message) {
        return new TkOpenApiException(code, message, 401);
    }

    public static TkOpenApiException forbidden(String code, String message) {
        return new TkOpenApiException(code, message, 403);
    }

    public static TkOpenApiException notFound(String code, String message) {
        return new TkOpenApiException(code, message, 404);
    }

    public static TkOpenApiException conflict(String code, String message) {
        return new TkOpenApiException(code, message, 409);
    }

    public static TkOpenApiException tooManyRequests(String code, String message) {
        return new TkOpenApiException(code, message, 429);
    }

    public static TkOpenApiException unavailable(String code, String message) {
        return new TkOpenApiException(code, message, 503);
    }
}
