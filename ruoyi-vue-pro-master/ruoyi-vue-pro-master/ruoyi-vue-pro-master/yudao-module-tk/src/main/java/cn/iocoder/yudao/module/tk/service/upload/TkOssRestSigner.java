package cn.iocoder.yudao.module.tk.service.upload;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class TkOssRestSigner {

    private static final String HMAC_SHA1 = "HmacSHA1";

    private TkOssRestSigner() {
    }

    static String sign(String method, String contentMd5, String contentType, String date,
                       String canonicalizedResource, String accessKeySecret) {
        String canonical = method + "\n"
                + blankToEmpty(contentMd5) + "\n"
                + blankToEmpty(contentType) + "\n"
                + date + "\n"
                + canonicalizedResource;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成 OSS 请求签名失败：" + ex.getMessage(), ex);
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
