package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.hutool.core.util.StrUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.Optional;

final class TkFileCleanupPathPolicy {

    private static final String TK_PREFIX = "tk/";
    private static final String GENERATION_TASK_SEGMENT = "/generation-tasks/";
    private static final String REFERENCE_VIDEO_PREFIX = "tk/reference-videos/";
    private static final String REFERENCE_COVER_PREFIX = "tk/reference-covers/";

    private TkFileCleanupPathPolicy() {
    }

    static OptionalLong extractGenerationTaskId(String path) {
        Optional<String> generationTaskPath = extractGenerationTaskPath(path);
        if (!generationTaskPath.isPresent()) {
            return OptionalLong.empty();
        }
        String[] parts = generationTaskPath.get().split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("generation-tasks".equals(parts[i])) {
                try {
                    return OptionalLong.of(Long.parseLong(parts[i + 1]));
                } catch (NumberFormatException ex) {
                    return OptionalLong.empty();
                }
            }
        }
        return OptionalLong.empty();
    }

    static Optional<String> extractGenerationTaskPath(String value) {
        String path = normalizeToPath(value);
        if (!isSafeTkPath(path) || !path.contains(GENERATION_TASK_SEGMENT)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    static boolean isReferencePreviewPath(String path) {
        if (!isSafeTkPath(path)) {
            return false;
        }
        return StrUtil.startWith(path, REFERENCE_VIDEO_PREFIX)
                || StrUtil.startWith(path, REFERENCE_COVER_PREFIX);
    }

    private static boolean isSafeTkPath(String path) {
        return StrUtil.isNotBlank(path)
                && StrUtil.startWith(path, TK_PREFIX)
                && !path.contains("..")
                && !path.contains("\\");
    }

    private static String normalizeToPath(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        String text = StrUtil.trim(value);
        try {
            if (StrUtil.startWithIgnoreCase(text, "http://") || StrUtil.startWithIgnoreCase(text, "https://")) {
                text = URI.create(text).getRawPath();
            }
        } catch (Exception ignored) {
            // Fall through to plain string parsing below.
        }
        text = StrUtil.subBefore(text, "?", false);
        text = StrUtil.subBefore(text, "#", false);
        while (StrUtil.startWith(text, "/")) {
            text = StrUtil.removePrefix(text, "/");
        }
        int tkIndex = text.indexOf(TK_PREFIX);
        if (tkIndex > 0) {
            text = text.substring(tkIndex);
        }
        try {
            return URLDecoder.decode(text, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return text;
        }
    }
}
