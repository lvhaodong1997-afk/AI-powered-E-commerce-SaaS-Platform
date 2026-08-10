package cn.iocoder.yudao.module.tk.service.reference;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkSafeRemoteUrlValidatorTest {

    @Test
    void rejectsLoopbackAddress() {
        TkSafeRemoteUrlValidator validator = new TkSafeRemoteUrlValidator(Set.of("tiktok.com"), true);

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate("http://127.0.0.1/video.mp4"));
    }

    @Test
    void acceptsConfiguredBusinessHost() {
        TkSafeRemoteUrlValidator validator = new TkSafeRemoteUrlValidator(Set.of("tiktok.com"), true);

        assertDoesNotThrow(() -> validator.validate("https://www.tiktok.com/@demo/video/123"));
    }
}
