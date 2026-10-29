package cn.iocoder.yudao.module.tk.service.tiktokshop;

/** Never carries response text, a request URL, or a cause containing credentials. */
public class TkTiktokShopApiException extends RuntimeException {
    private final String code;
    private final String requestId;
    public TkTiktokShopApiException(String code, String requestId) {
        super("TikTok Shop request failed");
        this.code = safe(code, "UPSTREAM_ERROR");
        this.requestId = safe(requestId, null);
    }
    private static String safe(String value, String fallback) {
        return value != null && value.matches("[A-Za-z0-9_:-]{1,128}") ? value : fallback;
    }
    public String getCode() { return code; }
    public String getRequestId() { return requestId; }
    public boolean isReauthorizationRequired() {
        return "105001".equals(code) || "105002".equals(code) || "105005".equals(code)
                || "101000".equals(code) || "INVALID_IDENTITY".equals(code) || "INVALID_TOKEN".equals(code);
    }
}
