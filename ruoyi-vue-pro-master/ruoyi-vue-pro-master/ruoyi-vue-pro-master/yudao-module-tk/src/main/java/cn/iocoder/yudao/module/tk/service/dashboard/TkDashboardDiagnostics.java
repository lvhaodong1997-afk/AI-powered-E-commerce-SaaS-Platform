package cn.iocoder.yudao.module.tk.service.dashboard;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class TkDashboardDiagnostics {

    static final Set<String> RUNNING_STATUSES = new HashSet<>(Arrays.asList(
            TkGenerationStatusEnum.PENDING,
            TkGenerationStatusEnum.PRECHECKED,
            TkGenerationStatusEnum.ANALYZING,
            TkGenerationStatusEnum.SCRIPT_READY,
            TkGenerationStatusEnum.VOICE_READY,
            TkGenerationStatusEnum.MATERIAL_MATCHING,
            TkGenerationStatusEnum.MATERIAL_MATCHED,
            TkGenerationStatusEnum.SUBTITLE_TIMELINE_READY,
            TkGenerationStatusEnum.VISUAL_ANALYZED,
            TkGenerationStatusEnum.CLIP_PLANNED,
            TkGenerationStatusEnum.RENDERING,
            TkGenerationStatusEnum.EXPORTING
    ));

    private TkDashboardDiagnostics() {
    }

    static boolean isRunningStatus(String status) {
        return RUNNING_STATUSES.contains(status);
    }

    static FailureDiagnosis classifyFailure(String failCode, String currentStep, String failReason) {
        String signal = StrUtil.join(" ", normalized(failCode), normalized(currentStep), normalized(failReason))
                .toUpperCase();
        if (StrUtil.containsAny(signal, "FFMPEG", "RENDER", "EXPORT")) {
            return new FailureDiagnosis("FFMPEG_RENDER", "RENDERING",
                    "Check render inputs, OSS source accessibility, and FFmpeg timeout settings.");
        }
        if (StrUtil.containsAny(signal, "DOWNLOAD", "SOURCE", "REFERENCE", "SOCKETTIMEOUT", "READ TIMED OUT")) {
            return new FailureDiagnosis("REFERENCE_DOWNLOAD", "FAILED",
                    "Check source video accessibility and retry source download.");
        }
        if (StrUtil.containsAny(signal, "OSS", "STORAGE", "UPLOAD")) {
            return new FailureDiagnosis("OSS_STORAGE", "FAILED",
                    "Check OSS object permissions, bucket reachability, and generated file upload.");
        }
        if (StrUtil.containsAny(signal, "VOICE", "AUDIO", "TTS")) {
            return new FailureDiagnosis("VOICEOVER", "VOICE_READY",
                    "Check voice configuration, TTS provider response, and audio file generation.");
        }
        if (StrUtil.containsAny(signal, "SUBTITLE", "ASS", "TIMELINE")) {
            return new FailureDiagnosis("SUBTITLE", "SUBTITLE_TIMELINE_READY",
                    "Check subtitle timeline, ASS generation, and subtitle render settings.");
        }
        if (StrUtil.containsAny(signal, "MATERIAL", "CLIP", "INSUFFICIENT")) {
            return new FailureDiagnosis("MATERIAL_MATCHING", "MATERIAL_MATCHING",
                    "Check material library availability and clip planning result.");
        }
        if (StrUtil.containsAny(signal, "TIKTOK", "AUTH", "PUBLISH", "TOKEN")) {
            return new FailureDiagnosis("TIKTOK_PUBLISH", "FAILED",
                    "Check TikTok account authorization and publishing status.");
        }
        return new FailureDiagnosis("UNKNOWN", "FAILED", "Open the task detail and inspect the full failure reason.");
    }

    private static String normalized(String value) {
        return StrUtil.blankToDefault(value, "");
    }

    @Data
    @AllArgsConstructor
    static class FailureDiagnosis {
        private String category;
        private String actionStatus;
        private String actionHint;
    }
}
