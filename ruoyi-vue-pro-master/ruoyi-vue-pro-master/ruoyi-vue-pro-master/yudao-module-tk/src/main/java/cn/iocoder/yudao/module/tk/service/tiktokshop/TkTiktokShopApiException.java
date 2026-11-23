package cn.iocoder.yudao.module.tk.service.tiktokshop;

/** Never carries response text, a request URL, or a cause containing credentials. */
public class TkTiktokShopApiException extends RuntimeException {
    public static final String CREATOR_MARKETPLACE_ACCESS_DENIED = "CREATOR_MARKETPLACE_ACCESS_DENIED";
    public static final String CREATOR_PERMISSION_DENIED = "CREATOR_PERMISSION_DENIED";
    public static final String CREATOR_REFRESH_PENDING = "CREATOR_REFRESH_PENDING";
    public static final String CREATOR_REAUTHORIZE = "CREATOR_REAUTHORIZE";
    public static final String SELLER_ANALYTICS_ACCESS_DENIED = "SELLER_ANALYTICS_ACCESS_DENIED";
    public static final String SELLER_FEATURE_ACCESS_DENIED = "SELLER_FEATURE_ACCESS_DENIED";
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
    boolean isAccessTokenRejected() { return "105001".equals(code) || "105002".equals(code); }
    boolean isPermissionDenied() { return "105005".equals(code); }
    TkTiktokShopApiException creatorOperationError() {
        return isPermissionDenied() ? new TkTiktokShopApiException(CREATOR_PERMISSION_DENIED, requestId)
                : isAccessTokenRejected() ? new TkTiktokShopApiException(CREATOR_REFRESH_PENDING, requestId) : this;
    }
    public boolean isReauthorizationRequired() {
        return CREATOR_REAUTHORIZE.equals(code) || isAccessTokenRejected() || "105005".equals(code)
                || "101000".equals(code) || "INVALID_IDENTITY".equals(code) || "INVALID_TOKEN".equals(code)
                || "WRONG_ACCOUNT_TYPE".equals(code) || "INSUFFICIENT_SCOPE".equals(code);
    }
}
