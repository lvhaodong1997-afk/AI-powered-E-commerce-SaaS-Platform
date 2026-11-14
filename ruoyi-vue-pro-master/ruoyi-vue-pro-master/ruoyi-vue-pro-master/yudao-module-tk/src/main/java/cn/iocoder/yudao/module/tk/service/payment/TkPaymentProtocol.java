package cn.iocoder.yudao.module.tk.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Shared wire contract. Sign the original UTF-8 bytes, never reserialized JSON. */
public final class TkPaymentProtocol {
    private static final Set<String> STATES = new HashSet<>(Arrays.asList(
            "quoted", "payment_pending", "paid", "canceled", "refund_pending", "refunded", "review"));
    private static final String[] IMMUTABLE = {"orderNo", "userId", "tenantId", "packageCode", "quantity",
            "catalogVersion", "baseAmountCents", "points", "amountCents", "currency", "livemode", "paymentMethod"};
    private TkPaymentProtocol() { }

    public static String sign(String secret, String timestamp, String nonce, String method, String path, String body) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("支付签名未配置");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            String canonical = "v1\n" + timestamp + "\n" + nonce + "\n" + method + "\n" + path + "\n" + hex(digest);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("支付签名不可用", e); }
    }

    public static void verify(String secret, String timestamp, String nonce, String signature,
                              String method, String path, String body, long nowSeconds) {
        if (timestamp == null || !timestamp.matches("[0-9]{10}") || nonce == null || !nonce.matches("[a-f0-9]{32}")
                || signature == null || !signature.matches("[a-f0-9]{64}")
                || Math.abs(nowSeconds - Long.parseLong(timestamp)) > 300) {
            throw new IllegalArgumentException("支付通知签名无效");
        }
        String expected = sign(secret, timestamp, nonce, method, path, body);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("支付通知签名无效");
    }

    public static void validateSnapshot(JsonNode expected, JsonNode actual) {
        if (actual == null || !actual.isObject()) throw new IllegalArgumentException("支付结果格式无效");
        for (String name : IMMUTABLE) {
            JsonNode left = expected.get(name), right = actual.get(name);
            boolean equal = left != null && right != null && !left.isNull() && !right.isNull();
            if (equal && left.isIntegralNumber()) equal = right.isIntegralNumber() && left.bigIntegerValue().equals(right.bigIntegerValue());
            else if (equal) equal = left.equals(right);
            if (!equal) throw new IllegalArgumentException("支付结果与订单不匹配：" + name);
        }
        if(!java.util.Objects.equals(expected.get("fx"),actual.get("fx")))throw new IllegalArgumentException("支付结果汇率发生变化");
    }

    public static boolean isAllowedTransition(String current, String next) {
        if (!STATES.contains(current) || !STATES.contains(next)) return false;
        if (current.equals(next)) return true;
        switch (current) {
            case "refunded": return "review".equals(next);
            case "canceled": return "review".equals(next);
            case "refund_pending": return "refunded".equals(next) || "review".equals(next);
            case "paid": return "refund_pending".equals(next) || "refunded".equals(next) || "review".equals(next);
            case "review": return "refunded".equals(next) || "canceled".equals(next);
            case "payment_pending": return !"quoted".equals(next);
            default: return true;
        }
    }

    public static String orderNo(String value) {
        if (value == null || !value.matches("TKP_[a-f0-9]{32}")) throw new IllegalArgumentException("订单号无效");
        return value;
    }

    private static String hex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte b : value) result.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        return result.toString();
    }
}
