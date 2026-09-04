package cn.iocoder.yudao.module.tk.framework.openapi;

public interface TkOpenApiGatewayPolicy {

    void checkAndConsume(String clientId, String nonce, int rateLimitPerMinute,
                         int dailyQuota, long nonceTtlSeconds);
}
