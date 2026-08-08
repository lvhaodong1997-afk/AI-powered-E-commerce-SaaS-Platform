package cn.iocoder.yudao.module.tk.service.upload;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

class TkOssPostPolicySigner {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final DateTimeFormatter OSS_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private TkOssPostPolicySigner() {
    }

    static Policy sign(String accessKeyId, String accessKeySecret, String keyPrefix, Long maxFileSize,
                       Integer expireSeconds, Clock clock) {
        long maxSize = maxFileSize == null || maxFileSize <= 0 ? 100L * 1024 * 1024 : maxFileSize;
        int seconds = expireSeconds == null || expireSeconds <= 0 ? 1800 : expireSeconds;
        Instant expiration = Instant.now(clock).plus(seconds, ChronoUnit.SECONDS);
        String expirationText = OSS_EXPIRATION_FORMATTER.format(expiration.truncatedTo(ChronoUnit.MILLIS));
        String policyJson = "{\"expiration\":\"" + expirationText + "\",\"conditions\":["
                + "[\"starts-with\",\"$key\",\"" + escapeJson(keyPrefix) + "\"],"
                + "[\"content-length-range\",1," + maxSize + "]]}";
        String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        return new Policy(accessKeyId, policy, signature(policy, accessKeySecret), expirationText);
    }

    private static String signature(String policy, String accessKeySecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            return Base64.getEncoder().encodeToString(mac.doFinal(policy.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成 OSS 上传签名失败：" + ex.getMessage(), ex);
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Getter
    @AllArgsConstructor
    static class Policy {
        private final String accessKeyId;
        private final String policy;
        private final String signature;
        private final String expiration;
    }
}
