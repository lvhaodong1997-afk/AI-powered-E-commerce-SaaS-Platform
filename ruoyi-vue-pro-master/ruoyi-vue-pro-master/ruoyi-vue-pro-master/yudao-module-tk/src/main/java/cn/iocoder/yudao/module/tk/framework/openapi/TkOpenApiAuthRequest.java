package cn.iocoder.yudao.module.tk.framework.openapi;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TkOpenApiAuthRequest {
    private final String clientId;
    private final String timestamp;
    private final String nonce;
    private final String signature;
    private final String method;
    private final String requestTarget;
    private final byte[] body;
    private final String clientIp;
    private final String requiredPermission;
}
