package cn.iocoder.yudao.module.tk.service.social.auth;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TkSocialTokenCipher {
    private final TkSocialProperties properties;
    private final SecureRandom random = new SecureRandom();
    public TkSocialTokenCipher(TkSocialProperties properties) { this.properties = properties; }

    public String encrypt(String value, String context) {
        try {
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + ciphertext.length)
                    .put(nonce).put(ciphertext).array());
        } catch (Exception e) { throw new IllegalStateException("Meta 凭据加密失败"); }
    }

    public String decrypt(String value, String context) {
        try {
            if (value == null || !value.startsWith("v1:")) throw new IllegalArgumentException();
            ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(value.substring(3)));
            byte[] nonce = new byte[12]; bytes.get(nonce);
            byte[] data = new byte[bytes.remaining()]; bytes.get(data);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Meta 凭据不可用，请重新授权"); }
    }

    public void validateKey() { key(); }
    private SecretKeySpec key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getEncryptionKey());
            if (bytes.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(bytes, "AES");
        } catch (Exception e) { throw new IllegalStateException("Meta encryption-key 必须为 Base64 编码的 32 字节密钥"); }
    }
}
