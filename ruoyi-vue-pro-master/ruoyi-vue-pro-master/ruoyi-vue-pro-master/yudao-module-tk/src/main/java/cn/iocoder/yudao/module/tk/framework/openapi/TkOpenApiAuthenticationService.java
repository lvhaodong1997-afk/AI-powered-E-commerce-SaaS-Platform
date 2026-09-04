package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class TkOpenApiAuthenticationService {

    private final TkOpenApiClientMapper clientMapper;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkOpenApiGatewayPolicy gatewayPolicy;
    private final Clock clock;
    private final long timestampToleranceSeconds;

    @Autowired
    public TkOpenApiAuthenticationService(TkOpenApiClientMapper clientMapper,
                                          TkOpenApiSecretCipher secretCipher,
                                          TkOpenApiGatewayPolicy gatewayPolicy,
                                          @Value("${tk.open-api.timestamp-tolerance-seconds:300}") long timestampToleranceSeconds) {
        this(clientMapper, secretCipher, gatewayPolicy, Clock.systemUTC(), timestampToleranceSeconds);
    }

    TkOpenApiAuthenticationService(TkOpenApiClientMapper clientMapper, TkOpenApiSecretCipher secretCipher,
                                   TkOpenApiGatewayPolicy gatewayPolicy, Clock clock, long timestampToleranceSeconds) {
        this.clientMapper = clientMapper;
        this.secretCipher = secretCipher;
        this.gatewayPolicy = gatewayPolicy;
        this.clock = clock;
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public TkOpenApiPrincipal authenticate(TkOpenApiAuthRequest request) {
        validateHeaders(request);
        validateTimestamp(request.getTimestamp());
        TkOpenApiClientDO client = clientMapper.selectByClientId(request.getClientId());
        if (client == null || !Integer.valueOf(0).equals(client.getStatus())) {
            throw TkOpenApiException.unauthorized("OPEN_API_CLIENT_INVALID", "clientId is invalid or disabled");
        }
        if (!TkOpenApiIpMatcher.matches(client.getAllowedIps(), request.getClientIp())) {
            throw TkOpenApiException.forbidden("OPEN_API_IP_NOT_ALLOWED", "client IP is not allowed");
        }
        String canonical = TkOpenApiSigner.canonicalRequest(request.getMethod(), request.getRequestTarget(),
                request.getTimestamp(), request.getNonce(), request.getBody());
        String expected = TkOpenApiSigner.hmacBase64(decryptSecret(client.getClientSecretCipher()), canonical);
        if (!TkOpenApiSigner.matches(expected, request.getSignature())) {
            throw TkOpenApiException.unauthorized("OPEN_API_SIGNATURE_INVALID", "request signature is invalid");
        }
        TkOpenApiPrincipal principal = new TkOpenApiPrincipal(client.getClientId(), client.getClientName(),
                client.getPermissions());
        if (!principal.hasPermission(request.getRequiredPermission())) {
            throw TkOpenApiException.forbidden("OPEN_API_PERMISSION_DENIED", "client permission is insufficient");
        }
        gatewayPolicy.checkAndConsume(client.getClientId(), request.getNonce(),
                valueOrDefault(client.getRateLimitPerMinute(), 120), valueOrDefault(client.getDailyQuota(), 10000),
                Math.max(60, timestampToleranceSeconds));
        return principal;
    }

    private void validateHeaders(TkOpenApiAuthRequest request) {
        if (request == null || StrUtil.hasBlank(request.getClientId(), request.getTimestamp(), request.getNonce(),
                request.getSignature(), request.getMethod(), request.getRequestTarget())) {
            throw TkOpenApiException.unauthorized("OPEN_API_AUTH_HEADER_MISSING", "required authentication header is missing");
        }
    }

    private void validateTimestamp(String timestamp) {
        try {
            long requestSeconds = Long.parseLong(timestamp);
            long nowSeconds = clock.instant().getEpochSecond();
            long toleranceSeconds = Math.max(0, timestampToleranceSeconds);
            long lowerBound = nowSeconds < Long.MIN_VALUE + toleranceSeconds
                    ? Long.MIN_VALUE : nowSeconds - toleranceSeconds;
            long upperBound = nowSeconds > Long.MAX_VALUE - toleranceSeconds
                    ? Long.MAX_VALUE : nowSeconds + toleranceSeconds;
            if (requestSeconds < lowerBound || requestSeconds > upperBound) {
                throw TkOpenApiException.unauthorized("OPEN_API_TIMESTAMP_EXPIRED", "request timestamp has expired");
            }
        } catch (NumberFormatException ex) {
            throw TkOpenApiException.unauthorized("OPEN_API_TIMESTAMP_EXPIRED", "request timestamp is invalid");
        }
    }

    private String decryptSecret(String cipherText) {
        try {
            return secretCipher.decrypt(cipherText);
        } catch (Exception ex) {
            throw TkOpenApiException.unavailable("OPEN_API_SECRET_UNAVAILABLE", "client secret cannot be loaded");
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }
}
