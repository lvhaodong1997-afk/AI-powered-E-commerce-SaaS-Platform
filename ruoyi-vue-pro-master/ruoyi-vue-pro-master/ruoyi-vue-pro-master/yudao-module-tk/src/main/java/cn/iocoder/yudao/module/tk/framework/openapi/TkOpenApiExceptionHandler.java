package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@RestControllerAdvice(basePackages = "cn.iocoder.yudao.module.tk.controller.open.tiktok")
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TkOpenApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("[handleUnexpected][requestId({})]", TkOpenApiContext.getRequestId(), ex);
        return ResponseEntity.status(500).body(TkOpenApiResponse.error("OPEN_API_INTERNAL_ERROR",
                "internal server error", TkOpenApiContext.getRequestId()));
    }
}
