package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkOpenVideoTranscriptTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkOpenVideoTranscriptTaskMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkOpenVideoTranscriptExtractServiceImpl implements TkOpenVideoTranscriptExtractService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ASR_PROVIDER = "FASTER_WHISPER";
    private static final int MAX_VIDEO_DURATION_SECONDS = 600;

    private final ExecutorService executorService = Executors.newFixedThreadPool(1);

    @Resource
    private TkOpenVideoTranscriptTaskMapper transcriptTaskMapper;
    @Resource
    private TkReferenceVideoContentService referenceVideoContentService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private FileApi fileApi;

    @Override
    public TkOpenVideoTranscriptExtractCreateRespVO createExtractTask(TkOpenVideoTranscriptExtractCreateReqVO reqVO) {
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .sourceUrl(StrUtil.trim(reqVO.getSourceUrl()))
                .sourceUrlHash(DigestUtil.sha256Hex(StrUtil.trim(reqVO.getSourceUrl())))
                .targetLanguage(StrUtil.trimToNull(reqVO.getTargetLanguage()))
                .status(STATUS_PENDING)
                .asrProvider(ASR_PROVIDER)
                .build();
        transcriptTaskMapper.insert(task);
        executorService.execute(() -> runExtractTask(task.getId()));
        return TkOpenVideoTranscriptExtractCreateRespVO.builder()
                .taskId(task.getId())
                .status(STATUS_PENDING)
                .build();
    }

    @Override
    public TkOpenVideoTranscriptExtractRespVO getExtractTask(Long taskId) {
        TkOpenVideoTranscriptTaskDO task = transcriptTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }
        return TkOpenVideoTranscriptExtractRespVO.builder()
                .taskId(task.getId())
                .status(task.getStatus())
                .failReason(task.getFailReason())
                .sourceUrl(task.getSourceUrl())
                .targetLanguage(task.getTargetLanguage())
                .resolvedVideoUrl(task.getResolvedVideoUrl())
                .coverUrl(task.getCoverUrl())
                .videoDuration(task.getVideoDuration())
                .resolution(task.getResolution())
                .audioUrl(task.getAudioUrl())
                .audioDuration(task.getAudioDuration())
                .transcriptText(task.getTranscriptText())
                .segments(parseJsonArray(task.getSegmentsJson()))
                .words(parseJsonArray(task.getWordsJson()))
                .asrProvider(task.getAsrProvider())
                .asrModel(task.getAsrModel())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
    }

    private void runExtractTask(Long taskId) {
        File taskDir = null;
        try {
            TkOpenVideoTranscriptTaskDO task = transcriptTaskMapper.selectById(taskId);
            if (task == null) {
                return;
            }
            updateStatus(taskId, STATUS_PROCESSING, null);
            taskDir = FileUtil.mkdir(new File(resolveWorkDir(), "open-transcript-" + taskId + "-" + UUID.randomUUID()));

            TkReferenceVideoContent videoContent = referenceVideoContentService.analyze(task.getSourceUrl());
            Integer videoDuration = videoContent.getDurationSeconds() == null ? null : videoContent.getDurationSeconds().intValue();
            if (videoDuration != null && videoDuration > MAX_VIDEO_DURATION_SECONDS) {
                throw new IllegalStateException("视频时长超过 " + MAX_VIDEO_DURATION_SECONDS + " 秒，已拒绝提取");
            }
            transcriptTaskMapper.updateById(TkOpenVideoTranscriptTaskDO.builder()
                    .id(taskId)
                    .resolvedVideoUrl(videoContent.getResolvedVideoUrl())
                    .coverUrl(videoContent.getCoverUrl())
                    .videoDuration(videoDuration)
                    .resolution(videoContent.getResolution())
                    .build());

            File videoFile = download(videoContent.getResolvedVideoUrl(), new File(taskDir, "source-video.mp4"));
            File audioFile = extractAudio(videoFile, new File(taskDir, "audio.wav"));
            String audioUrl = uploadAudio(taskId, audioFile);
            AsrResult asrResult = runAsr(task, audioFile);
            transcriptTaskMapper.updateById(TkOpenVideoTranscriptTaskDO.builder()
                    .id(taskId)
                    .audioUrl(audioUrl)
                    .audioDuration(asrResult.audioDuration)
                    .status(STATUS_SUCCESS)
                    .failReason("")
                    .transcriptText(asrResult.transcriptText)
                    .segmentsJson(asrResult.segmentsJson)
                    .wordsJson(asrResult.wordsJson)
                    .asrProvider(ASR_PROVIDER)
                    .asrModel(asrResult.model)
                    .rawAsrResult(asrResult.rawJson)
                    .build());
        } catch (Exception ex) {
            log.warn("[runExtractTask][taskId({}) open video transcript extract failed]", taskId, ex);
            transcriptTaskMapper.updateById(TkOpenVideoTranscriptTaskDO.builder()
                    .id(taskId)
                    .status(STATUS_FAILED)
                    .failReason(StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "视频文案时间轴提取失败"), 1024))
                    .build());
        } finally {
            FileUtil.del(taskDir);
        }
    }

    private void updateStatus(Long taskId, String status, String failReason) {
        transcriptTaskMapper.updateById(TkOpenVideoTranscriptTaskDO.builder()
                .id(taskId)
                .status(status)
                .failReason(failReason)
                .build());
    }

    private File extractAudio(File videoFile, File audioFile) throws Exception {
        runCommand(Arrays.asList(ffmpeg(), "-y", "-i", videoFile.getAbsolutePath(),
                "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", audioFile.getAbsolutePath()),
                180);
        if (!audioFile.isFile() || audioFile.length() <= 0) {
            throw new IllegalStateException("FFmpeg 未生成有效音频文件");
        }
        return audioFile;
    }

    private String uploadAudio(Long taskId, File audioFile) throws Exception {
        return fileApi.createFile(Files.readAllBytes(audioFile.toPath()),
                "transcript-audio-" + taskId + ".wav",
                "tk/open-video-transcripts/" + taskId,
                "audio/wav");
    }

    private AsrResult runAsr(TkOpenVideoTranscriptTaskDO task, File audioFile) throws Exception {
        TkGenerationProperties.Asr asr = generationProperties.getSubtitle().getAsr();
        if (asr == null || !Boolean.TRUE.equals(asr.getEnabled())) {
            throw new IllegalStateException("ASR 未启用，无法提取视频口播文案和时间轴");
        }
        File scriptFile = resolvePath(asr.getScriptPath());
        if (!scriptFile.isFile()) {
            throw new IllegalStateException("ASR 脚本不存在：" + scriptFile.getAbsolutePath());
        }
        String model = StrUtil.blankToDefault(asr.getModel(), "small");
        List<String> command = new ArrayList<>(Arrays.asList(
                StrUtil.blankToDefault(asr.getPython(), "py"),
                scriptFile.getAbsolutePath(),
                "--audio", audioFile.getAbsolutePath(),
                "--language", StrUtil.blankToDefault(task.getTargetLanguage(), ""),
                "--text", "",
                "--keywords", "[]",
                "--model", model
        ));
        String output = runCommand(command, asr.getTimeoutSeconds());
        JsonNode root = JsonUtils.parseTree(output);
        JsonNode segments = root.path("segments");
        if (!segments.isArray() || segments.size() <= 0) {
            throw new IllegalStateException("ASR 未返回有效分段时间轴");
        }
        List<Map<String, Object>> words = new ArrayList<>();
        List<String> transcriptParts = new ArrayList<>();
        for (JsonNode segment : segments) {
            String text = segment.path("text").asText("");
            if (StrUtil.isNotBlank(text)) {
                transcriptParts.add(text.trim());
            }
            JsonNode segmentWords = segment.path("words");
            if (segmentWords.isArray()) {
                for (JsonNode word : segmentWords) {
                    words.add(JsonUtils.parseObject(word.toString(), Map.class));
                }
            }
        }
        return new AsrResult(
                root.path("audioDuration").asDouble(0D),
                String.join("\n", transcriptParts),
                segments.toString(),
                JsonUtils.toJsonString(words),
                model,
                output);
    }

    private File download(String url, File target) {
        if (StrUtil.isBlank(url) || !(StrUtil.startWithIgnoreCase(url, "http://")
                || StrUtil.startWithIgnoreCase(url, "https://"))) {
            throw new IllegalStateException("文件 URL 不是可下载的 HTTP 地址：" + url);
        }
        TkGenerationProperties.RenderDownload renderDownload = generationProperties.getRenderDownload();
        String requestUrl = resolveDownloadUrl(url, renderDownload.getPublicBaseUrl(), renderDownload.getInternalBaseUrl());
        int timeoutMillis = Math.max(10, renderDownload.getTimeoutSeconds() == null
                ? 180 : renderDownload.getTimeoutSeconds()) * 1000;
        try (HttpResponse response = HttpRequest.get(requestUrl).timeout(timeoutMillis).execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException("下载视频失败，HTTP " + response.getStatus());
            }
            FileUtil.writeBytes(response.bodyBytes(), target);
            return target;
        }
    }

    private String resolveDownloadUrl(String url, String publicBaseUrl, String internalBaseUrl) {
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

    private List<Map<String, Object>> parseJsonArray(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Map> items = JsonUtils.parseArray(json, Map.class);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map item : items) {
                result.add(new LinkedHashMap<>(item));
            }
            return result;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String runCommand(List<String> command, Integer timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ExecutorService outputExecutor = Executors.newSingleThreadExecutor();
        Future<String> outputFuture = outputExecutor.submit(() -> {
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            return output.toString();
        });
        try {
            boolean finished = process.waitFor(timeoutSeconds == null ? 300 : timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("外部命令执行超时");
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException("外部命令执行失败：" + StrUtil.maxLength(output, 512));
            }
            return output;
        } finally {
            outputExecutor.shutdownNow();
        }
    }

    private File resolvePath(String path) {
        if (StrUtil.isBlank(path)) {
            return new File("");
        }
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(System.getProperty("user.dir"), path);
    }

    private File resolveWorkDir() {
        String workDir = generationProperties == null || generationProperties.getFfmpeg() == null
                ? null : generationProperties.getFfmpeg().getWorkDir();
        return FileUtil.mkdir(StrUtil.blankToDefault(workDir, System.getProperty("java.io.tmpdir") + "/tk-generation"));
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

    private static class AsrResult {
        private final Double audioDuration;
        private final String transcriptText;
        private final String segmentsJson;
        private final String wordsJson;
        private final String model;
        private final String rawJson;

        private AsrResult(Double audioDuration, String transcriptText, String segmentsJson, String wordsJson,
                          String model, String rawJson) {
            this.audioDuration = audioDuration;
            this.transcriptText = transcriptText;
            this.segmentsJson = segmentsJson;
            this.wordsJson = wordsJson;
            this.model = model;
            this.rawJson = rawJson;
        }
    }

}
