package cn.iocoder.yudao.module.tk.service.tiktok;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokMediaTransferTest {

    @TempDir
    Path tempDir;
    private HttpServer server;
    private ExecutorService executor;
    private Thread downloading;
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void cleanup() throws Exception {
        release.countDown();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        if (downloading != null) {
            downloading.interrupt();
            downloading.join(2_000);
        }
    }

    @Test
    void downloadWritesBeforeRemoteResponseFinishes() throws Exception {
        byte[] chunk = new byte[64 * 1024];
        Arrays.fill(chunk, (byte) 19);
        String url = server(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(chunk);
                output.flush();
                awaitRelease();
                output.write(chunk);
            } finally {
                exchange.close();
            }
        });
        Path target = tempDir.resolve("stream.mp4");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        downloading = new Thread(() -> {
            try {
                TkTiktokMediaTransfer.download(url, target, 1_000_000L);
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        downloading.setDaemon(true);
        downloading.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while ((!Files.exists(target) || Files.size(target) == 0) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }

        assertTrue(Files.exists(target) && Files.size(target) > 0, "download must not buffer the whole response");
        assertTrue(downloading.isAlive());
        release.countDown();
        downloading.join(2_000);

        assertFalse(downloading.isAlive());
        assertNull(failure.get());
        assertEquals(2L * chunk.length, Files.size(target));
    }

    @Test
    void streamedResponseWithoutLengthCannotExceedLimitAndPartialFileIsRemoved() throws Exception {
        String url = server(exchange -> respond(exchange, 0, new byte[512]));
        Path target = tempDir.resolve("too-large.mp4");

        IOException error = assertThrows(IOException.class,
                () -> transfer(2_000).downloadToFile(url, target, 127));

        assertTrue(error.getMessage().contains("byte limit"));
        assertFalse(Files.exists(target));
    }

    @Test
    void advertisedOversizeDoesNotOverwriteExistingFile() throws Exception {
        String url = server(exchange -> respond(exchange, 512, new byte[512]));
        Path target = Files.write(tempDir.resolve("existing.mp4"), new byte[]{7, 8});

        assertThrows(IOException.class, () -> transfer(2_000).downloadToFile(url, target, 127));

        assertArrayEquals(new byte[]{7, 8}, Files.readAllBytes(target));
    }

    @Test
    void downloadCallDeadlineBoundsAContinuouslyTricklingBodyAndRemovesPartialFile() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = tricklingServer(requests);
        Path target = tempDir.resolve("slow.mp4");

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(IOException.class,
                () -> transfer(200).downloadToFile(url, target, 1_000_000)));

        assertEquals(1, requests.get());
        assertFalse(Files.exists(target));
    }

    @Test
    void interruptedDownloadStopsWithoutRetryAndDeletesPartialFile() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = tricklingServer(requests);
        Path target = tempDir.resolve("interrupted.mp4");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        downloading = new Thread(() -> {
            try { transfer(5_000).downloadToFile(url, target, 1_000_000); }
            catch (Throwable ex) { failure.set(ex); }
        });
        downloading.setDaemon(true);
        downloading.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!Files.exists(target) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(Files.exists(target));

        downloading.interrupt();
        downloading.join(2_000);

        assertFalse(downloading.isAlive());
        assertNotNull(failure.get());
        assertTrue(downloading.isInterrupted());
        assertEquals(1, requests.get());
        assertFalse(Files.exists(target));
    }

    @Test
    void uploadAttemptDeadlineRetriesOnlyThroughLeaseHook() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            awaitRelease();
            exchange.close();
        });
        AtomicInteger checks = new AtomicInteger();
        Path video = Files.write(tempDir.resolve("upload.mp4"), new byte[]{1, 2, 3});
        TkTiktokApiClient client = new TkTiktokApiClient(new TkTiktokMediaTransfer(
                1_000, 5_000, 5_000, 150, 3_000, 3_000));

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IllegalStateException.class,
                () -> client.resumeUploadVideoChunks(url, video, "video/mp4", 0, checks::incrementAndGet)));

        assertEquals(3, checks.get());
        assertEquals(3, requests.get());
    }

    @Test
    void writeIdleTimeoutBoundsServerThatDoesNotReadUploadBody() throws Exception {
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
        AtomicInteger checks = new AtomicInteger();
        TkTiktokApiClient client = new TkTiktokApiClient(new TkTiktokMediaTransfer(
                1_000, 5_000, 150, 4_000, 5_000, 5_000));

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IllegalStateException.class,
                () -> client.resumeUploadVideoChunks(url, video, "video/mp4", 0, checks::incrementAndGet)));

        assertEquals(3, checks.get());
        assertEquals(3, requests.get());
    }

    @Test
    void oauthFormPreservesExactContentTypeWithoutAddingCharset() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        String form = "grant_type=refresh_token&refresh_token=rft.test%2Bvalue%2F%3D";
        String url = server(exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            respond(exchange, 2, new byte[]{'{', '}'});
        });

        TkTiktokMediaTransfer.ApiResponse response = transfer(2_000).post(url, null,
                "application/x-www-form-urlencoded", form);

        assertEquals("application/x-www-form-urlencoded", contentType.get());
        assertEquals(form, receivedBody.get());
        assertEquals(200, response.status);
    }

    @Test
    void jsonApiCallDeadlineBoundsTricklingBodyWithoutReplayingPost() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = tricklingServer(requests);
        TkTiktokMediaTransfer transfer = new TkTiktokMediaTransfer(1_000, 5_000, 5_000,
                5_000, 5_000, 5_000, 200);

        IOException error = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(IOException.class,
                () -> transfer.post(url, "Bearer private-token", "application/json", "{}")));

        assertEquals(1, requests.get());
        assertFalse(error.toString().contains("private-token"));
        assertFalse(error.toString().contains("never-log-this"));
    }

    @Test
    void initPostRedirectDoesNotReplayMutation() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            exchange.getResponseHeaders().add("Location", "/init-again");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });

        TkTiktokMediaTransfer.ApiResponse response = transfer(2_000).post(url,
                "Bearer private-token", "application/json", "{}");

        assertEquals(307, response.status);
        assertEquals(1, requests.get());
    }

    @Test
    void forbiddenPutPreservesExpiredUrlStatusWithoutRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        String url = server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        Path video = Files.write(tempDir.resolve("expired.mp4"), new byte[]{1});

        TkTiktokApiClient.UploadException error = assertThrows(TkTiktokApiClient.UploadException.class,
                () -> new TkTiktokApiClient().uploadVideoChunks(url, video, "video/mp4"));

        assertTrue(error.isUploadUrlExpired());
        assertEquals(403, error.getStatus());
        assertEquals(1, requests.get());
    }

    private TkTiktokMediaTransfer transfer(long downloadMillis) {
        return new TkTiktokMediaTransfer(1_000, 5_000, 5_000, 5_000, 5_000, downloadMillis);
    }

    private String tricklingServer(AtomicInteger requests) throws Exception {
        return server(exchange -> {
            requests.incrementAndGet();
            drain(exchange.getRequestBody());
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                while (release.getCount() > 0) {
                    output.write(new byte[]{1, 2, 3});
                    output.flush();
                    try { Thread.sleep(25); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
                }
            } catch (IOException ignored) {
                // Expected when the caller cancels a timed-out stream.
            } finally {
                exchange.close();
            }
        });
    }

    private String server(HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "transfer-test-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/media?upload_token=never-log-this";
    }

    private void respond(HttpExchange exchange, long length, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(200, length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    private void awaitRelease() {
        try { release.await(10, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    private void drain(InputStream input) throws IOException {
        try (InputStream body = input) {
            byte[] buffer = new byte[8192];
            while (body.read(buffer) != -1) { }
        }
    }
}
