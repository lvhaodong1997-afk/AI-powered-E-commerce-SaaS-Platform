package cn.iocoder.yudao.module.tk.service.open.x;

public final class TkOpenXCallbackOutcome {
    private final boolean handled;
    private final boolean success;
    private final String redirectUrl;
    private final String failReason;

    private TkOpenXCallbackOutcome(boolean handled, boolean success, String redirectUrl, String failReason) {
        this.handled = handled;
        this.success = success;
        this.redirectUrl = redirectUrl;
        this.failReason = failReason;
    }

    public static TkOpenXCallbackOutcome unhandled() {
        return new TkOpenXCallbackOutcome(false, false, null, null);
    }

    public static TkOpenXCallbackOutcome page(boolean success) {
        return new TkOpenXCallbackOutcome(true, success, null, null);
    }

    public static TkOpenXCallbackOutcome failurePage(String failReason) {
        return new TkOpenXCallbackOutcome(true, false, null, failReason);
    }

    public static TkOpenXCallbackOutcome redirect(boolean success, String redirectUrl) {
        return new TkOpenXCallbackOutcome(true, success, redirectUrl, null);
    }

    public boolean isHandled() { return handled; }
    public boolean isSuccess() { return success; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getFailReason() { return failReason; }
}
