package cn.iocoder.yudao.module.tk.service.open.x;

public final class TkOpenXCallbackOutcome {
    private final boolean handled;
    private final boolean success;
    private final String redirectUrl;

    private TkOpenXCallbackOutcome(boolean handled, boolean success, String redirectUrl) {
        this.handled = handled;
        this.success = success;
        this.redirectUrl = redirectUrl;
    }

    public static TkOpenXCallbackOutcome unhandled() {
        return new TkOpenXCallbackOutcome(false, false, null);
    }

    public static TkOpenXCallbackOutcome page(boolean success) {
        return new TkOpenXCallbackOutcome(true, success, null);
    }

    public static TkOpenXCallbackOutcome redirect(boolean success, String redirectUrl) {
        return new TkOpenXCallbackOutcome(true, success, redirectUrl);
    }

    public boolean isHandled() { return handled; }
    public boolean isSuccess() { return success; }
    public String getRedirectUrl() { return redirectUrl; }
}