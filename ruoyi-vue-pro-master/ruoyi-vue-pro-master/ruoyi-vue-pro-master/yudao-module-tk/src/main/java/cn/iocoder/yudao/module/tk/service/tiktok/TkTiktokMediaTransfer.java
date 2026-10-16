package cn.iocoder.yudao.module.tk.service.tiktok;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Bounded HTTP calls. Defaults can be overridden with Java system properties under
 * tk.tiktok.media-transfer: connect-timeout-millis, read-idle-timeout-millis,
 * write-idle-timeout-millis, upload-attempt-timeout-millis,
 * upload-session-timeout-millis, download-timeout-millis, api-timeout-millis.
 * Upload retries remain exclusively owned by TkTiktokApiClient.
 */
public final class TkTiktokMediaTransfer {

    private static final long MAX_VIDEO_BYTES = 4_000_000_000L;
    private static final int MAX_JSON_BYTES = 4 * 1024 * 1024;
    private static final String PROPERTY_PREFIX = "tk.tiktok.media-transfer.";
    private static final ScheduledExecutorService INTERRUPT_MONITOR = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "tk-tiktok-transfer-interrupt");
        thread.setDaemon(true);
        return thread;
    });

    private final OkHttpClient uploadClient;
    private final OkHttpClient downloadClient;
    private final long uploadSessionMillis;
    private final long apiTimeoutMillis;

    public TkTiktokMediaTransfer() {
        this(configured("connect-timeout-millis", 10_000L),
                configured("read-idle-timeout-millis", 60_000L),
                configured("write-idle-timeout-millis", 60_000L),
                configured("upload-attempt-timeout-millis", 180_000L),
                configured("upload-session-timeout-millis", 20 * 60_000L),
                configured("download-timeout-millis", 15 * 60_000L),
                configured("api-timeout-millis", 60_000L));
    }

    public TkTiktokMediaTransfer(long connectMillis, long readIdleMillis, long writeIdleMillis,
                                 long uploadAttemptMillis, long uploadSessionMillis, long downloadMillis) {
        this(connectMillis, readIdleMillis, writeIdleMillis, uploadAttemptMillis, uploadSessionMillis,
                downloadMillis, 60_000L);
    }

    public TkTiktokMediaTransfer(long connectMillis, long readIdleMillis, long writeIdleMillis,
                                 long uploadAttemptMillis, long uploadSessionMillis, long downloadMillis,
                                 long apiTimeoutMillis) {
        this.uploadSessionMillis = positiveTimeout(uploadSessionMillis);
        this.apiTimeoutMillis = positiveTimeout(apiTimeoutMillis);
        uploadClient = new OkHttpClient.Builder()
                .connectTimeout(positiveTimeout(connectMillis), TimeUnit.MILLISECONDS)
                .readTimeout(positiveTimeout(readIdleMillis), TimeUnit.MILLISECONDS)
                .writeTimeout(positiveTimeout(writeIdleMillis), TimeUnit.MILLISECONDS)
                .callTimeout(positiveTimeout(uploadAttemptMillis), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build();
        downloadClient = uploadClient.newBuilder()
                .callTimeout(positiveTimeout(downloadMillis), TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    /** Streams at most maxBytes (and never more than TikTok's 4 GB limit) into a caller-owned temporary file. */
    public static void download(String url, Path target, long maxBytes) throws IOException {
        new TkTiktokMediaTransfer().downloadToFile(url, target, maxBytes);
    }

    public void downloadToFile(String url, Path target, long maxBytes) throws IOException {
        if (maxBytes <= 0) throw new IllegalArgumentException("media byte limit must be positive");
        long limit = Math.min(maxBytes, MAX_VIDEO_BYTES);
        checkInterrupted();
        Call call = downloadClient.newCall(request(url).get().build());
        boolean opened = false;
        try (InterruptGuard ignored = new InterruptGuard(call); Response response = call.execute()) {
            if (!response.isSuccessful()) throw new IOException("cannot download media, HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("media response body is missing");
            if (body.contentLength() > limit) throw new IOException("media exceeds byte limit");
            try (InputStream input = body.byteStream()) {
                OutputStream output = Files.newOutputStream(target);
                opened = true;
                try (OutputStream closeableOutput = output) {
                    copyBounded(input, closeableOutput, limit);
                }
            }
        } catch (IOException ex) {
            if (opened) {
                try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            }
            throw safeFailure(ex);
        }
    }

    long newUploadDeadline() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(uploadSessionMillis);
    }

    void checkUploadActive(long deadlineNanos) throws TransferStoppedException {
        checkInterrupted();
        if (deadlineNanos - System.nanoTime() <= 0) {
            throw new TransferStoppedException("TikTok upload session deadline exceeded");
        }
    }

    int putChunk(String url, byte[] bytes, String contentType, long offset, long total,
                 long deadlineNanos) throws IOException {
        checkUploadActive(deadlineNanos);
        Request request = request(url)
                .header("Content-Range", "bytes " + offset + "-" + (offset + bytes.length - 1) + "/" + total)
                .put(RequestBody.create(bytes, MediaType.parse(contentType))).build();
        Call call = uploadClient.newCall(request);
        call.timeout().deadlineNanoTime(deadlineNanos);
        try (InterruptGuard ignored = new InterruptGuard(call); Response response = call.execute()) {
            checkUploadActive(deadlineNanos);
            return response.code();
        } catch (IOException ex) {
            checkUploadActive(deadlineNanos);
            throw safeFailure(ex);
        }
    }

    ApiResponse post(String url, String authorization, String contentType, String body) throws IOException {
        Request.Builder builder = request(url).post(RequestBody.create(body, MediaType.parse(contentType)));
        if (authorization != null) builder.header("Authorization", authorization);
        return json(builder.build());
    }

    ApiResponse get(String url, String authorization) throws IOException {
        return json(request(url).header("Authorization", authorization).get().build());
    }

    private ApiResponse json(Request request) throws IOException {
        checkInterrupted();
        Call call = uploadClient.newCall(request);
        call.timeout().timeout(apiTimeoutMillis, TimeUnit.MILLISECONDS);
        try (InterruptGuard ignored = new InterruptGuard(call); Response response = call.execute()) {
            ResponseBody body = response.body();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (body != null) {
                if (body.contentLength() > MAX_JSON_BYTES) throw new IOException("API response exceeds byte limit");
                try (InputStream input = body.byteStream()) {
                    copyBounded(input, output, MAX_JSON_BYTES);
                }
            }
            return new ApiResponse(response.code(), response.header("Content-Type"),
                    new String(output.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw safeFailure(ex);
        }
    }

    private static Request.Builder request(String url) throws IOException {
        try {
            return new Request.Builder().url(url);
        } catch (IllegalArgumentException ex) {
            // URL parsing errors may contain signed query strings. Never propagate them.
            throw new IOException("invalid media transfer URL");
        }
    }

    private static void copyBounded(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long count = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            checkInterrupted();
            if (read > limit - count) throw new IOException("media exceeds byte limit");
            output.write(buffer, 0, read);
            count += read;
        }
        checkInterrupted();
    }

    private static void checkInterrupted() throws TransferStoppedException {
        if (Thread.currentThread().isInterrupted()) throw new TransferStoppedException("TikTok transfer interrupted");
    }

    private static IOException safeFailure(IOException ex) {
        if (Thread.currentThread().isInterrupted()) return new TransferStoppedException("TikTok transfer interrupted");
        if (ex instanceof TransferStoppedException) return ex;
        // Keep our fixed messages, redact implementation exceptions that can embed a signed URL.
        String message = ex.getMessage();
        if (message != null && (message.startsWith("cannot download media, HTTP ")
                || message.equals("media exceeds byte limit") || message.equals("media response body is missing")
                || message.equals("API response exceeds byte limit"))) return new IOException(message);
        return new IOException(ex instanceof InterruptedIOException
                ? "TikTok HTTP transfer timed out" : "TikTok HTTP transfer failed (" + ex.getClass().getSimpleName() + ")");
    }

    private static long configured(String name, long fallback) {
        String value = System.getProperty(PROPERTY_PREFIX + name);
        return value == null ? fallback : positiveTimeout(Long.parseLong(value));
    }

    private static long positiveTimeout(long millis) {
        if (millis <= 0 || millis > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid HTTP timeout");
        return millis;
    }

    static final class TransferStoppedException extends InterruptedIOException {
        TransferStoppedException(String message) { super(message); }
    }

    static final class ApiResponse {
        final int status;
        final String contentType;
        final String body;

        ApiResponse(int status, String contentType, String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    private static final class InterruptGuard implements Closeable {
        private final ScheduledFuture<?> monitor;

        private InterruptGuard(Call call) throws TransferStoppedException {
            checkInterrupted();
            Thread owner = Thread.currentThread();
            monitor = INTERRUPT_MONITOR.scheduleAtFixedRate(() -> {
                if (owner.isInterrupted()) call.cancel();
            }, 0, 25, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() { monitor.cancel(false); }
    }
}
