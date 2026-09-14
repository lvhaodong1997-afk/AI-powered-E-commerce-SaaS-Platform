package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkMiniMaxTtsClientTest {
    private HttpServer server;
    private TkMiniMaxTtsClient client;
    private TkGenerationProperties properties;
    private TkMiniMaxVoiceDictionaryService catalog;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> token = new AtomicReference<>();
    private final AtomicReference<String> payload = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private static final byte[] MP3 = new byte[] {'I', 'D', '3', 4, 0, 0, 0, 0, 0, 0};

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tts/minimax/generate", exchange -> {
            calls.incrementAndGet();
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            payload.set(new String(TkMiniMaxTtsClient.readLimited(exchange.getRequestBody(), 4096), StandardCharsets.UTF_8));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        properties = new TkGenerationProperties();
        properties.getMinimax().setWorkerUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getMinimax().setInternalToken("local-test-token");
        catalog = mock(TkMiniMaxVoiceDictionaryService.class);
        when(catalog.buildTtsRequest("原文", "voice-A")).thenReturn(Collections.singletonMap("text", "原文"));
        client = spy(new TkMiniMaxTtsClient());
        ReflectionTestUtils.setField(client, "generationProperties", properties);
        ReflectionTestUtils.setField(client, "voiceDictionaryService", catalog);
        response.set("{\"base_resp\":{\"status_code\":0},\"output\":{\"audio\":{\"url\":\"https://audio.example/result.mp3\"}}}");
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void sendsAuthenticatedWorkerRequestAndReturnsDownloadedBytes() {
        doReturn(MP3).when(client).downloadAudio("https://audio.example/result.mp3");
        assertArrayEquals(MP3, client.synthesize(request()));
        assertEquals("local-test-token", token.get());
        assertEquals("{\"text\":\"原文\"}", payload.get());
        assertEquals(1, calls.get());
        assertEquals("mp3", client.audioFormat());
        verify(catalog).buildTtsRequest("原文", "voice-A");
    }

    @Test
    void canCallReferenceWorkerWithoutOptionalInternalToken() {
        properties.getMinimax().setInternalToken(" ");
        doReturn(MP3).when(client).downloadAudio("https://audio.example/result.mp3");
        assertArrayEquals(MP3, client.synthesize(request()));
        assertNull(token.get());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectsBusinessFailureWithoutLeakingWorkerResponseOrRetrying() {
        response.set("{\"base_resp\":{\"status_code\":1004,\"status_msg\":\"secret-upstream\"}}");
        Exception ex = assertThrows(IllegalStateException.class, () -> client.synthesize(request()));
        assertFalse(ex.toString().contains("secret-upstream"));
        assertEquals(1, calls.get());
        verify(client, never()).downloadAudio(anyString());
    }

    @Test
    void malformedWorkerJsonDoesNotLogItsSensitiveBody() {
        Logger logger = (Logger) LoggerFactory.getLogger(JsonUtils.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            response.set("{signed-url-secret invalid json");
            assertThrows(IllegalStateException.class, () -> client.synthesize(request()));
            assertTrue(events.list.stream().noneMatch(event -> event.getFormattedMessage().contains("signed-url-secret")));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void rejectsHexMissingStatusAndInvalidUrls() {
        for (String invalid : new String[] {
                "{\"audio_url\":\"https://audio.example/a.mp3\"}",
                "{\"base_resp\":{\"status_code\":\"0\"},\"audio_url\":\"https://audio.example/a.mp3\"}",
                "{\"base_resp\":{\"status_code\":0},\"audio_url\":\"deadbeef\"}",
                "{\"base_resp\":{\"status_code\":0},\"audio_url\":\"http://audio.example/a.mp3\"}",
                "{\"base_resp\":{\"status_code\":0},\"audio_url\":\"https://user:password@audio.example/a.mp3\"}"}) {
            response.set(invalid);
            assertThrows(IllegalStateException.class, () -> client.synthesize(request()));
        }
        verify(client, never()).downloadAudio(anyString());
    }

    @Test
    void downloadsPublicMp3WithoutWorkerTokenAndRejectsRedirectsAndOversize() throws Exception {
        URI uri = URI.create("https://93.184.216.34/audio.mp3");
        HttpURLConnection download = mock(HttpURLConnection.class);
        doReturn(download).when(client).openConnection(uri);
        when(download.getResponseCode()).thenReturn(200);
        when(download.getContentLengthLong()).thenReturn((long) MP3.length);
        when(download.getInputStream()).thenReturn(new ByteArrayInputStream(MP3));
        assertArrayEquals(MP3, client.downloadAudio(uri.toString()));
        verify(download, never()).setRequestProperty(anyString(), anyString());
        verify(download).setInstanceFollowRedirects(false);
        when(download.getResponseCode()).thenReturn(302);
        assertThrows(IllegalStateException.class, () -> client.downloadAudio(uri.toString()));
        when(download.getResponseCode()).thenReturn(200);
        properties.getMinimax().setMaxDownloadBytes(9L);
        assertThrows(IllegalStateException.class, () -> client.downloadAudio(uri.toString()));
    }

    @Test
    void downloadRejectsPrivateAddressAndInvalidAudioAndBoundsResponse() throws Exception {
        assertThrows(IllegalStateException.class, () -> client.downloadAudio("https://127.0.0.1/a.mp3"));
        assertThrows(IllegalStateException.class, () -> TkMiniMaxTtsClient.requireMp3("<html>error</html>".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class, () -> TkMiniMaxTtsClient.requireMp3(new byte[0]));
        assertThrows(IllegalStateException.class, () -> TkMiniMaxTtsClient.readLimited(new ByteArrayInputStream(MP3), 9));
        assertArrayEquals(MP3, TkMiniMaxTtsClient.readLimited(new ByteArrayInputStream(MP3), 10));
        TkMiniMaxTtsClient.requireMp3(MP3);
    }

    private TkVoiceSynthesisRequest request() {
        return TkVoiceSynthesisRequest.builder().text("原文").voiceCode("voice-A").finalSynthesis(true).build();
    }
}
