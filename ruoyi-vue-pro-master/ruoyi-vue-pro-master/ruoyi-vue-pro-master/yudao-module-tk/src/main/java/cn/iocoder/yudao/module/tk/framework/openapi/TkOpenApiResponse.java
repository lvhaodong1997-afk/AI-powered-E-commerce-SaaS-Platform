package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkOpenApiResponse<T> {
    private Object code;
    private String msg;
    private T data;
    private String requestId;

    public static <T> TkOpenApiResponse<T> success(T data) {
        return new TkOpenApiResponse<>(0, "OK", data, TkOpenApiContext.getRequestId());
    }

    public static TkOpenApiResponse<Void> error(String code, String message, String requestId) {
        return new TkOpenApiResponse<>(code, message, null, requestId);
    }
}
