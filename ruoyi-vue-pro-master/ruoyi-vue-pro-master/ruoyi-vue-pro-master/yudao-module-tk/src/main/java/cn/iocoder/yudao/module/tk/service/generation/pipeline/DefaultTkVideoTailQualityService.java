package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DefaultTkVideoTailQualityService implements TkVideoTailQualityService {

    private static final double TAIL_SECONDS = 8D;
    private static final double MIN_TAIL_UNIQUE_RATIO = 0.30D;
    private static final double AUDIO_VIDEO_TOLERANCE_SECONDS = 0.60D;
    private static final double SUBTITLE_AUDIO_TOLERANCE_SECONDS = 1.00D;

    @Resource
    private TkGenerationProperties generationProperties;

    @Override
    public TkVideoTailQualityReport inspect(TkGenerationTaskDO task, TkRenderResult renderResult) {
        if (renderResult == null || StrUtil.isBlank(renderResult.getOutputUrl())) {
            return TkVideoTailQualityReport.pass(0D, 0D, 0D);
        }
        try {
            double videoDuration = probeDuration(renderResult.getOutputUrl());
            double audioDuration = StrUtil.isBlank(task.getAudioUrl()) ? 0D : probeDuration(task.getAudioUrl());
            double subtitleEnd = resolveSubtitleEnd(renderResult.getSubtitleTimelineUrl());
            boolean videoShorterThanAudio = TkVideoTailQualitySupport.isVideoShorterThanAudio(
                    videoDuration, audioDuration, AUDIO_VIDEO_TOLERANCE_SECONDS);
            boolean subtitleAudioMismatch = TkVideoTailQualitySupport.hasSubtitleAudioMismatch(
                    audioDuration, subtitleEnd, SUBTITLE_AUDIO_TOLERANCE_SECONDS);
            boolean lowDynamicTail = TkVideoTailQualitySupport.isLowDynamicTail(
                    probeTailFrameHashes(renderResult.getOutputUrl()), MIN_TAIL_UNIQUE_RATIO);
            boolean retryRecommended = lowDynamicTail || videoShorterThanAudio || subtitleAudioMismatch;
            String message = StrUtil.format("video={}, audio={}, subtitleEnd={}, lowDynamicTail={}, videoShorterThanAudio={}, subtitleAudioMismatch={}",
                    videoDuration, audioDuration, subtitleEnd, lowDynamicTail, videoShorterThanAudio, subtitleAudioMismatch);
            return new TkVideoTailQualityReport(retryRecommended, lowDynamicTail, videoShorterThanAudio,
                    subtitleAudioMismatch, videoDuration, audioDuration, subtitleEnd, message);
        } catch (Exception ex) {
            log.warn("[inspect][taskId({}) trace({}) 视频尾部质量检测失败，放行本次生成]",
                    task == null ? null : task.getId(), task == null ? null : task.getBusinessTraceId(), ex);
            return TkVideoTailQualityReport.pass(0D, 0D, 0D);
        }
    }

    private double probeDuration(String mediaUrl) throws Exception {
        if (StrUtil.isBlank(mediaUrl)) {
            return 0D;
        }
        List<String> command = Arrays.asList(ffprobe(), "-v", "error",
                "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                resolveDownloadUrl(mediaUrl));
        String output = runCommand(command, 30);
        if (StrUtil.isBlank(output)) {
            return 0D;
        }
        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    private List<String> probeTailFrameHashes(String videoUrl) throws Exception {
        if (StrUtil.isBlank(videoUrl)) {
            return new ArrayList<>();
        }
        List<String> command = Arrays.asList(ffmpeg(), "-hide_banner", "-v", "error",
                "-sseof", "-" + String.format(java.util.Locale.ROOT, "%.3f", TAIL_SECONDS),
                "-i", resolveDownloadUrl(videoUrl),
                "-vf", "fps=1,scale=160:-1",
                "-f", "framemd5", "-");
        String output = runCommand(command, 60);
        List<String> hashes = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (StrUtil.isBlank(line) || line.startsWith("#")) {
                continue;
            }
            int commaIndex = line.lastIndexOf(',');
            if (commaIndex >= 0 && commaIndex + 1 < line.length()) {
                hashes.add(line.substring(commaIndex + 1).trim());
            }
        }
        return hashes;
    }

    private double resolveSubtitleEnd(String subtitleTimelineUrl) {
        if (StrUtil.isBlank(subtitleTimelineUrl)) {
            return 0D;
        }
        try {
            TkGenerationProperties.RenderDownload renderDownload = generationProperties.getRenderDownload();
            String requestUrl = DefaultTkVideoRenderService.resolveDownloadUrl(subtitleTimelineUrl,
                    renderDownload.getPublicBaseUrl(), renderDownload.getInternalBaseUrl());
            int timeoutMillis = Math.max(10, renderDownload.getTimeoutSeconds() == null
                    ? 180 : renderDownload.getTimeoutSeconds()) * 1000;
            try (HttpResponse response = HttpRequest.get(requestUrl).timeout(timeoutMillis).execute()) {
                if (!response.isOk()) {
                    return 0D;
                }
                TkSubtitleTimeline timeline = JsonUtils.parseObject(response.body(), TkSubtitleTimeline.class);
                return resolveSubtitleEnd(timeline);
            }
        } catch (Exception ex) {
            return 0D;
        }
    }

    private double resolveSubtitleEnd(TkSubtitleTimeline timeline) {
        if (timeline == null) {
            return 0D;
        }
        double subtitleEnd = timeline.getAudioDuration();
        if (timeline.getSegments() != null) {
            for (TkSubtitleSegment segment : timeline.getSegments()) {
                subtitleEnd = Math.max(subtitleEnd, segment.getEnd());
            }
        }
        return subtitleEnd;
    }

    private String runCommand(List<String> command, int timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("command timeout");
        }
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            output = builder.toString();
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(output);
        }
        return output;
    }

    private String resolveDownloadUrl(String url) {
        TkGenerationProperties.RenderDownload renderDownload = generationProperties.getRenderDownload();
        return DefaultTkVideoRenderService.resolveDownloadUrl(url,
                renderDownload.getPublicBaseUrl(), renderDownload.getInternalBaseUrl());
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

}
