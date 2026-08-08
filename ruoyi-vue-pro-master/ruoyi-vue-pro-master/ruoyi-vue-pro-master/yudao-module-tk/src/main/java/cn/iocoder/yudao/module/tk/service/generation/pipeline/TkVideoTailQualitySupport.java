package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.collection.CollUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TkVideoTailQualitySupport {

    private TkVideoTailQualitySupport() {
    }

    static boolean isLowDynamicTail(List<String> frameHashes, double minUniqueRatio) {
        if (CollUtil.isEmpty(frameHashes)) {
            return false;
        }
        Set<String> uniqueHashes = new HashSet<>(frameHashes);
        double uniqueRatio = uniqueHashes.size() * 1.0D / frameHashes.size();
        return uniqueRatio < minUniqueRatio;
    }

    static boolean hasSubtitleAudioMismatch(double audioDuration, double subtitleEnd, double toleranceSeconds) {
        if (audioDuration <= 0D || subtitleEnd <= 0D) {
            return false;
        }
        return Math.abs(audioDuration - subtitleEnd) > toleranceSeconds;
    }

    static boolean isVideoShorterThanAudio(double videoDuration, double audioDuration, double toleranceSeconds) {
        if (videoDuration <= 0D || audioDuration <= 0D) {
            return false;
        }
        return videoDuration + toleranceSeconds < audioDuration;
    }

}
