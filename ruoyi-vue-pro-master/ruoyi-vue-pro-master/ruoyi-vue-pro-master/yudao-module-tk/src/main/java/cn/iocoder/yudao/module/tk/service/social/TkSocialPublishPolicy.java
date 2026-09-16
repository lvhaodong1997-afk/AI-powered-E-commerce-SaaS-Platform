package cn.iocoder.yudao.module.tk.service.social;

import java.util.List;

public final class TkSocialPublishPolicy {
    private TkSocialPublishPolicy() {}
    public static boolean canRetry(String status) {
        return "FAILED".equals(status) || "REAUTH_REQUIRED".equals(status);
    }
    public static String aggregate(List<String> states) {
        if (states.isEmpty()) throw new IllegalArgumentException("发布任务必须包含目标账号");
        if (states.stream().allMatch("PENDING"::equals)) return "PENDING";
        if (states.stream().anyMatch(s -> "PENDING".equals(s) || "PROCESSING".equals(s))) return "PROCESSING";
        if (states.stream().allMatch("SUCCESS"::equals)) return "SUCCESS";
        if (states.contains("UNKNOWN")) return "UNKNOWN";
        if (states.contains("SUCCESS")) return "PARTIAL_SUCCESS";
        if (states.contains("REAUTH_REQUIRED")) return "REAUTH_REQUIRED";
        return "FAILED";
    }
}
