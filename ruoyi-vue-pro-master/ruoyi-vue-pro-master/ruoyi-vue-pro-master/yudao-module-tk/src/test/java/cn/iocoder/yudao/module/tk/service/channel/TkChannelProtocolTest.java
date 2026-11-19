package cn.iocoder.yudao.module.tk.service.channel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TkChannelProtocolTest {
    @Test void parsesOnlyContiguousAcknowledgements() {
        assertEquals(0, TkChannelProtocol.nextByte(null, 1024));
        assertEquals(512, TkChannelProtocol.nextByte("bytes=0-511", 1024));
        assertThrows(IllegalArgumentException.class, () -> TkChannelProtocol.nextByte("bytes=1-511", 1024));
        assertThrows(IllegalArgumentException.class, () -> TkChannelProtocol.nextByte("bytes=0-1023", 1024));
    }
    @Test void acceptsOnlyTheExpectedGoogleUploadEndpoint() {
        assertDoesNotThrow(() -> TkChannelProtocol.uploadUri("https://www.googleapis.com/upload/youtube/v3/videos?upload_id=test"));
        assertThrows(IllegalArgumentException.class, () -> TkChannelProtocol.uploadUri("https://www.googleapis.com.evil.invalid/upload/youtube/v3/videos"));
        assertThrows(IllegalArgumentException.class, () -> TkChannelProtocol.uploadUri("https://www.googleapis.com:444/upload/youtube/v3/videos"));
    }
    @Test void treatsUnknownFinalWritesAsUnsafeToRepeat() {
        assertEquals("UNKNOWN", TkChannelProtocol.failureState(503, true));
        assertEquals("PENDING", TkChannelProtocol.failureState(503, false));
        assertEquals("PENDING", TkChannelProtocol.failureState(429, true));
        assertEquals("REAUTH_REQUIRED", TkChannelProtocol.failureState(401, true));
    }
}
