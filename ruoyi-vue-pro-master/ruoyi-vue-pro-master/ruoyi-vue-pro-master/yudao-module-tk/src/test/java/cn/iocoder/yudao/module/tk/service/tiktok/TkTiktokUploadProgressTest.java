package cn.iocoder.yudao.module.tk.service.tiktok;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongPredicate;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokUploadProgressTest {
    @TempDir Path temp;
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void lostAcknowledgementWithoutProgressDoesNotReplayAcceptedBytes() throws Exception {
        AtomicLong accepted = new AtomicLong();
        List<String> ranges = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> {
            long bytes = 0;
            byte[] buffer = new byte[1024];
            int n;
            while ((n = exchange.getRequestBody().read(buffer)) != -1) bytes += n;
            ranges.add(exchange.getRequestHeaders().getFirst("Content-Range"));
            if (!accepted.compareAndSet(0, bytes)) exchange.sendResponseHeaders(416, -1);
            exchange.close();
        });
        server.start();
        Path video = Files.write(temp.resolve("video.mp4"), new byte[]{1, 2, 3, 4});

        assertThrows(IllegalStateException.class, () -> new TkTiktokApiClient().uploadVideoChunks(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/upload", video, "video/mp4"));

        assertEquals(4, accepted.get());
        assertEquals(1, ranges.size(), "An uncertain acknowledgement needs progress reconciliation before another PUT");
    }

    @Test void lostFinalAcknowledgementCompletesFromOfficialProgressWithoutAnotherPut() throws Exception {
        AtomicLong accepted = new AtomicLong();
        String url = progressServer(accepted, offset -> true);
        AtomicInteger queries = new AtomicInteger();

        upload(url, smallVideo(), 0, () -> { }, deadline -> { queries.incrementAndGet(); return accepted.get(); });

        assertEquals(Collections.singletonList("bytes 0-3/4"), requests);
        assertEquals(1, queries.get());
    }

    @ParameterizedTest @ValueSource(ints = {500, 502, 504})
    void serverErrorAfterAcceptingBytesQueriesProgressBeforeAnotherPut(int status) throws Exception {
        AtomicLong accepted = new AtomicLong();
        String url = progressServer(accepted, offset -> true, status);

        upload(url, smallVideo(), 0, () -> { }, deadline -> accepted.get());

        assertEquals(Collections.singletonList("bytes 0-3/4"), requests);
    }

    @Test void lostMiddleAcknowledgementContinuesAtNextChunkWithCorrectFileBytes() throws Exception {
        AtomicLong accepted = new AtomicLong();
        String url = progressServer(accepted, offset -> offset == 32_000_000L);

        upload(url, chunkedVideo(), 0, () -> { }, deadline -> accepted.get());

        assertEquals(Arrays.asList("bytes 0-31999999/100000000", "bytes 32000000-63999999/100000000",
                "bytes 64000000-99999999/100000000"), requests);
        assertEquals(100_000_000L, accepted.get());
    }

    @Test void rangeConflictSkipsToOfficialProgressInsteadOfReplayingOldRange() throws Exception {
        AtomicLong accepted = new AtomicLong(32_000_000L);
        String url = progressServer(accepted, offset -> false);

        upload(url, chunkedVideo(), 0, () -> { }, deadline -> accepted.get());

        assertEquals(Arrays.asList("bytes 0-31999999/100000000", "bytes 32000000-63999999/100000000",
                "bytes 64000000-99999999/100000000"), requests);
        assertEquals(100_000_000L, accepted.get());
    }

    @Test void rangeConflictCanSkipSeveralConfirmedChunks() throws Exception {
        AtomicLong accepted = new AtomicLong(64_000_000L);
        String url = progressServer(accepted, offset -> false);

        upload(url, chunkedVideo(), 0, () -> { }, deadline -> accepted.get());

        assertEquals(Arrays.asList("bytes 0-31999999/100000000", "bytes 64000000-99999999/100000000"), requests);
        assertEquals(100_000_000L, accepted.get());
    }

    @ParameterizedTest @ValueSource(longs = {-1, 1, 5})
    void invalidProgressDoesNotSendAnotherPut(long progress) throws Exception {
        String url = rejectServer();

        assertThrows(IllegalStateException.class, () -> upload(url, smallVideo(), 0, () -> { }, deadline -> progress));

        assertEquals(1, requests.size());
    }

    @Test void backwardProgressDoesNotReplayEarlierChunks() throws Exception {
        String url = rejectServer();

        assertThrows(IllegalStateException.class, () -> upload(url, chunkedVideo(), 32_000_000L, () -> { }, deadline -> 0L));

        assertEquals(Collections.singletonList("bytes 32000000-63999999/100000000"), requests);
    }

    @Test void missingProgressDefersToRecovery() throws Exception {
        String url = rejectServer();

        assertThrows(IllegalStateException.class, () -> upload(url, smallVideo(), 0, () -> { }, deadline -> null));

        assertEquals(1, requests.size());
    }

    @Test void progressQueryFailureDoesNotRetryThePut() throws Exception {
        AtomicLong accepted = new AtomicLong();
        String url = progressServer(accepted, offset -> true);
        IllegalStateException failure = new IllegalStateException("status unavailable");

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> upload(url, smallVideo(), 0, () -> { }, deadline -> { throw failure; })));

        assertEquals(1, requests.size());
    }

    @Test void unchangedProgressHasBoundedRetries() throws Exception {
        String url = rejectServer();
        AtomicInteger queries = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> upload(url, smallVideo(), 0, () -> { },
                deadline -> { queries.incrementAndGet(); return 0L; }));

        assertEquals(3, queries.get());
        assertEquals(3, requests.size());
    }

    @Test void leaseLossAfterProgressQueryStopsTheNextWrite() throws Exception {
        String url = rejectServer();
        AtomicInteger checks = new AtomicInteger();
        IllegalStateException fenced = new IllegalStateException("lease lost");

        assertSame(fenced, assertThrows(IllegalStateException.class, () -> upload(url, smallVideo(), 0,
                () -> { if (checks.incrementAndGet() == 2) throw fenced; }, deadline -> 0L)));

        assertEquals(1, requests.size());
    }

    @Test void healthyUploadDoesNotQueryProgress() throws Exception {
        AtomicLong accepted = new AtomicLong();
        String url = progressServer(accepted, offset -> false);

        upload(url, smallVideo(), 0, () -> { }, deadline -> { fail("Healthy upload must not query progress"); return null; });

        assertEquals(4, accepted.get());
    }

    @Test void stalledProgressQueryUsesRemainingUploadDeadlineAndDoesNotReplay() throws Exception {
        String url = rejectServer();
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        server.createContext("/status", exchange -> {
            queries.incrementAndGet();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        String statusUrl = url.replace("/upload", "/status");
        TkTiktokMediaTransfer transport = new TkTiktokMediaTransfer(1000, 5000, 5000, 5000, 700, 5000);
        TkTiktokMediaTransfer transfer = spy(transport);
        doAnswer(call -> transport.post(statusUrl, call.getArgument(1), call.getArgument(2),
                call.getArgument(3), call.getArgument(4)))
                .when(transfer).post(anyString(), anyString(), anyString(), anyString(), anyLong());
        TkTiktokApiClient client = new TkTiktokApiClient(transfer);
        long started = System.nanoTime();
        try {
            assertThrows(IllegalStateException.class, () -> client.resumeUploadVideoChunks(url, smallVideo(),
                    "video/mp4", 0, () -> { },
                    deadline -> client.fetchPostStatus("token", "existing-publish-id", deadline).getUploadedBytes()));
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2500,
                    "A stalled status query must stop at the upload deadline, without query retry delays");
            assertEquals(1, queries.get());
            assertEquals(1, requests.size());
        } finally {
            release.countDown();
        }
    }

    private void upload(String url, Path video, long offset, Runnable owner, LongFunction<Long> progress) {
        new cn.iocoder.yudao.module.tk.service.open.platform.TkOpenTiktokPlatformAdapter(new TkTiktokApiClient())
                .resumeUploadVideo(url, video, "video/mp4", offset, owner, progress);
    }

    private Path smallVideo() throws Exception {
        return Files.write(temp.resolve("small.mp4"), new byte[]{1, 2, 3, 4});
    }

    private Path chunkedVideo() throws Exception {
        Path video = temp.resolve("chunked.mp4");
        try (RandomAccessFile file = new RandomAccessFile(video.toFile(), "rw")) {
            file.setLength(100_000_000L);
            for (long offset : new long[]{0, 32_000_000L, 64_000_000L}) {
                file.seek(offset);
                file.write((int) (offset / 32_000_000L) + 1);
            }
        }
        return video;
    }

    private String rejectServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> {
            byte[] buffer = new byte[8192];
            while (exchange.getRequestBody().read(buffer) != -1) { }
            requests.add(exchange.getRequestHeaders().getFirst("Content-Range"));
            exchange.sendResponseHeaders(416, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
    }

    private String progressServer(AtomicLong accepted, LongPredicate loseAcknowledgement) throws Exception {
        return progressServer(accepted, loseAcknowledgement, 0);
    }

    private String progressServer(AtomicLong accepted, LongPredicate loseAcknowledgement, int failureStatus) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/upload", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Content-Range");
            long offset = Long.parseLong(range.substring(6, range.indexOf('-')));
            long total = Long.parseLong(range.substring(range.indexOf('/') + 1));
            requests.add(range);
            long bytes = 0;
            byte[] buffer = new byte[8192];
            int first = -1;
            int n;
            while ((n = exchange.getRequestBody().read(buffer)) != -1) {
                if (n > 0 && first < 0) first = buffer[0];
                bytes += n;
            }
            if (offset != accepted.get()) {
                exchange.sendResponseHeaders(416, -1);
            } else if (total == 100_000_000L && first != (int) (offset / 32_000_000L) + 1) {
                exchange.sendResponseHeaders(400, -1);
            } else {
                accepted.addAndGet(bytes);
                if (!loseAcknowledgement.test(offset)) exchange.sendResponseHeaders(accepted.get() == total ? 201 : 206, -1);
                else if (failureStatus > 0) exchange.sendResponseHeaders(failureStatus, -1);
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/upload";
    }
}
