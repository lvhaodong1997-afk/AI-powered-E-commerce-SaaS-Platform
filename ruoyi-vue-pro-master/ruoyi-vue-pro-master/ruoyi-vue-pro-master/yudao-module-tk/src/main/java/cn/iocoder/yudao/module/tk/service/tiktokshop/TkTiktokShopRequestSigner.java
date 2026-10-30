package cn.iocoder.yudao.module.tk.service.tiktokshop;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.TreeMap;

public final class TkTiktokShopRequestSigner {
    private TkTiktokShopRequestSigner() {}

    /** Query keys and values are decoded; encodedPath and body are exactly as sent. */
    public static String sign(String encodedPath, Map<String, String> query, byte[] exactBody,
                              String contentType, String appSecret) {
        if (appSecret == null || appSecret.trim().isEmpty()) throw new IllegalArgumentException("App secret is required");
        if (encodedPath == null || !encodedPath.startsWith("/") || encodedPath.startsWith("//")
                || encodedPath.contains("?") || encodedPath.contains("#")) {
            throw new IllegalArgumentException("An encoded request path without query or fragment is required");
        }
        StringBuilder prefix = new StringBuilder(appSecret).append(encodedPath);
        if (query != null) {
            new TreeMap<>(query).forEach((key, value) -> {
                if (!"sign".equals(key) && !"access_token".equals(key)) {
                    prefix.append(key).append(value == null ? "" : value);
                }
            });
        }
        try {
            byte[] secret = appSecret.getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(prefix.toString().getBytes(StandardCharsets.UTF_8));
            String mediaType = contentType == null ? "" : contentType.split(";", 2)[0].trim();
            if (!"multipart/form-data".equalsIgnoreCase(mediaType) && exactBody != null) mac.update(exactBody);
            byte[] digest = mac.doFinal(secret);
            char[] hex = new char[digest.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = alphabet[(digest[i] & 0xff) >>> 4];
                hex[i * 2 + 1] = alphabet[digest[i] & 0xf];
            }
            return new String(hex);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to generate TikTok Shop signature", exception);
        }
    }
}
