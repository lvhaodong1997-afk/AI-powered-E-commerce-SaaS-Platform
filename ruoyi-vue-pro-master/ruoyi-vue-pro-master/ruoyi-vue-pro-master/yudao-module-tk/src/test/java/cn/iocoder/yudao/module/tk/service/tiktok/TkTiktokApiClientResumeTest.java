package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenTiktokPlatformAdapter;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokApiClientResumeTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private String uploadUrl;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger retryableResponses = new AtomicInteger();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Capture every path: an accidental init or URL change must also fail the assertions.
        server.createContext("/", exchange -> {
            long size = 0;
            int first = -1;
            int last = -1;
            try (InputStream body = exchange.getRequestBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = body.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    if (first == -1) {
                        first = buffer[0] & 0xff;
                    }
                    last = buffer[read - 1] & 0xff;
                    size += read;
                }
            }
            String range = exchange.getRequestHeaders().getFirst("Content-Range");
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " " + exchange.getRequestHeaders().getFirst("Content-Type")
                    + " " + exchange.getRequestHeaders().getFirst("Content-Length")
                    + " " + range + " body=" + size + ":" + first + ":" + last);
            try {
                int status = retryableResponses.getAndUpdate(remaining -> Math.max(0, remaining - 1)) > 0
                        ? 503 : ("bytes 32000000-63999999/100000000".equals(range) ? 206 : 201);
                exchange.sendResponseHeaders(status, -1);
            } finally {
                exchange.close();
            }
        });
        server.start();
        uploadUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/upload?upload_id=same&upload_token=test";
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {32_000_000L, 64_000_000L})
    void resumeSkipsAcceptedChunksAndPreservesUrlRangesAndFilePosition(long uploadedBytes) throws Exception {
        Path video = chunkedVideo();

        new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/quicktime", uploadedBytes);

        String middle = "PUT /upload?upload_id=same&upload_token=test video/quicktime 32000000"
                + " bytes 32000000-63999999/100000000 body=32000000:21:22";
        String last = "PUT /upload?upload_id=same&upload_token=test video/quicktime 36000000"
                + " bytes 64000000-99999999/100000000 body=36000000:31:32";
        assertEquals(uploadedBytes == 32_000_000L ? Arrays.asList(middle, last) : Collections.singletonList(last),
                requests);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1L, 1L, 31_999_999L, 32_000_001L, 96_000_000L, 100_000_001L, Long.MAX_VALUE})
    void resumeRejectsInvalidBoundaryBeforeAnyHttpRequest(long uploadedBytes) throws Exception {
        Path video = chunkedVideo();

        assertThrows(IllegalArgumentException.class,
                () -> new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/mp4", uploadedBytes));

        assertTrue(requests.isEmpty());
    }

    @Test
    void resumeRejectsPartialSingleChunkBeforeAnyHttpRequest() throws Exception {
        Path video = Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});

        assertThrows(IllegalArgumentException.class,
                () -> new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/mp4", 1L));

        assertTrue(requests.isEmpty());
    }

    @Test
    void resumeWithCompleteFileSendsNothingForSingleAndMergedFinalChunks() throws Exception {
        TkTiktokApiClient client = new TkTiktokApiClient();
        Path small = Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});

        client.resumeUploadVideoChunks(uploadUrl, small, "video/mp4", 4L);
        client.resumeUploadVideoChunks(uploadUrl, chunkedVideo(), "video/mp4", 100_000_000L);

        assertTrue(requests.isEmpty());
    }

    @Test
    void zeroProgressAndOriginalUploadBothStartAtByteZero() throws Exception {
        TkTiktokApiClient client = new TkTiktokApiClient();
        Path video = Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});

        client.resumeUploadVideoChunks(uploadUrl, video, "video/mp4", 0L);
        client.uploadVideoChunks(uploadUrl, video, "video/mp4");

        String expected = "PUT /upload?upload_id=same&upload_token=test video/mp4 4 bytes 0-3/4 body=4:1:4";
        assertEquals(Arrays.asList(expected, expected), requests);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fencedInitialOrResumedUploadSendsNoRequest(boolean resume) throws Exception {
        Path video = chunkedVideo();
        TkOpenPublishPlatformAdapter adapter = new TkOpenTiktokPlatformAdapter(new TkTiktokApiClient());
        IllegalStateException fenced = new IllegalStateException("upload lease lost");
        AtomicInteger checks = new AtomicInteger();
        Runnable beforeChunk = () -> {
            checks.incrementAndGet();
            throw fenced;
        };

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> {
            if (resume) {
                adapter.resumeUploadVideo(uploadUrl, video, "video/quicktime", 32_000_000L, beforeChunk);
            } else {
                adapter.uploadVideo(uploadUrl, video, "video/quicktime", beforeChunk);
            }
        });

        assertSame(fenced, actual);
        assertEquals(1, checks.get());
        assertTrue(requests.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leaseLostAfterAcceptedChunkPreventsRemainingChunks(boolean resume) throws Exception {
        Path video = chunkedVideo();
        TkOpenPublishPlatformAdapter adapter = new TkOpenTiktokPlatformAdapter(new TkTiktokApiClient());
        IllegalStateException fenced = new IllegalStateException("upload lease lost");
        AtomicInteger checks = new AtomicInteger();
        Runnable beforeChunk = () -> {
            if (checks.incrementAndGet() == 2) {
                throw fenced;
            }
        };

        IllegalStateException actual = assertThrows(IllegalStateException.class, () -> {
            if (resume) {
                adapter.resumeUploadVideo(uploadUrl, video, "video/quicktime", 32_000_000L, beforeChunk);
            } else {
                adapter.uploadVideo(uploadUrl, video, "video/quicktime", beforeChunk);
            }
        });

        assertSame(fenced, actual);
        assertEquals(2, checks.get());
        String expectedRange = resume ? "32000000-63999999" : "0-31999999";
        String expectedMarkers = resume ? "21:22" : "11:12";
        assertEquals(Collections.singletonList("PUT /upload?upload_id=same&upload_token=test video/quicktime"
                + " 32000000 bytes " + expectedRange + "/100000000 body=32000000:" + expectedMarkers), requests);
    }

    @Test
    void leaseLostBeforeHttpRetryPropagatesWithoutAnotherRequest() throws Exception {
        Path video = Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});
        retryableResponses.set(1);
        IllegalStateException fenced = new IllegalStateException("upload lease lost during retry");
        AtomicInteger checks = new AtomicInteger();

        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/mp4", 0L, () -> {
                    if (checks.incrementAndGet() == 2) {
                        throw fenced;
                    }
                }));

        assertSame(fenced, actual);
        assertEquals(2, checks.get());
        assertEquals(Collections.singletonList(
                "PUT /upload?upload_id=same&upload_token=test video/mp4 4 bytes 0-3/4 body=4:1:4"), requests);
    }

    @Test
    void healthyLeaseIsCheckedBeforeEveryChunkAndRetry() throws Exception {
        Path video = chunkedVideo();
        retryableResponses.set(1);
        AtomicInteger checks = new AtomicInteger();

        new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/quicktime", 32_000_000L,
                () -> assertEquals(requests.size(), checks.getAndIncrement()));

        assertEquals(3, checks.get());
        String middle = "PUT /upload?upload_id=same&upload_token=test video/quicktime 32000000"
                + " bytes 32000000-63999999/100000000 body=32000000:21:22";
        String last = "PUT /upload?upload_id=same&upload_token=test video/quicktime 36000000"
                + " bytes 64000000-99999999/100000000 body=36000000:31:32";
        assertEquals(Arrays.asList(middle, middle, last), requests);
    }

    @Test
    void completedResumeDoesNotCallHookOrSendRequest() throws Exception {
        Path video = Files.write(tempDir.resolve("small.mp4"), new byte[]{1, 2, 3, 4});
        AtomicInteger checks = new AtomicInteger();

        new TkTiktokApiClient().resumeUploadVideoChunks(uploadUrl, video, "video/mp4", 4L,
                checks::incrementAndGet);

        assertEquals(0, checks.get());
        assertTrue(requests.isEmpty());
    }

    private Path chunkedVideo() throws Exception {
        Path video = tempDir.resolve("chunked.mov");
        try (RandomAccessFile file = new RandomAccessFile(video.toFile(), "rw")) {
            file.setLength(100_000_000L);
            long[] positions = {0L, 31_999_999L, 32_000_000L, 63_999_999L, 64_000_000L, 99_999_999L};
            int[] markers = {11, 12, 21, 22, 31, 32};
            for (int index = 0; index < positions.length; index++) {
                file.seek(positions[index]);
                file.write(markers[index]);
            }
        }
        return video;
    }
}
