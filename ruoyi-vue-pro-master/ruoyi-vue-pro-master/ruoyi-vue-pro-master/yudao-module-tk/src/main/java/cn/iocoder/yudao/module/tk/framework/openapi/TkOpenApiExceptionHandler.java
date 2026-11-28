package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = {"cn.iocoder.yudao.module.tk.controller.open.tiktok",
        "cn.iocoder.yudao.module.tk.controller.open.x", "cn.iocoder.yudao.module.tk.controller.open.youtube",
        "cn.iocoder.yudao.module.tk.controller.open.meta"})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class TkOpenApiExceptionHandler {

    @ExceptionHandler(TkOpenApiException.class)
    public ResponseEntity<TkOpenApiResponse<Void>> handleOpenApiException(TkOpenApiException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(TkOpenApiResponse.error(ex.getCode(), ex.getMessage(), TkOpenApiContext.getRequestId()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            IllegalArgumentException.class})
    public ResponseEntity<TkOpenApiResponse<Void>> handleValidation(Exception ex) {
        return ResponseEntity.badRequest().body(TkOpenApiResponse.error("OPEN_API_PARAMETER_INVALID",
                ex.getMessage(), TkOpenApiContext.getRequestId()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<TkOpenApiResponse<Void>> handleBodyValidation() {
        return ResponseEntity.badRequest().body(TkOpenApiResponse.error("OPEN_API_PARAMETER_INVALID",
                "Malformed request body or path parameter", TkOpenApiContext.getRequestId()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<TkOpenApiResponse<Void>> handleUnsupportedMediaType() {
        return ResponseEntity.status(415).body(TkOpenApiResponse.error("OPEN_API_PARAMETER_INVALID",
                "Unsupported Content-Type", TkOpenApiContext.getRequestId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TkOpenApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[handleUnexpected][requestId({})]", TkOpenApiContext.getRequestId(), ex);
        return ResponseEntity.status(500).body(TkOpenApiResponse.error("OPEN_API_INTERNAL_ERROR",
                "internal server error", TkOpenApiContext.getRequestId()));
    }
}
