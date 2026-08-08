package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner.Policy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOssPostPolicySignerTest {

    @Test
    void signCreatesShortLivedPolicyWithoutExposingSecret() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-07T08:00:00Z"), ZoneOffset.UTC);

        Policy policy = TkOssPostPolicySigner.sign(
                "access-key-id",
                "secret-key-value",
                "tk/166/166/material-videos/20260707/",
                104857600L,
                1800,
                clock);

        String decodedPolicy = new String(Base64.getDecoder().decode(policy.getPolicy()), StandardCharsets.UTF_8);
        assertTrue(decodedPolicy.contains("\"expiration\":\"2026-07-07T08:30:00.000Z\""));
        assertTrue(decodedPolicy.contains("[\"starts-with\",\"$key\",\"tk/166/166/material-videos/20260707/\"]"));
        assertTrue(decodedPolicy.contains("[\"content-length-range\",1,104857600]"));
        assertTrue(policy.getSignature().length() > 20);
        assertFalse(policy.getPolicy().contains("secret-key-value"));
        assertFalse(policy.getSignature().contains("secret-key-value"));
        assertNotEquals(policy.getPolicy(), policy.getSignature());
    }
}
