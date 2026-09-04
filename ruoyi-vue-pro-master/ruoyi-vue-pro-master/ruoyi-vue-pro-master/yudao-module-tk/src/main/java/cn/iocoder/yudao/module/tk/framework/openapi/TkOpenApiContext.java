package cn.iocoder.yudao.module.tk.framework.openapi;

import java.util.function.Supplier;

public final class TkOpenApiContext {

    private static final ThreadLocal<TkOpenApiPrincipal> PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private TkOpenApiContext() {
    }

    public static TkOpenApiPrincipal getPrincipal() {
        return PRINCIPAL.get();
    }

    public static TkOpenApiPrincipal getRequiredPrincipal() {
        TkOpenApiPrincipal principal = getPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Open API client context is missing");
        }
        return principal;
    }

    public static String getRequestId() {
        return REQUEST_ID.get();
    }

    public static void set(TkOpenApiPrincipal principal, String requestId) {
        PRINCIPAL.set(principal);
        REQUEST_ID.set(requestId);
    }

    public static void clear() {
        PRINCIPAL.remove();
        REQUEST_ID.remove();
    }

    public static <T> T call(TkOpenApiPrincipal principal, String requestId, Supplier<T> action) {
        TkOpenApiPrincipal oldPrincipal = PRINCIPAL.get();
        String oldRequestId = REQUEST_ID.get();
        set(principal, requestId);
        try {
            return action.get();
        } finally {
            if (oldPrincipal == null) {
                clear();
            } else {
                set(oldPrincipal, oldRequestId);
            }
        }
    }

}
