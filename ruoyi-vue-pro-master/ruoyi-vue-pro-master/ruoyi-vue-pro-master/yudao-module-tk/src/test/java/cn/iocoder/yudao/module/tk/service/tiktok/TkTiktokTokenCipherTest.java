package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkTiktokTokenCipherTest {

    @Test
    void encryptRejectsNonBlankTokenWhenSecretMissing() {
        TkTiktokTokenCipher cipher = createCipher(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cipher.encrypt("access-token"));

        assertTrue(error.getMessage().contains("token-secret"));
    }

    @Test
    void aesGcmRoundTripsWhenSecretConfigured() {
        TkTiktokTokenCipher cipher = createCipher("test-token-secret");

        String encrypted = cipher.encrypt("access-token");

        assertTrue(encrypted.startsWith("aesgcm:"));
        assertNotEquals("access-token", encrypted);
        assertEquals("access-token", cipher.decrypt(encrypted));
    }

    @Test
    void decryptReadsLegacyBase64WithoutSecret() {
        TkTiktokTokenCipher cipher = createCipher(null);

        assertEquals("legacy-token", cipher.decrypt("b64:bGVnYWN5LXRva2Vu"));
    }

    @Test
    void decryptRejectsAesGcmTokenWhenSecretMissing() {
        String encrypted = createCipher("test-token-secret").encrypt("access-token");
        TkTiktokTokenCipher cipher = createCipher(null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(encrypted));

        assertTrue(error.getMessage().contains("token-secret"));
    }

    @Test
    void decryptRejectsPlaintextToken() {
        TkTiktokTokenCipher cipher = createCipher("test-token-secret");

        assertThrows(IllegalStateException.class, () -> cipher.decrypt("plaintext-token"));
    }

    @Test
    void decryptRejectsUnknownPrefix() {
        TkTiktokTokenCipher cipher = createCipher("test-token-secret");

        assertThrows(IllegalStateException.class, () -> cipher.decrypt("unknown:token"));
    }

    private static TkTiktokTokenCipher createCipher(String secret) {
        TkApiKeyConfigService configService = mock(TkApiKeyConfigService.class);
        when(configService.getValue("TIKTOK", "token-secret")).thenReturn(secret);
        TkTiktokTokenCipher cipher = new TkTiktokTokenCipher();
        ReflectionTestUtils.setField(cipher, "configService", configService);
        return cipher;
    }

}
