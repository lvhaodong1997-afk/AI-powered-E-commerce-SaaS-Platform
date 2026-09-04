package cn.iocoder.yudao.module.tk.framework.openapi;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

public final class TkOpenApiSigner {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TkOpenApiSigner() {
    }

    public static String canonicalRequest(String method, String requestTarget, String timestamp,
                                          String nonce, byte[] body) {
        return method.toUpperCase(Locale.ROOT) + "\n"
                + requestTarget + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256Hex(body == null ? new byte[0] : body);
    }

    public static String hmacBase64(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Open API HMAC calculation failed", ex);
        }
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes == null ? new byte[0] : bytes);
            char[] result = new char[digest.length * 2];
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                result[index * 2] = HEX[value >>> 4];
                result[index * 2 + 1] = HEX[value & 0x0f];
            }
            return new String(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Open API SHA-256 calculation failed", ex);
        }
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

}
