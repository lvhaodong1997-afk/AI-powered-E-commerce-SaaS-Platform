package cn.iocoder.yudao.module.tk.service.tiktok;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokApiClientTransferTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private Thread uploading;
    private final CountDownLatch releaseResponse = new CountDownLatch(1);

    @AfterEach
    void cleanup() throws Exception {
        releaseResponse.countDown();
        if (server != null) server.stop(0);
        if (uploading != null) {
            uploading.interrupt();
            uploading.join(2_000);
        }
        System.clearProperty("tk.tiktok.media-transfer.upload-session-timeout-millis");
    }

    @Test
    void alreadyInterruptedUploadDoesNotSendOrCheckLease() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        Path video = video();
        AtomicInteger checks = new AtomicInteger();
        try {
            Thread.currentThread().interrupt();
            assertThrows(IllegalStateException.class, () -> new TkTiktokApiClient()
                    .resumeUploadVideoChunks(url, video, "video/mp4", 0L, checks::incrementAndGet));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(0, requests.get());
        assertEquals(0, checks.get());
    }

    @Test
    void interruptCancelsInFlightResponseWaitWithoutRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch received = new CountDownLatch(1);
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            received.countDown();
            awaitRelease();
            exchange.close();
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Path video = video();
        uploading = new Thread(() -> {
            try {
                new TkTiktokApiClient().uploadVideoChunks(url, video, "video/mp4");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        uploading.setDaemon(true);
        uploading.start();
        assertTrue(received.await(2, TimeUnit.SECONDS));

        uploading.interrupt();
        uploading.join(2_000);

        assertFalse(uploading.isAlive(), "interrupt must cancel the active socket wait");
        assertNotNull(failure.get());
        assertTrue(uploading.isInterrupted());
        assertEquals(1, requests.get());
    }

    @Test
    void sessionDeadlineBoundsResponseWaitAndPreventsRetry() throws Exception {
        System.setProperty("tk.tiktok.media-transfer.upload-session-timeout-millis", "250");
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            awaitRelease();
            exchange.close();
        });
        Path video = video();

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IllegalStateException.class,
                () -> new TkTiktokApiClient().uploadVideoChunks(url, video, "video/mp4")));

        assertEquals(1, requests.get());
    }

    @Test
    void sessionDeadlineAlsoBoundsServerThatNeverReadsRequestBody() throws Exception {
        System.setProperty("tk.tiktok.media-transfer.upload-session-timeout-millis", "250");
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            awaitRelease();
            exchange.close();
        });
        Path video = tempDir.resolve("large.mp4");
        try (RandomAccessFile sparse = new RandomAccessFile(video.toFile(), "rw")) {
            sparse.setLength(64_000_000L);
        }

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IllegalStateException.class,
                () -> new TkTiktokApiClient().uploadVideoChunks(url, video, "video/mp4")));

        assertEquals(1, requests.get());
    }

    @Test
    void uploadRedirectIsReturnedWithoutFollowingOrReplayingPut() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            exchange.getResponseHeaders().add("Location", "/unexpected");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        Path video = video();

        TkTiktokApiClient.UploadException error = assertThrows(TkTiktokApiClient.UploadException.class,
                () -> new TkTiktokApiClient().uploadVideoChunks(url, video, "video/mp4"));

        assertEquals(307, error.getStatus());
        assertEquals(1, requests.get());
    }

    private String server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/upload?upload_token=never-log-this";
    }

    private Path video() throws Exception {
        return Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});
    }

    private void awaitRelease() {
        try {
            releaseResponse.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void drain(InputStream input) throws java.io.IOException {
        try (InputStream body = input) {
            byte[] buffer = new byte[8192];
            while (body.read(buffer) != -1) { }
        }
    }
}
