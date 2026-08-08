package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class TkRenderMediaSupport {

    private static final DecimalFormat TEMPO_FORMAT =
            new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.US));

    private TkRenderMediaSupport() {
    }

    static boolean shouldAdaptAudio(double videoDuration, double audioDuration) {
        // TTS narration is the timing source for subtitles. Do not stretch it to match material length.
        return false;
    }

    static String buildAtempoFilter(double videoDuration, double audioDuration) {
        double tempo = audioDuration / videoDuration;
        List<String> filters = new ArrayList<>();
        while (tempo > 2.0D) {
            filters.add("atempo=2");
            tempo = tempo / 2.0D;
        }
        while (tempo < 0.5D) {
            filters.add("atempo=0.5");
            tempo = tempo / 0.5D;
        }
        filters.add("atempo=" + TEMPO_FORMAT.format(tempo));
        return String.join(",", filters);
    }

    static String buildVideoSpeedFilter(double sourceDuration, double targetDuration) {
        double speed = sourceDuration / targetDuration;
        return "setpts=PTS/" + TEMPO_FORMAT.format(speed);
    }

    static String formatSeconds(double seconds) {
        return TEMPO_FORMAT.format(Math.max(0D, seconds));
    }

    static String sourceCacheFileName(String url, String fileName) {
        return "source-cache-" + sha256(url).substring(0, 16) + "-" + safeName(fileName);
    }

    static String safeName(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "video.mp4");
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

}
