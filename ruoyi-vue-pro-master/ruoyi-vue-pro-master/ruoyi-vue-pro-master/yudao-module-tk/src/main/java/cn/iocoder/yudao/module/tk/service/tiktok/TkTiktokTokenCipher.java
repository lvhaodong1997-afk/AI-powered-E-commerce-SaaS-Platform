package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class TkTiktokTokenCipher {

    private static final String PROVIDER = "TIKTOK";
    private static final String FALLBACK_PREFIX = "b64:";
    private static final String AES_PREFIX = "aesgcm:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    @Resource
    private TkApiKeyConfigService configService;

    public String encrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String secret = configService.getValue(PROVIDER, "token-secret");
        if (StrUtil.isBlank(secret)) {
            return FALLBACK_PREFIX + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey(secret), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return AES_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("TikTok token 加密失败", ex);
        }
    }

    public String decrypt(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        if (value.startsWith(FALLBACK_PREFIX)) {
            return new String(Base64.getDecoder().decode(value.substring(FALLBACK_PREFIX.length())), StandardCharsets.UTF_8);
        }
        if (!value.startsWith(AES_PREFIX)) {
            return value;
        }
        String secret = configService.getValue(PROVIDER, "token-secret");
        if (StrUtil.isBlank(secret)) {
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(AES_PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey(secret), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("TikTok token 解密失败", ex);
        }
    }

    private byte[] secretKey(String secret) throws Exception {
        return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), 16);
    }

}
