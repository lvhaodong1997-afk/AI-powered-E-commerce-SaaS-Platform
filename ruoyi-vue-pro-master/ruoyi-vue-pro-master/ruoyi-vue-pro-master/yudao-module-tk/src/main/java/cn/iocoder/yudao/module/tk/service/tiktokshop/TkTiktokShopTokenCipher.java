package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TkTiktokShopTokenCipher {
    private final TkTiktokShopProperties properties;
    private final SecureRandom random = new SecureRandom();
    public TkTiktokShopTokenCipher(TkTiktokShopProperties properties) { this.properties = properties; }
    public String encrypt(String token, Long tenantId, String appKey, String openId) {
        try {
            if (token == null || token.isEmpty()) throw new IllegalArgumentException();
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, tenantId, appKey, openId);
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return "v1." + Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ignored) { throw new IllegalStateException("Shop token encryption unavailable"); }
    }
    public String decrypt(String value, Long tenantId, String appKey, String openId) {
        try {
            if (value == null || !value.startsWith("v1.")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(value.substring(3));
            if (bytes.length < 29) throw new IllegalArgumentException();
            byte[] iv = java.util.Arrays.copyOf(bytes,12);
            return new String(cipher(Cipher.DECRYPT_MODE,iv,tenantId,appKey,openId).doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
        } catch (Exception ignored) { throw new IllegalStateException("Shop token decryption unavailable"); }
    }
    private Cipher cipher(int mode, byte[] iv, Long tenant, String app, String open) throws Exception {
        if (!properties.hasValidEncryptionKey() || tenant == null || tenant <= 0 || app == null || app.isEmpty() || open == null || open.isEmpty())
            throw new IllegalStateException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(Base64.getDecoder().decode(properties.getTokenEncryptionKey()),"AES"),new GCMParameterSpec(128,iv));
        // Length prefixes prevent ambiguity even if an upstream identifier contains a delimiter.
        cipher.updateAAD(("tiktok-shop:v1:"+tenant+":"+app.length()+":"+app+":"+open.length()+":"+open).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
