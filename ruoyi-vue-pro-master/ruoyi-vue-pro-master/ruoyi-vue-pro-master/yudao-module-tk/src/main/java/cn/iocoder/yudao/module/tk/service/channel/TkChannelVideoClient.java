package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Objects;

import static cn.iocoder.yudao.module.tk.service.channel.TkChannelOAuthClient.*;

@Component
@RequiredArgsConstructor
public class TkChannelVideoClient {
    private static final int CHUNK = 4 * 1024 * 1024;
    private static final String GOOGLE_UPLOAD = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";
    private final TkChannelHttp http;

    public Checkpoint advance(TkSocialPublishTaskDO task, TkSocialPublishDetailDO detail, String token,
                              String source, long size, String uploadUrl) {
        return TkChannelProperties.X.equals(detail.getPlatform()) ? x(task, detail, bearer(token), source, size)
                : youtube(task, detail, bearer(token), source, null, null, size, uploadUrl);
    }

    Checkpoint advanceYouTube(TkSocialPublishTaskDO task, TkSocialPublishDetailDO detail, String token,
                              String source, Path localSource, String etag, long size, String uploadUrl) {
        return youtube(task, detail, bearer(token), source, localSource, etag, size, uploadUrl);
    }

    Checkpoint reconcileYouTube(String session, String token, long size) {
        String url = TkChannelProtocol.uploadUri(session).toString();
        try {
            TkChannelHttp.Response response = http.send("PUT", url, bearer(token), "video/mp4", new byte[0],
                    params("Content-Range", "bytes */" + size), false);
            return response.status == 308 ? null : received(response.json, size);
        } catch (TkSocialPlatformException ex) { throw youtubeSessionFailure(ex); }
    }

    private Checkpoint x(TkSocialPublishTaskDO task, TkSocialPublishDetailDO detail, String auth, String source, long size) {
        String id = detail.getExternalContainerId(), stage = detail.getPlatformStatus();
        long offset = detail.getUploadOffset() == null ? 0 : detail.getUploadOffset();
        if (id == null) {
            ObjectNode init = TkChannelHttp.JSON.createObjectNode().put("media_category", "tweet_video")
                    .put("media_type", "video/mp4").put("total_bytes", size);
            String created = requireId(http.json("POST", X_API + "/media/upload/initialize", auth, init, false).path("data"), "[0-9]{1,24}", false);
            return new Checkpoint("X_UPLOAD", created, null, null, 0, 0);
        }
        if (!id.matches("[0-9]{1,24}")) throw invalidState();
        if ("X_UPLOAD".equals(stage) && offset < size) {
            byte[] bytes = readRange(source, offset, size);
            ObjectNode append = TkChannelHttp.JSON.createObjectNode().put("segment_index", offset / CHUNK)
                    .put("media", Base64.getEncoder().encodeToString(bytes));
            http.json("POST", X_API + "/media/upload/" + id + "/append", auth, append, true);
            return new Checkpoint("X_UPLOAD", id, null, null, offset + bytes.length, 0);
        }
        if ("X_UPLOAD".equals(stage) || "X_PROCESSING".equals(stage)) {
            JsonNode result = "X_UPLOAD".equals(stage)
                    ? http.json("POST", X_API + "/media/upload/" + id + "/finalize", auth, null, true)
                    : http.json("GET", X_API + "/media/upload?command=STATUS&media_id=" + id, auth, null, false);
            JsonNode data = result.path("data");
            if (!data.isObject()) throw invalidState();
            JsonNode info = data.path("processing_info");
            String processing = info.path("state").asText();
            if (info.isMissingNode() || info.isNull() || "succeeded".equals(processing)) return new Checkpoint("X_READY", id, null, null, size, 0);
            if ("failed".equals(processing)) throw rejected("X_MEDIA_REJECTED", "X rejected video processing");
            if (!"pending".equals(processing) && !"in_progress".equals(processing)) throw invalidState();
            return new Checkpoint("X_PROCESSING", id, null, null, size, Math.max(2, Math.min(300, info.path("check_after_secs").asInt(10))));
        }
        if (!"X_READY".equals(stage)) throw invalidState();
        ObjectNode post = TkChannelHttp.JSON.createObjectNode();
        if (task.getChannelText() != null && !task.getChannelText().isEmpty()) post.put("text", task.getChannelText());
        post.putObject("media").putArray("media_ids").add(id);
        String postId = requireId(http.json("POST", X_API + "/tweets", auth, post, true).path("data"), "[0-9]{1,24}", true);
        return new Checkpoint("PUBLISHED", id, null, postId, size, 0);
    }

    private Checkpoint youtube(TkSocialPublishTaskDO task, TkSocialPublishDetailDO detail, String auth, String source,
                               Path localSource, String etag, long size, String session) {
        String id = detail.getExternalPostId();
        if (id != null) return youtubeStatus(id, auth, size);
        if (session == null) {
            ObjectNode data = TkChannelHttp.JSON.createObjectNode();
            data.putObject("snippet").put("title", task.getTitle()).put("description", task.getChannelText()).put("categoryId", "22");
            data.putObject("status").put("privacyStatus", task.getChannelPrivacy())
                    .put("selfDeclaredMadeForKids", Boolean.TRUE.equals(task.getChannelForKids()))
                    .put("containsSyntheticMedia", Boolean.TRUE.equals(task.getChannelSynthetic()));
            TkChannelHttp.Response opened = http.send("POST", GOOGLE_UPLOAD, auth, TkChannelHttp.JSON_TYPE,
                    data.toString().getBytes(StandardCharsets.UTF_8), params("X-Upload-Content-Type", "video/mp4",
                            "X-Upload-Content-Length", Long.toString(size)), false);
            return new Checkpoint("YT_UPLOAD", null, TkChannelProtocol.uploadUri(opened.location).toString(), null, 0, 0);
        }
        String url = TkChannelProtocol.uploadUri(session).toString();
        try {
            TkChannelHttp.Response query = http.send("PUT", url, auth, "video/mp4", new byte[0],
                    params("Content-Range", "bytes */" + size), false);
            if (query.status != 308) return received(query.json, size);
            long offset = TkChannelProtocol.nextByte(query.range, size);
            byte[] chunk = localSource == null ? readRange(source, offset, size, etag) : readLocal(localSource, offset, size);
            long end = offset + chunk.length - 1;
            TkChannelHttp.Response sent = http.send("PUT", url, auth, "video/mp4", chunk,
                    params("Content-Range", "bytes " + offset + "-" + end + "/" + size), false);
            if (sent.status != 308) return received(sent.json, size);
            long next = TkChannelProtocol.nextByte(sent.range, size);
            if (next <= offset) throw TkChannelHttp.failure(0, false);
            return new Checkpoint("YT_UPLOAD", null, session, null, next, 0);
        } catch (TkSocialPlatformException e) {
            throw youtubeSessionFailure(e);
        }
    }

    private static TkSocialPlatformException youtubeSessionFailure(TkSocialPlatformException e) {
        if ("CHANNEL_HTTP_404".equals(e.getCode()) || "CHANNEL_HTTP_410".equals(e.getCode()))
            return new TkSocialPlatformException("YT_SESSION_GONE", "YouTube upload session expired; verify the channel before trying again", false, false, true);
        return e;
    }

    public Checkpoint youtubeStatus(String id, String auth, long size) {
        if (!id.matches("[A-Za-z0-9_-]{11}")) throw invalidState();
        JsonNode items = http.json("GET", YT_API + "/videos?part=status,processingDetails&id=" + id, auth, null, false).path("items");
        if (!items.isArray() || items.size() != 1 || !id.equals(items.get(0).path("id").asText())) throw invalidState();
        JsonNode item = items.get(0);
        String upload = item.path("status").path("uploadStatus").asText();
        String processing = item.path("processingDetails").path("processingStatus").asText();
        if ("processed".equals(upload) || "succeeded".equals(processing)) return new Checkpoint("PUBLISHED", null, null, id, size, 0);
        if ("rejected".equals(upload) || "failed".equals(upload) || "deleted".equals(upload) || "failed".equals(processing) || "terminated".equals(processing))
            throw rejected("YT_VIDEO_REJECTED", "YouTube rejected video processing");
        return new Checkpoint("YT_PROCESSING", null, null, id, size, 15);
    }

    private static Checkpoint received(JsonNode body, long size) {
        return new Checkpoint("YT_PROCESSING", null, null, requireId(body, "[A-Za-z0-9_-]{11}", true), size, 5);
    }

    private static byte[] readRange(String source, long offset, long size) {
        return readRange(source, offset, size, null);
    }

    private static byte[] readRange(String source, long offset, long size, String etag) {
        HttpURLConnection connection = null;
        try {
            int length = Math.toIntExact(Math.min(CHUNK, size - offset));
            if (offset < 0 || length <= 0) throw invalidState();
            long end = offset + length - 1;
            connection = (HttpURLConnection) URI.create(source).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("Range", "bytes=" + offset + "-" + end);
            if (etag != null) connection.setRequestProperty("If-Match", etag.startsWith("\"") ? etag : "\"" + etag + "\"");
            int status = connection.getResponseCode();
            if (etag != null && (status == 412 || (status == 206 && !unquote(etag).equals(unquote(connection.getHeaderField("ETag"))))))
                throw rejected("MEDIA_SOURCE_CHANGED", "stored video changed after completion");
            if (status != 206 || !Objects.equals("bytes " + offset + "-" + end + "/" + size, connection.getHeaderField("Content-Range")))
                throw new IOException("Unexpected source range");
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = TkChannelHttp.bounded(input, length);
                if (bytes.length != length) throw new IOException("Truncated source range");
                return bytes;
            }
        } catch (IOException ex) { throw TkChannelHttp.failure(0, false); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static String unquote(String etag) { return etag == null ? "" : etag.replace("\"", ""); }

    private static byte[] readLocal(Path path, long offset, long size) {
        int length = Math.toIntExact(Math.min(CHUNK, size - offset));
        if (offset < 0 || length <= 0) throw invalidState();
        ByteBuffer bytes = ByteBuffer.allocate(length);
        try (SeekableByteChannel file = Files.newByteChannel(path)) {
            if (file.size() != size) throw invalidState();
            file.position(offset);
            while (bytes.hasRemaining()) if (file.read(bytes) < 0) throw invalidState();
            return bytes.array();
        } catch (IOException ex) { throw TkChannelHttp.failure(0, false); }
    }

    private static String requireId(JsonNode node, String pattern, boolean uncertain) {
        String id = node.path("id").asText();
        if (!id.matches(pattern)) {
            if (uncertain) throw invalidState();
            throw rejected("CHANNEL_RESPONSE_INVALID", "Platform response was incomplete");
        }
        return id;
    }
    private static TkSocialPlatformException invalidState() {
        return new TkSocialPlatformException("CHANNEL_STATE_UNCERTAIN", "Platform result requires verification", false, false, true);
    }
    private static TkSocialPlatformException rejected(String code, String message) {
        return new TkSocialPlatformException(code, message, false, false, false);
    }

    @lombok.Value
    public static class Checkpoint {
        String stage, containerId, uploadUrl, postId;
        long offset;
        int delay;
    }
}
