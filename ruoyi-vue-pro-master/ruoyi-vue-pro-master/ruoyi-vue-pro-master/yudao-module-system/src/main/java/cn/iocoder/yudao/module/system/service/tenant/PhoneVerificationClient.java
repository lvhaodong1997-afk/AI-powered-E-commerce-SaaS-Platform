package cn.iocoder.yudao.module.system.service.tenant;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationSmsRespVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/** TK's app-specific SMS verification bridge; never queries the provider's user registry. */
@Component
public class PhoneVerificationClient {
    @Resource private TenantRegistrationProperties properties;
    @Resource private ObjectMapper objectMapper;

    public TenantRegistrationSmsRespVO send(String mobile, String requestId, String clientIp) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("mobile", mobile); body.put("requestId", requestId); body.put("purpose", "REGISTER"); body.put("clientIp", clientIp);
        JsonNode data = post("/internal/phone-verification/send", body);
        if (!data.path("smsRequestId").isTextual() || data.path("smsRequestId").asText().isEmpty()
                || data.path("expiresInSeconds").asInt() <= 0 || data.path("resendAfterSeconds").asInt() <= 0) {
            throw unavailable();
        }
        return new TenantRegistrationSmsRespVO().setSmsRequestId(data.path("smsRequestId").asText())
                .setDeliveryStatus(data.path("deliveryStatus").asText()).setExpiresInSeconds(data.path("expiresInSeconds").asInt())
                .setResendAfterSeconds(data.path("resendAfterSeconds").asInt());
    }

    public void consume(TenantRegistrationReqVO request) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("mobile", request.getMobile()); body.put("smsRequestId", request.getSmsRequestId());
        body.put("code", request.getSmsCode()); body.put("registrationRequestId", request.getRequestId()); body.put("purpose", "REGISTER");
        validateBinding(post("/internal/phone-verification/consume", body), request);
    }

    static void validateBinding(JsonNode data, TenantRegistrationReqVO request) {
        if (!data.path("verified").isBoolean() || !data.path("verified").asBoolean()
                || !"clipforge".equals(data.path("appId").asText()) || !"REGISTER".equals(data.path("purpose").asText())
                || !request.getMobile().equals(data.path("mobile").asText())
                || !request.getRequestId().equals(data.path("registrationRequestId").asText())) {
            throw exception0(400, "短信验证结果不匹配");
        }
    }

    private JsonNode post(String path, Map<String,Object> body) {
        if (!properties.smsAvailable()) { throw unavailable(); }
        try {
            String raw = objectMapper.writeValueAsString(body);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String url = properties.getPhoneBaseUrl().replaceAll("/$", "") + path;
            try (HttpResponse response = HttpRequest.post(url).charset(StandardCharsets.UTF_8).timeout(8000)
                    .header("Content-Type", "application/json").header("X-TK-App-Id", "clipforge")
                    .header("X-TK-Timestamp", timestamp).header("X-TK-Nonce", nonce)
                    .header("X-TK-Signature", sign(properties.getPhoneSecret(), timestamp, nonce, "POST", path, raw))
                    .body(raw).execute()) {
                if (!response.isOk()) { throw unavailable(); }
                JsonNode result = objectMapper.readTree(response.body());
                if (result == null || result.path("code").asInt() != 200 || !result.path("data").isObject()) { throw unavailable(); }
                return result.path("data");
            }
        } catch (Exception e) { throw unavailable(); }
    }

    static String sign(String secret, String timestamp, String nonce, String method, String path, String body) {
        try {
            String hash = hex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
            return hmac(secret, "v1\n" + timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + hash);
        } catch (Exception e) { throw new IllegalStateException("Unable to sign verification request", e); }
    }

    static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Unable to sign registration request", e); }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) { result.append(String.format("%02x", value & 0xff)); }
        return result.toString();
    }

    private static RuntimeException unavailable() { return exception0(400, "短信验证服务暂不可用，请稍后重试"); }
}
