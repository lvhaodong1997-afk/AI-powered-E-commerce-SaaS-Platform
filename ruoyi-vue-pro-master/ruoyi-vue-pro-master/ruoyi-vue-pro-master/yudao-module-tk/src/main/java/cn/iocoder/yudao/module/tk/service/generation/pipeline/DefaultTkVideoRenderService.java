package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationStepLogDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationStepLogMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DefaultTkVideoRenderService implements TkVideoRenderService {

    private static final String NORMALIZE_VIDEO_FILTER =
            "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1";
    private static final String FINAL_AUDIO_FILTER = "loudnorm=I=-16:TP=-1.5:LRA=11";
    private static final double SECTION_COMPRESS_EPSILON_SECONDS = 0.05D;

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkGenerationOutputStorageService generationOutputStorageService;
    @Resource
    private TkGenerationStepLogMapper stepLogMapper;
    @Resource
    private TkKeywordHighlightService keywordHighlightService;
    @Resource
    private TkSubtitleTimelineService subtitleTimelineService;
    @Resource
    private TkVisualAnalysisService visualAnalysisService;
    @Resource
    private TkSubtitleLayoutService subtitleLayoutService;
    @Resource
    private TkAssSubtitleRenderService assSubtitleRenderService;

    @Override
    public TkRenderResult render(TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan) {
        return render(task, clipPlan, TkRenderProgressReporter.NOOP);
    }

    @Override
    public TkRenderResult render(TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan,
                                 TkRenderProgressReporter progressReporter) {
        File taskDir = FileUtil.mkdir(resolveWorkDir(task));
        boolean completed = false;
        try {
            TkRenderProgressReporter reporter = progressReporter == null ? TkRenderProgressReporter.NOOP : progressReporter;
            Map<String, File> sourceCache = new java.util.concurrent.ConcurrentHashMap<>();
            int materialCount = (int) clipPlan.stream()
                    .filter(item -> item != null && StrUtil.isNotBlank(item.getFileUrl()))
                    .map(TkClipPlanItem::getFileUrl)
                    .distinct()
                    .count();
            reporter.report("RENDER_DOWNLOAD", "正在下载素材", 66, 0, materialCount);
            runRenderStep(task, "RENDER_DOWNLOAD", "Render download material",
                    () -> prefetchSourceFiles(taskDir, clipPlan, sourceCache, reporter));

            reporter.report("RENDER_TRANSCODE_SEGMENTS", "正在转码拼接素材", 72, 0, 0);
            File mergedVideo = runRenderStep(task, "RENDER_TRANSCODE_SEGMENTS", "Render transcode segments",
                    () -> {
                        List<File> segments = buildSegments(task, taskDir, clipPlan, sourceCache, reporter);
                        File concatList = writeConcatList(taskDir, segments);
                        File output = new File(taskDir, "merged-video.mp4");
                        runCommand(Arrays.asList(ffmpeg(), "-y", "-f", "concat", "-safe", "0", "-i", concatList.getAbsolutePath(),
                                "-c", "copy", output.getAbsolutePath()));
                        return ensureTargetDuration(task, taskDir, output);
                    });

            reporter.report("RENDER_SUBTITLE", "正在生成字幕", 88, 0, 1);
            RenderMedia renderMedia = runRenderStep(task, "RENDER_SUBTITLE", "Render subtitle assets",
                    () -> {
                        File audioFile = StrUtil.isBlank(task.getAudioUrl()) ? null : prepareVoiceAudio(taskDir, task.getAudioUrl());
                        SubtitleAssets subtitleAssets = buildSubtitleAssets(taskDir, task, clipPlan, mergedVideo, audioFile);
                        return new RenderMedia(audioFile, subtitleAssets);
                    });

            reporter.report("RENDER_FINAL_MERGE", "正在合成视频、配音和背景音乐", 92, 0, 1);
            File finalVideo = runRenderStep(task, "RENDER_FINAL_MERGE", "Render final merge",
                    () -> {
                        File output = new File(taskDir, "final-video.mp4");
                        File bgmFile = resolveBgmFile(taskDir, task);
                        List<String> renderCommand = buildFinalRenderCommand(ffmpeg(), mergedVideo, renderMedia.audioFile, bgmFile,
                                normalizeBgmVolume(task.getBgmVolume()), TkVideoDurationSupport.normalize(task.getTargetDuration()),
                                renderMedia.subtitleAssets.assFile, output, ffmpegPreset(), nativeOpeningDurationSeconds(task));
                        runCommand(renderCommand);
                        return output;
                    });

            reporter.report("RENDER_UPLOAD_OSS", "正在上传生成结果", 96, 0, 1);
            UploadResult uploadResult = runRenderStep(task, "RENDER_UPLOAD_OSS", "Render upload assets",
                    () -> uploadAssets(task, clipPlan, finalVideo, renderMedia.subtitleAssets, reporter));
            SubtitleAssets subtitleAssets = uploadResult.subtitleAssets;
            String outputUrl = uploadResult.outputUrl;
            TkRenderResult result = new TkRenderResult(outputUrl, subtitleAssets.assUrl, subtitleAssets.timelineUrl,
                    subtitleAssets.visualAnalysisUrl, subtitleAssets.layoutUrl, subtitleAssets.assUrl,
                    subtitleAssets.asrRawUrl, subtitleAssets.qualityUrl);
            completed = true;
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("视频渲染失败：" + ex.getMessage(), ex);
        } finally {
            if (completed) {
                FileUtil.del(taskDir);
            }
        }
    }

    private File prepareVoiceAudio(File taskDir, String audioUrl) throws Exception {
        File sourceAudio = download(audioUrl, new File(taskDir, "voice-source.media"));
        File normalizedAudio = new File(taskDir, "voice.wav");
        runCommand(Arrays.asList(ffmpeg(), "-y", "-i", sourceAudio.getAbsolutePath(),
                "-vn", "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le", normalizedAudio.getAbsolutePath()));
        return normalizedAudio;
    }

    private SubtitleAssets buildSubtitleAssets(File taskDir, TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan,
                                               File videoFile, File audioFile) {
        SubtitleAssets assets = new SubtitleAssets();
        if (Boolean.FALSE.equals(task.getSubtitleEnabled()) || audioFile == null) {
            return assets;
        }
        List<String> keywords = keywordHighlightService.resolveKeywords(task, task.getScriptText());
        assets.timeline = buildSubtitleTimeline(task, audioFile, keywords);
        assets.visualAnalysis = visualAnalysisService.analyze(videoFile, clipPlan);
        assets.layout = subtitleLayoutService.layout(task, assets.timeline, assets.visualAnalysis);
        assets.assFile = assSubtitleRenderService.render(task, assets.layout, new File(taskDir, "subtitle.ass"));
        return assets;
    }

    TkSubtitleTimeline buildSubtitleTimeline(TkGenerationTaskDO task, File audioFile, List<String> keywords) {
        String narrationScript = TkNativeOpeningSupport.resolveNarrationScript(
                task.getScriptText(), task.getSegmentTimeline(), task.getOpeningProcessMode());
        TkSubtitleTimeline timeline = subtitleTimelineService.buildTimeline(task, narrationScript, audioFile, keywords);
        TkNativeOpeningSupport.shiftTimeline(timeline, nativeOpeningDurationSeconds(task));
        return timeline;
    }

    static List<String> buildNormalizeFullSourceCommand(String ffmpeg, File source, File segment) {
        return buildNormalizeFullSourceCommand(ffmpeg, source, segment, "veryfast");
    }

    static List<String> buildNormalizeFullSourceCommand(String ffmpeg, File source, File segment, String preset) {
        return Arrays.asList(ffmpeg, "-y", "-i", source.getAbsolutePath(),
                "-vf", NORMALIZE_VIDEO_FILTER,
                "-r", "30", "-an", "-c:v", "libx264", "-preset", normalizePreset(preset), segment.getAbsolutePath());
    }

    static List<String> buildNormalizeClipCommand(String ffmpeg, File source, File segment,
                                                   double startSeconds, double durationSeconds,
                                                   String preset, boolean useVariant) {
        return buildAdaptClipCommand(ffmpeg, source, segment, startSeconds, durationSeconds, durationSeconds, preset);
    }

    static List<String> buildAdaptClipCommand(String ffmpeg, File source, File segment,
                                               double startSeconds, double sourceDuration,
                                               double targetDuration, String preset) {
        String videoFilter = NORMALIZE_VIDEO_FILTER;
        if (Math.abs(sourceDuration - targetDuration) > SECTION_COMPRESS_EPSILON_SECONDS) {
            videoFilter += "," + TkRenderMediaSupport.buildVideoSpeedFilter(sourceDuration, targetDuration);
        }
        return Arrays.asList(ffmpeg, "-y", "-ss", formatDecimal(Math.max(0D, startSeconds)),
                "-t", formatDecimal(Math.max(0.001D, sourceDuration)), "-i", source.getAbsolutePath(),
                "-vf", videoFilter,
                "-r", "30", "-an", "-c:v", "libx264", "-preset", normalizePreset(preset), segment.getAbsolutePath());
    }

    static List<String> buildNativeOpeningClipCommand(String ffmpeg, File source, File segment,
                                                       double startSeconds, double durationSeconds,
                                                       String preset, boolean hasSourceAudio) {
        List<String> command = new ArrayList<>(Arrays.asList(ffmpeg, "-y",
                "-ss", formatDecimal(Math.max(0D, startSeconds)),
                "-t", formatDecimal(Math.max(0.001D, durationSeconds)),
                "-i", source.getAbsolutePath()));
        if (!hasSourceAudio) {
            command.addAll(Arrays.asList("-f", "lavfi", "-t", formatDecimal(Math.max(0.001D, durationSeconds)),
                    "-i", "anullsrc=channel_layout=stereo:sample_rate=44100"));
        }
        command.addAll(Arrays.asList("-vf", NORMALIZE_VIDEO_FILTER,
                "-map", "0:v:0", "-map", hasSourceAudio ? "0:a:0" : "1:a:0",
                "-r", "30", "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                "-t", formatDecimal(Math.max(0.001D, durationSeconds)), segment.getAbsolutePath()));
        return command;
    }

    static List<String> buildSilentBodyClipCommand(String ffmpeg, File source, File segment,
                                                    double startSeconds, double sourceDuration,
                                                    double targetDuration, String preset) {
        String videoFilter = NORMALIZE_VIDEO_FILTER;
        if (Math.abs(sourceDuration - targetDuration) > SECTION_COMPRESS_EPSILON_SECONDS) {
            videoFilter += "," + TkRenderMediaSupport.buildVideoSpeedFilter(sourceDuration, targetDuration);
        }
        return Arrays.asList(ffmpeg, "-y", "-ss", formatDecimal(Math.max(0D, startSeconds)),
                "-t", formatDecimal(Math.max(0.001D, sourceDuration)), "-i", source.getAbsolutePath(),
                "-f", "lavfi", "-t", formatDecimal(Math.max(0.001D, targetDuration)),
                "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
                "-vf", videoFilter, "-map", "0:v:0", "-map", "1:a:0",
                "-r", "30", "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                "-t", formatDecimal(Math.max(0.001D, targetDuration)), segment.getAbsolutePath());
    }

    static List<String> buildCompressSectionCommand(String ffmpeg, File source, File segment,
                                                    double sourceDuration, double targetDuration) {
        return buildCompressSectionCommand(ffmpeg, source, segment, sourceDuration, targetDuration, "veryfast");
    }

    static List<String> buildCompressSectionCommand(String ffmpeg, File source, File segment,
                                                    double sourceDuration, double targetDuration, String preset) {
        return Arrays.asList(ffmpeg, "-y", "-i", source.getAbsolutePath(),
                "-vf", NORMALIZE_VIDEO_FILTER + "," + TkRenderMediaSupport.buildVideoSpeedFilter(sourceDuration, targetDuration),
                "-r", "30", "-an", "-c:v", "libx264", "-preset", normalizePreset(preset), segment.getAbsolutePath());
    }

    static List<String> buildDurationCorrectionCommand(String ffmpeg, File source, File output,
                                                        double sourceDuration, double targetDuration, String preset) {
        return Arrays.asList(ffmpeg, "-y", "-i", source.getAbsolutePath(),
                "-vf", NORMALIZE_VIDEO_FILTER + "," + TkRenderMediaSupport.buildVideoSpeedFilter(sourceDuration, targetDuration),
                "-r", "30", "-an", "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-t", formatDecimal(targetDuration), output.getAbsolutePath());
    }

    static List<String> buildNativeDurationPadCommand(String ffmpeg, File source, File output,
                                                       double sourceDuration, double targetDuration,
                                                       String preset) {
        double padDuration = Math.max(0D, targetDuration - sourceDuration);
        return Arrays.asList(ffmpeg, "-y", "-i", source.getAbsolutePath(),
                "-vf", "tpad=stop_mode=clone:stop_duration=" + formatDecimal(padDuration),
                "-af", "apad", "-map", "0:v:0", "-map", "0:a:0",
                "-r", "30", "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                "-t", formatDecimal(targetDuration), output.getAbsolutePath());
    }

    static List<String> buildPadSectionCommand(String ffmpeg, File source, File segment,
                                               double sourceDuration, double targetDuration) {
        return buildPadSectionCommand(ffmpeg, source, segment, sourceDuration, targetDuration, "veryfast");
    }

    static List<String> buildPadSectionCommand(String ffmpeg, File source, File segment,
                                               double sourceDuration, double targetDuration, String preset) {
        return buildCompressSectionCommand(ffmpeg, source, segment, sourceDuration, targetDuration, preset);
    }

    static List<String> buildFinalRenderCommand(String ffmpeg, File mergedVideo, File finalAudioFile,
                                                File bgmFile, Double bgmVolume, double videoDuration,
                                                File assFile, File finalVideo) {
        return buildFinalRenderCommand(ffmpeg, mergedVideo, finalAudioFile, bgmFile, bgmVolume, videoDuration,
                assFile, finalVideo, "veryfast", 0D);
    }

    static List<String> buildFinalRenderCommand(String ffmpeg, File mergedVideo, File finalAudioFile,
                                                File bgmFile, Double bgmVolume, double videoDuration,
                                                File assFile, File finalVideo, String preset) {
        return buildFinalRenderCommand(ffmpeg, mergedVideo, finalAudioFile, bgmFile, bgmVolume, videoDuration,
                assFile, finalVideo, preset, 0D);
    }

    static List<String> buildFinalRenderCommand(String ffmpeg, File mergedVideo, File finalAudioFile,
                                                File bgmFile, Double bgmVolume, double videoDuration,
                                                File assFile, File finalVideo, String preset,
                                                double nativeOpeningDuration) {
        List<String> renderCommand = new ArrayList<>(Arrays.asList(ffmpeg, "-y", "-i", mergedVideo.getAbsolutePath()));
        boolean hasVoice = finalAudioFile != null;
        if (hasVoice) {
            renderCommand.addAll(Arrays.asList("-i", finalAudioFile.getAbsolutePath()));
        }
        if (bgmFile != null) {
            renderCommand.addAll(Arrays.asList("-stream_loop", "-1", "-i", bgmFile.getAbsolutePath()));
        }
        if (assFile != null) {
            renderCommand.add("-vf");
            renderCommand.add("ass=" + escapeSubtitlePath(assFile));
        }
        if (nativeOpeningDuration > SECTION_COMPRESS_EPSILON_SECONDS) {
            renderCommand.addAll(Arrays.asList("-filter_complex",
                    buildNativeAudioMixFilter(bgmVolume, videoDuration, nativeOpeningDuration, hasVoice,
                            bgmFile != null),
                    "-map", "0:v:0", "-map", "[aout]",
                    "-c:v", "libx264", "-preset", normalizePreset(preset),
                    "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                    "-t", formatDecimal(videoDuration), finalVideo.getAbsolutePath()));
            return renderCommand;
        }
        if (hasVoice && bgmFile == null) {
            renderCommand.addAll(Arrays.asList("-map", "0:v:0", "-map", "1:a:0",
                    "-c:v", "libx264", "-preset", normalizePreset(preset),
                    "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                    "-af", FINAL_AUDIO_FILTER,
                    "-t", formatDecimal(videoDuration), finalVideo.getAbsolutePath()));
            return renderCommand;
        }
        if (hasVoice) {
            renderCommand.addAll(Arrays.asList("-filter_complex", buildBgmMixFilter(bgmVolume, videoDuration),
                    "-map", "0:v:0", "-map", "[aout]",
                    "-c:v", "libx264", "-preset", normalizePreset(preset),
                    "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                    "-t", formatDecimal(videoDuration), finalVideo.getAbsolutePath()));
            return renderCommand;
        }
        if (bgmFile != null) {
            renderCommand.addAll(Arrays.asList("-filter_complex", buildBgmOnlyFilter(bgmVolume, videoDuration),
                "-map", "0:v:0", "-map", "[aout]",
                "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-c:a", "aac", "-ar", "44100", "-ac", "2", "-b:a", "192k",
                "-t", formatDecimal(videoDuration), finalVideo.getAbsolutePath()));
            return renderCommand;
        }
        renderCommand.addAll(Arrays.asList("-map", "0:v:0",
                "-c:v", "libx264", "-preset", normalizePreset(preset),
                "-an", "-t", formatDecimal(videoDuration), finalVideo.getAbsolutePath()));
        return renderCommand;
    }

    private static String buildNativeAudioMixFilter(Double bgmVolume, double videoDuration,
                                                     double nativeOpeningDuration, boolean hasVoice,
                                                     boolean hasBgm) {
        double openingDuration = Math.max(0D, nativeOpeningDuration);
        double delayMillis = Math.max(0L, Math.round(openingDuration * 1000D));
        double bodyDuration = Math.max(0D, videoDuration - openingDuration);
        double fadeOutStart = Math.max(0D, bodyDuration - 1D);
        List<String> inputs = new ArrayList<>();
        StringBuilder filter = new StringBuilder()
                .append("[0:a]atrim=duration=").append(formatDecimal(openingDuration))
                .append(",asetpts=PTS-STARTPTS[opening]");
        if (hasVoice) {
            filter.append(';')
                    .append("[1:a]").append(FINAL_AUDIO_FILTER)
                    .append(",adelay=").append(Math.round(delayMillis)).append('|').append(Math.round(delayMillis))
                    .append(",asetpts=PTS-STARTPTS[voice]");
            inputs.add("[voice]");
        }
        if (hasBgm) {
            int bgmInputIndex = hasVoice ? 2 : 1;
            filter.append(';')
                    .append('[').append(bgmInputIndex).append(":a]volume=")
                    .append(formatDecimal(normalizeBgmVolume(bgmVolume)))
                    .append(",afade=t=in:ss=0:d=1")
                    .append(",afade=t=out:st=").append(formatDecimal(fadeOutStart)).append(":d=1")
                    .append(",atrim=duration=").append(formatDecimal(bodyDuration))
                    .append(",adelay=").append(Math.round(delayMillis)).append('|').append(Math.round(delayMillis))
                    .append(",asetpts=PTS-STARTPTS[bgm]");
            inputs.add("[bgm]");
        }
        if (inputs.isEmpty()) {
            return filter.append(";[opening]apad,atrim=duration=")
                    .append(formatDecimal(videoDuration)).append(",asetpts=PTS-STARTPTS[aout]").toString();
        }
        filter.append(';').append("[opening]").append(String.join("", inputs))
                .append("amix=inputs=").append(inputs.size() + 1)
                .append(":duration=longest:dropout_transition=0,atrim=duration=")
                .append(formatDecimal(videoDuration)).append(",asetpts=PTS-STARTPTS[aout]");
        return filter.toString();
    }

    private File ensureTargetDuration(TkGenerationTaskDO task, File taskDir, File mergedVideo) throws Exception {
        double targetDuration = TkVideoDurationSupport.normalize(task.getTargetDuration());
        double mergedDuration = probeDuration(mergedVideo);
        if (mergedDuration <= 0D || mergedDuration + SECTION_COMPRESS_EPSILON_SECONDS >= targetDuration) {
            return mergedVideo;
        }
        File adjustedVideo = new File(taskDir, "merged-video-duration-adjusted.mp4");
        log.info("[ensureTargetDuration][taskId({}) targetDuration({}) mergedDuration({})]",
                task.getId(), targetDuration, mergedDuration);
        if (TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())) {
            runCommand(buildNativeDurationPadCommand(ffmpeg(), mergedVideo, adjustedVideo,
                    mergedDuration, targetDuration, ffmpegPreset()));
        } else {
            runCommand(buildDurationCorrectionCommand(ffmpeg(), mergedVideo, adjustedVideo,
                    mergedDuration, targetDuration, ffmpegPreset()));
        }
        return adjustedVideo;
    }

    private static String buildBgmMixFilter(Double bgmVolume, double videoDuration) {
        double volume = normalizeBgmVolume(bgmVolume);
        double fadeOutStart = Math.max(0D, videoDuration - 1D);
        return StrUtil.format("[1:a]{}[voice];[2:a]volume={},afade=t=in:ss=0:d=1,afade=t=out:st={}:d=1[bgm];"
                        + "[voice][bgm]amix=inputs=2:duration=first:dropout_transition=0[aout]",
                FINAL_AUDIO_FILTER, formatDecimal(volume), formatDecimal(fadeOutStart));
    }

    private static String buildBgmOnlyFilter(Double bgmVolume, double videoDuration) {
        double volume = normalizeBgmVolume(bgmVolume);
        double fadeOutStart = Math.max(0D, videoDuration - 1D);
        return StrUtil.format("[1:a]volume={},afade=t=in:ss=0:d=1,afade=t=out:st={}:d=1[aout]",
                formatDecimal(volume), formatDecimal(fadeOutStart));
    }

    private static double normalizeBgmVolume(Double bgmVolume) {
        if (bgmVolume == null) {
            return 0.10D;
        }
        return Math.max(0.01D, Math.min(0.30D, bgmVolume));
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private <T> T runRenderStep(TkGenerationTaskDO task, String stepCode, String stepName,
                                RenderStep<T> step) throws Exception {
        LocalDateTime startTime = LocalDateTime.now();
        try {
            T result = step.execute();
            recordRenderStep(task, stepCode, stepName, startTime, "SUCCESS", null);
            return result;
        } catch (Exception ex) {
            recordRenderStep(task, stepCode, stepName, startTime, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private void recordRenderStep(TkGenerationTaskDO task, String stepCode, String stepName,
                                  LocalDateTime startTime, String status, String failReason) {
        if (stepLogMapper == null || task == null || task.getId() == null) {
            return;
        }
        LocalDateTime endTime = LocalDateTime.now();
        TkGenerationStepLogDO log = TkGenerationStepLogDO.builder()
                .taskId(task.getId())
                .batchId(task.getBatchId())
                .stepCode(stepCode)
                .stepName(stepName)
                .status(status)
                .startTime(startTime)
                .endTime(endTime)
                .durationMillis(Math.max(0L, Duration.between(startTime, endTime).toMillis()))
                .failReason(failReason)
                .retryCount(task.getRetryCount() == null ? 0 : task.getRetryCount())
                .build();
        log.setTenantId(task.getTenantId());
        stepLogMapper.insert(log);
    }

    private List<File> buildSegments(TkGenerationTaskDO task, File taskDir, List<TkClipPlanItem> clipPlan, Map<String, File> sourceCache,
                                     TkRenderProgressReporter reporter) throws Exception {
        List<ClipSpec> clipSpecs = new ArrayList<>();
        Map<String, Double> durationCache = new LinkedHashMap<>();
        boolean nativeOpeningMode = TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode());
        for (StageGroup group : groupSections(clipPlan)) {
            List<ClipSpec> groupSpecs = new ArrayList<>();
            double groupTargetDuration = group.targetDuration();
            double groupDuration = 0D;
            for (TkClipPlanItem item : group.items) {
                ClipSpec spec = resolveClipSpec(taskDir, item, sourceCache, durationCache);
                if (spec == null) {
                    continue;
                }
                if (groupTargetDuration > 0D) {
                    double remaining = groupTargetDuration - groupDuration;
                    if (remaining <= 0.001D) {
                        break;
                    }
                    spec = spec.withDuration(Math.min(spec.durationSeconds, remaining));
                }
                groupSpecs.add(spec);
                groupDuration += spec.durationSeconds;
            }
            adaptClipSpecs(groupSpecs, groupTargetDuration);
            clipSpecs.addAll(groupSpecs);
        }
        if (clipSpecs.isEmpty()) {
            throw new IllegalStateException("没有可用于生成视频的有效素材");
        }
        adaptClipSpecs(clipSpecs, TkVideoDurationSupport.normalize(task.getTargetDuration()));
        List<File> segments = new ArrayList<>();
        int index = 1;
        int total = clipSpecs.size();
        for (ClipSpec spec : clipSpecs) {
            File segment = new File(taskDir, StrUtil.format("clip-{}.mp4", index++));
            if (spec.nativeOpening) {
                runCommand(buildNativeOpeningClipCommand(ffmpeg(), spec.source, segment, spec.startSeconds,
                        spec.durationSeconds, ffmpegPreset(), probeHasAudio(spec.source)));
            } else if (nativeOpeningMode) {
                runCommand(buildSilentBodyClipCommand(ffmpeg(), spec.source, segment, spec.startSeconds,
                        spec.sourceDurationSeconds, spec.durationSeconds, ffmpegPreset()));
            } else {
                runCommand(buildAdaptClipCommand(ffmpeg(), spec.source, segment, spec.startSeconds,
                        spec.sourceDurationSeconds, spec.durationSeconds, ffmpegPreset()));
            }
            segments.add(segment);
            reporter.report("RENDER_TRANSCODE_SEGMENTS", "正在转码拼接素材",
                    TkGenerationProgressSupport.stageProgress(72, 88, segments.size(), total), segments.size(), total);
        }
        return segments;
    }

    private ClipSpec resolveClipSpec(File taskDir, TkClipPlanItem item, Map<String, File> sourceCache,
                                     Map<String, Double> durationCache) throws Exception {
        if (item == null || StrUtil.isBlank(item.getFileUrl())) {
            return null;
        }
        File source = downloadCached(item.getFileUrl(), taskDir, item.getFileName(), sourceCache);
        Double sourceDurationValue = durationCache.get(source.getAbsolutePath());
        if (sourceDurationValue == null) {
            sourceDurationValue = probeDuration(source);
            durationCache.put(source.getAbsolutePath(), sourceDurationValue);
        }
        double sourceDuration = sourceDurationValue;
        double startSeconds = resolveStartSeconds(item);
        double plannedDuration = resolvePlannedDurationSeconds(item);
        double usableDuration = Math.max(0D, sourceDuration - startSeconds);
        double durationSeconds = Math.min(plannedDuration, usableDuration);
        if (durationSeconds <= 0.001D) {
            return null;
        }
        boolean nativeOpening = "NATIVE".equalsIgnoreCase(StrUtil.trimToEmpty(item.getReuseMode()));
        return new ClipSpec(source, startSeconds, durationSeconds, durationSeconds, nativeOpening);
    }

    private void adaptClipSpecs(List<ClipSpec> clipSpecs, double targetDuration) {
        double renderedDuration = clipSpecs.stream().mapToDouble(spec -> spec.durationSeconds).sum();
        if (renderedDuration + SECTION_COMPRESS_EPSILON_SECONDS >= targetDuration || clipSpecs.isEmpty()) {
            return;
        }
        double fixedDuration = clipSpecs.stream()
                .filter(spec -> spec.nativeOpening)
                .mapToDouble(spec -> spec.durationSeconds)
                .sum();
        double adaptableDuration = renderedDuration - fixedDuration;
        if (adaptableDuration <= SECTION_COMPRESS_EPSILON_SECONDS) {
            return;
        }
        double scale = Math.max(0D, targetDuration - fixedDuration) / adaptableDuration;
        for (int index = 0; index < clipSpecs.size(); index++) {
            ClipSpec spec = clipSpecs.get(index);
            if (!spec.nativeOpening) {
                clipSpecs.set(index, spec.withDuration(spec.durationSeconds * scale));
            }
        }
    }

    private double resolveStartSeconds(TkClipPlanItem item) {
        if (item.getStartMillis() != null && item.getStartMillis() >= 0L) {
            return item.getStartMillis() / 1000D;
        }
        return item.getStartSecond() == null ? 0D : Math.max(0D, item.getStartSecond());
    }

    private double resolvePlannedDurationSeconds(TkClipPlanItem item) {
        if (item.getDurationMillis() != null && item.getDurationMillis() > 0L) {
            return item.getDurationMillis() / 1000D;
        }
        return item.getDurationSecond() == null ? 0D : Math.max(0D, item.getDurationSecond());
    }

    private File buildSectionSegment(File taskDir, StageGroup group, int sectionIndex,
                                     Map<String, File> sourceCache) throws Exception {
        List<File> normalizedSources = new ArrayList<>();
        int sourceIndex = 1;
        for (TkClipPlanItem item : group.items) {
            File source = downloadCached(item.getFileUrl(), taskDir, item.getFileName(), sourceCache);
            File normalized = new File(taskDir, StrUtil.format("section-{}-source-{}.mp4", sectionIndex, sourceIndex++));
            runCommand(buildNormalizeFullSourceCommand(ffmpeg(), source, normalized, ffmpegPreset()));
            normalizedSources.add(normalized);
        }
        File rawSection;
        if (normalizedSources.size() == 1) {
            rawSection = normalizedSources.get(0);
        } else {
            File sectionConcatList = writeConcatList(taskDir, normalizedSources,
                    StrUtil.format("section-{}-concat.txt", sectionIndex));
            rawSection = new File(taskDir, StrUtil.format("section-{}-raw.mp4", sectionIndex));
            runCommand(Arrays.asList(ffmpeg(), "-y", "-f", "concat", "-safe", "0", "-i",
                    sectionConcatList.getAbsolutePath(), "-c", "copy", rawSection.getAbsolutePath()));
        }
        double targetDuration = group.targetDuration();
        if (targetDuration <= 0D) {
            return rawSection;
        }
        double sectionDuration = probeDuration(rawSection);
        if (sectionDuration > targetDuration + SECTION_COMPRESS_EPSILON_SECONDS) {
            File compressedSection = new File(taskDir, StrUtil.format("section-{}.mp4", sectionIndex));
            runCommand(buildCompressSectionCommand(ffmpeg(), rawSection, compressedSection, sectionDuration, targetDuration,
                    ffmpegPreset()));
            return compressedSection;
        }
        if (sectionDuration + SECTION_COMPRESS_EPSILON_SECONDS < targetDuration) {
            File paddedSection = new File(taskDir, StrUtil.format("section-{}.mp4", sectionIndex));
            runCommand(buildPadSectionCommand(ffmpeg(), rawSection, paddedSection, sectionDuration, targetDuration,
                    ffmpegPreset()));
            return paddedSection;
        }
        return rawSection;
    }

    private Void prefetchSourceFiles(File taskDir, List<TkClipPlanItem> clipPlan, Map<String, File> sourceCache,
                                     TkRenderProgressReporter reporter) throws Exception {
        Map<String, TkClipPlanItem> uniqueItems = new LinkedHashMap<>();
        for (TkClipPlanItem item : clipPlan) {
            if (item != null && StrUtil.isNotBlank(item.getFileUrl())) {
                uniqueItems.putIfAbsent(item.getFileUrl(), item);
            }
        }
        if (uniqueItems.isEmpty()) {
            return null;
        }
        int parallelism = Math.min(uniqueItems.size(), Math.max(1,
                defaultInt(generationProperties.getRenderDownload().getMaxParallelDownloads(), 3)));
        int completed = 0;
        int total = uniqueItems.size();
        if (parallelism <= 1) {
            for (TkClipPlanItem item : uniqueItems.values()) {
                downloadCached(item.getFileUrl(), taskDir, item.getFileName(), sourceCache);
                completed++;
                reporter.report("RENDER_DOWNLOAD", "正在下载素材",
                        TkGenerationProgressSupport.stageProgress(66, 72, completed, total), completed, total);
            }
            return null;
        }
        ExecutorService executorService = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (TkClipPlanItem item : uniqueItems.values()) {
                futures.add(executorService.submit(() ->
                        downloadCached(item.getFileUrl(), taskDir, item.getFileName(), sourceCache)));
            }
            for (Future<?> future : futures) {
                future.get();
                completed++;
                reporter.report("RENDER_DOWNLOAD", "正在下载素材",
                        TkGenerationProgressSupport.stageProgress(66, 72, completed, total), completed, total);
            }
            return null;
        } finally {
            executorService.shutdownNow();
        }
    }

    private UploadResult uploadAssets(TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan,
                                      File finalVideo, SubtitleAssets subtitleAssets,
                                      TkRenderProgressReporter reporter) {
        String outputUrl = generationOutputStorageService.uploadGeneratedAsset(task, finalVideo,
                StrUtil.format("generated-{}.mp4", task.getId()), "video/mp4");
        generationOutputStorageService.uploadGeneratedAsset(task,
                JsonUtils.toJsonString(clipPlan).getBytes(StandardCharsets.UTF_8),
                StrUtil.format("clip-plan-{}.json", task.getId()), "application/json");
        if (subtitleAssets.timeline != null) {
            subtitleAssets.timelineUrl = generationOutputStorageService.uploadGeneratedAsset(task,
                    JsonUtils.toJsonString(subtitleAssets.timeline).getBytes(StandardCharsets.UTF_8),
                    StrUtil.format("subtitle-timeline-{}.json", task.getId()), "application/json");
        }
        if (subtitleAssets.visualAnalysis != null) {
            subtitleAssets.visualAnalysisUrl = generationOutputStorageService.uploadGeneratedAsset(task,
                    JsonUtils.toJsonString(subtitleAssets.visualAnalysis).getBytes(StandardCharsets.UTF_8),
                    StrUtil.format("subtitle-visual-{}.json", task.getId()), "application/json");
        }
        if (subtitleAssets.layout != null) {
            subtitleAssets.layoutUrl = generationOutputStorageService.uploadGeneratedAsset(task,
                    JsonUtils.toJsonString(subtitleAssets.layout).getBytes(StandardCharsets.UTF_8),
                    StrUtil.format("subtitle-layout-{}.json", task.getId()), "application/json");
        }
        if (subtitleAssets.assFile != null) {
            subtitleAssets.assUrl = generationOutputStorageService.uploadGeneratedAsset(task, subtitleAssets.assFile,
                    StrUtil.format("subtitle-{}.ass", task.getId()), "text/x-ssa");
        }
        File asrRawFile = new File(finalVideo.getParentFile(), "asr-raw.json");
        if (asrRawFile.isFile()) {
            subtitleAssets.asrRawUrl = generationOutputStorageService.uploadGeneratedAsset(task, asrRawFile,
                    StrUtil.format("subtitle-asr-raw-{}.json", task.getId()), "application/json");
        }
        File subtitleQualityFile = new File(finalVideo.getParentFile(), "subtitle-quality.json");
        if (subtitleQualityFile.isFile()) {
            subtitleAssets.qualityUrl = generationOutputStorageService.uploadGeneratedAsset(task, subtitleQualityFile,
                    StrUtil.format("subtitle-quality-{}.json", task.getId()), "application/json");
        }
        reporter.report("RENDER_UPLOAD_OSS", "正在上传生成结果", 99, 1, 1);
        return new UploadResult(outputUrl, subtitleAssets);
    }

    private List<StageGroup> groupSections(List<TkClipPlanItem> clipPlan) {
        List<StageGroup> groups = new ArrayList<>();
        StageGroup current = null;
        for (TkClipPlanItem item : clipPlan) {
            if (current == null || !current.matches(item)) {
                current = new StageGroup(item);
                groups.add(current);
            }
            current.items.add(item);
        }
        return groups;
    }

    private File resolveBgmFile(File taskDir, TkGenerationTaskDO task) {
        if (!Boolean.TRUE.equals(task.getBgmEnabled()) || StrUtil.isBlank(task.getBgmUrl())) {
            return null;
        }
        return download(task.getBgmUrl(), new File(taskDir, "bgm." + safeAudioExtension(task.getBgmUrl())));
    }

    private String safeAudioExtension(String url) {
        String cleanUrl = StrUtil.subBefore(StrUtil.blankToDefault(url, "bgm.mp3"), "?", false);
        String extension = StrUtil.blankToDefault(FileUtil.extName(cleanUrl), "mp3").toLowerCase(Locale.ROOT);
        return Arrays.asList("mp3", "wav", "m4a", "aac").contains(extension) ? extension : "mp3";
    }

    private File writeConcatList(File taskDir, List<File> segments) {
        return writeConcatList(taskDir, segments, "concat.txt");
    }

    private File writeConcatList(File taskDir, List<File> segments, String fileName) {
        File concatList = new File(taskDir, fileName);
        StringBuilder content = new StringBuilder();
        for (File segment : segments) {
            content.append("file '").append(segment.getAbsolutePath().replace("\\", "/").replace("'", "'\\''")).append("'\n");
        }
        FileUtil.writeUtf8String(content.toString(), concatList);
        return concatList;
    }

    private File download(String url, File target) {
        if (StrUtil.isBlank(url) || !(StrUtil.startWithIgnoreCase(url, "http://") || StrUtil.startWithIgnoreCase(url, "https://"))) {
            throw new IllegalStateException("文件 URL 不是可下载的 HTTP 地址：" + url);
        }
        TkGenerationProperties.RenderDownload renderDownload = generationProperties.getRenderDownload();
        String requestUrl = resolveDownloadUrl(url, renderDownload.getPublicBaseUrl(), renderDownload.getInternalBaseUrl());
        int timeoutMillis = Math.max(10, defaultInt(renderDownload.getTimeoutSeconds(), 180)) * 1000;
        int maxAttempts = Math.max(1, defaultInt(renderDownload.getMaxAttempts(), 3));
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (HttpResponse response = HttpRequest.get(requestUrl).timeout(timeoutMillis).execute()) {
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    throw new IllegalStateException(StrUtil.format("下载文件失败，HTTP {}：{}", response.getStatus(), sanitizeUrl(url)));
                }
                FileUtil.writeBytes(response.bodyBytes(), target);
                return target;
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt >= maxAttempts) {
                    break;
                }
                log.warn("[download][attempt({}/{}) target({}) url({}) resolvedUrl({}) failed]",
                        attempt, maxAttempts, target.getName(), sanitizeUrl(url), sanitizeUrl(requestUrl), ex);
                sleepBeforeRetry(renderDownload.getRetryDelayMillis());
            }
        }
        throw new IllegalStateException(StrUtil.format("下载文件超时或失败：{}", sanitizeUrl(url)), lastException);
    }

    static String resolveDownloadUrl(String url, String publicBaseUrl, String internalBaseUrl) {
        if (StrUtil.hasBlank(url, publicBaseUrl, internalBaseUrl)) {
            return url;
        }
        String normalizedPublicBaseUrl = StrUtil.removeSuffix(publicBaseUrl, "/");
        String normalizedInternalBaseUrl = StrUtil.removeSuffix(internalBaseUrl, "/");
        if (!StrUtil.startWithIgnoreCase(url, normalizedPublicBaseUrl)) {
            return url;
        }
        String remaining = url.substring(normalizedPublicBaseUrl.length());
        if (StrUtil.isNotEmpty(remaining) && !remaining.startsWith("/") && !remaining.startsWith("?")) {
            return url;
        }
        return normalizedInternalBaseUrl + remaining;
    }

    private static String sanitizeUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        int queryIndex = url.indexOf('?');
        return queryIndex >= 0 ? url.substring(0, queryIndex) + "?***" : url;
    }

    private static void sleepBeforeRetry(Integer retryDelayMillis) {
        int delayMillis = retryDelayMillis == null ? 0 : retryDelayMillis;
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("下载文件重试等待被中断", ex);
        }
    }

    private static int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private File downloadCached(String url, File taskDir, String fileName, Map<String, File> sourceCache) {
        File cached = sourceCache.get(url);
        if (cached != null && cached.exists() && cached.length() > 0) {
            return cached;
        }
        File target = new File(taskDir, TkRenderMediaSupport.sourceCacheFileName(url, fileName));
        if (target.exists() && target.length() > 0) {
            sourceCache.put(url, target);
            return target;
        }
        File downloaded = download(url, target);
        sourceCache.put(url, downloaded);
        return downloaded;
    }

    private double probeDuration(File file) throws Exception {
        String output = runCommandForOutput(Arrays.asList(ffprobe(), "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", file.getAbsolutePath()), 60);
        try {
            return Double.parseDouble(output.trim());
        } catch (NumberFormatException ex) {
            return 0D;
        }
    }

    private boolean probeHasAudio(File file) throws Exception {
        String output = runCommandForOutput(Arrays.asList(ffprobe(), "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=index", "-of", "csv=p=0", file.getAbsolutePath()), 60);
        return StrUtil.isNotBlank(output);
    }

    private double nativeOpeningDurationSeconds(TkGenerationTaskDO task) {
        if (task == null || !TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())) {
            return 0D;
        }
        if (task.getOpeningDurationMs() != null && task.getOpeningDurationMs() > 0L) {
            return task.getOpeningDurationMs() / 1000D;
        }
        return Math.min(3D, TkVideoDurationSupport.normalize(task.getTargetDuration()));
    }

    private void runCommand(List<String> command) throws Exception {
        File outputFile = File.createTempFile("tk-ffmpeg-", ".log");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("命令执行超时：" + String.join(" ", command));
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(FileUtil.readUtf8String(outputFile));
            }
        } finally {
            FileUtil.del(outputFile);
        }
    }

    private String runCommandForOutput(List<String> command, int timeoutSeconds) throws Exception {
        File outputFile = File.createTempFile("tk-ffmpeg-", ".log");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile)
                    .start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("Command timeout: " + String.join(" ", command));
            }
            String output = FileUtil.readUtf8String(outputFile);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(output);
            }
            return output;
        } finally {
            FileUtil.del(outputFile);
        }
    }

    private File resolveWorkDir(TkGenerationTaskDO task) {
        String workDir = generationProperties.getFfmpeg().getWorkDir();
        if (StrUtil.isBlank(workDir)) {
            workDir = System.getProperty("java.io.tmpdir") + "/tk-generation";
        }
        workDir = workDir.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        return new File(workDir, String.valueOf(task.getId()));
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private String ffmpegPreset() {
        return normalizePreset(generationProperties.getFfmpeg().getPreset());
    }

    private String safeName(String fileName) {
        return TkRenderMediaSupport.safeName(fileName);
    }

    private static String normalizePreset(String preset) {
        return StrUtil.blankToDefault(StrUtil.trim(preset), "veryfast");
    }

    private static String escapeSubtitlePath(File subtitleFile) {
        return subtitleFile.getAbsolutePath().replace("\\", "/").replace(":", "\\\\:");
    }

    @FunctionalInterface
    private interface RenderStep<T> {
        T execute() throws Exception;
    }

    private static class RenderMedia {

        private final File audioFile;
        private final SubtitleAssets subtitleAssets;

        private RenderMedia(File audioFile, SubtitleAssets subtitleAssets) {
            this.audioFile = audioFile;
            this.subtitleAssets = subtitleAssets;
        }

    }

    private static class UploadResult {

        private final String outputUrl;
        private final SubtitleAssets subtitleAssets;

        private UploadResult(String outputUrl, SubtitleAssets subtitleAssets) {
            this.outputUrl = outputUrl;
            this.subtitleAssets = subtitleAssets;
        }

    }

    private static class ClipSpec {

        private final File source;
        private final double startSeconds;
        private final double sourceDurationSeconds;
        private final double durationSeconds;
        private final boolean nativeOpening;

        private ClipSpec(File source, double startSeconds, double sourceDurationSeconds,
                         double durationSeconds, boolean nativeOpening) {
            this.source = source;
            this.startSeconds = startSeconds;
            this.sourceDurationSeconds = sourceDurationSeconds;
            this.durationSeconds = durationSeconds;
            this.nativeOpening = nativeOpening;
        }

        private ClipSpec withDuration(double durationSeconds) {
            return new ClipSpec(source, startSeconds, sourceDurationSeconds, durationSeconds, nativeOpening);
        }

    }

    private static class StageGroup {

        private final String section;
        private final Integer sectionOrder;
        private final Integer sectionTargetSecond;
        private final List<TkClipPlanItem> items = new ArrayList<>();

        private StageGroup(TkClipPlanItem firstItem) {
            this.section = firstItem.getSection();
            this.sectionOrder = firstItem.getSectionOrder();
            this.sectionTargetSecond = firstItem.getSectionTargetSecond();
        }

        private boolean matches(TkClipPlanItem item) {
            return StrUtil.equals(section, item.getSection())
                    && java.util.Objects.equals(sectionOrder, item.getSectionOrder());
        }

        private double targetDuration() {
            if (sectionTargetSecond != null && sectionTargetSecond > 0) {
                return sectionTargetSecond;
            }
            return items.stream()
                    .mapToDouble(item -> item.getDurationMillis() != null && item.getDurationMillis() > 0L
                            ? item.getDurationMillis() / 1000D
                            : item.getDurationSecond() == null ? 0D : Math.max(0D, item.getDurationSecond()))
                    .sum();
        }

    }

    private static class SubtitleAssets {

        private TkSubtitleTimeline timeline;
        private TkVisualAnalysis visualAnalysis;
        private TkSubtitleLayout layout;
        private File assFile;
        private String timelineUrl;
        private String visualAnalysisUrl;
        private String layoutUrl;
        private String assUrl;
        private String asrRawUrl;
        private String qualityUrl;
    }

}
