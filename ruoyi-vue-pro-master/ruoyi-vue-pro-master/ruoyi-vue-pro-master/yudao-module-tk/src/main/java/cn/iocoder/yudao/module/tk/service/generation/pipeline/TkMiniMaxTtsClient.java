package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.reference.TkSafeRemoteUrlValidator;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** The Worker owns BCE signing; Java never holds BCE AK/SK or calls MiniMax directly. */
@Component
public class TkMiniMaxTtsClient implements TkVoiceTtsClient {
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkMiniMaxVoiceDictionaryService voiceDictionaryService;

    @Override
    public String provider() { return TkTtsProviderEnum.MINIMAX; }

    @Override
    public String audioFormat() { return "mp3"; }

    @Override
    public byte[] synthesize(TkVoiceSynthesisRequest request) {
        TkGenerationProperties.MiniMax config = generationProperties.getMinimax();
        if (request == null || StrUtil.isBlank(request.getText())) {
            throw new IllegalArgumentException("MiniMax 配音文案不能为空");
        }
        byte[] payload = JsonUtils.toJsonString(voiceDictionaryService.buildTtsRequest(
                request.getText(), request.getVoiceCode())).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            URI base = URI.create(config.getWorkerUrl());
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null) {
                throw new IllegalStateException("MiniMax Worker 地址配置无效");
            }
            String url = config.getWorkerUrl().replaceAll("/+$", "") + "/api/tts/minimax/generate";
            connection = openConnection(URI.create(url));
            configure(connection, timeoutMillis(config.getTimeoutSeconds(), 200));
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (StrUtil.isNotBlank(config.getInternalToken())) {
                connection.setRequestProperty("X-Internal-Token", config.getInternalToken());
            }
            connection.setDoOutput(true);
            // Streaming disables HttpURLConnection's implicit replay of a paid POST.
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream stream = connection.getOutputStream()) { stream.write(payload); }
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("MiniMax Worker 调用失败，HTTP " + connection.getResponseCode());
            }
            JsonNode root;
            try (InputStream stream = connection.getInputStream()) {
                root = JsonUtils.getObjectMapper().readTree(readLimited(stream, 1024 * 1024));
            }
            if (root == null) { throw new IllegalStateException("MiniMax Worker 响应无效"); }
            JsonNode status = root.path("base_resp").path("status_code");
            if (!status.isIntegralNumber() || status.longValue() != 0L) {
                throw new IllegalStateException("MiniMax 音频生成失败或返回状态无效");
            }
            String audioUrl = root.path("output").path("audio").path("url").asText("");
            if (StrUtil.isBlank(audioUrl)) { audioUrl = root.path("audio_url").asText(""); }
            requireHttpsAudioUri(audioUrl);
            return downloadAudio(audioUrl);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            // Never persist raw upstream bodies, signed URLs, request text or credentials in task errors.
            throw new IllegalStateException("MiniMax Worker 请求失败，请检查服务配置与连接");
        } finally {
            if (connection != null) { connection.disconnect(); }
        }
    }

    byte[] downloadAudio(String audioUrl) {
        HttpURLConnection connection = null;
        try {
            requireHttpsAudioUri(audioUrl);
            URI uri = new TkSafeRemoteUrlValidator(Collections.emptyList(), true).validate(audioUrl);
            connection = openConnection(uri);
            TkGenerationProperties.MiniMax config = generationProperties.getMinimax();
            configure(connection, timeoutMillis(config.getDownloadTimeoutSeconds(), 60));
            // This is a separate connection: the Worker token is never sent to audio storage.
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("MiniMax 音频下载失败，HTTP " + connection.getResponseCode());
            }
            long limit = config.getMaxDownloadBytes() == null ? 50L * 1024 * 1024 : config.getMaxDownloadBytes();
            if (limit <= 0 || limit > 200L * 1024 * 1024) {
                throw new IllegalStateException("MiniMax 音频下载大小限制配置无效");
            }
            if (connection.getContentLengthLong() > limit) {
                throw new IllegalStateException("MiniMax 音频超过下载大小限制");
            }
            byte[] bytes;
            try (InputStream stream = connection.getInputStream()) { bytes = readLimited(stream, limit); }
            requireMp3(bytes);
            return bytes;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("MiniMax 音频地址无效或下载失败");
        } finally {
            if (connection != null) { connection.disconnect(); }
        }
    }

    HttpURLConnection openConnection(URI uri) throws IOException {
        return (HttpURLConnection) uri.toURL().openConnection();
    }

    private static void configure(HttpURLConnection connection, int readTimeout) {
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(readTimeout);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
    }

    private static int timeoutMillis(Integer seconds, int fallback) {
        int value = seconds == null ? fallback : seconds;
        if (value < 1 || value > 600) { throw new IllegalStateException("MiniMax 超时时间配置无效"); }
        return value * 1000;
    }

    private static URI requireHttpsAudioUri(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception ex) {
            throw new IllegalStateException("MiniMax 未返回有效 HTTPS 音频地址");
        }
    }

    static byte[] readLimited(InputStream stream, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int length;
        long total = 0;
        while ((length = stream.read(buffer)) != -1) {
            total += length;
            if (total > limit) { throw new IllegalStateException("MiniMax 响应超过下载大小限制"); }
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    static void requireMp3(byte[] bytes) {
        boolean id3 = bytes.length >= 10 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3';
        boolean frame = bytes.length >= 4 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xe0) == 0xe0 && (bytes[1] & 0x06) != 0
                && (bytes[2] & 0xf0) != 0xf0 && (bytes[2] & 0x0c) != 0x0c;
        if (!id3 && !frame) { throw new IllegalStateException("MiniMax 下载内容不是有效 MP3 音频"); }
    }
}
