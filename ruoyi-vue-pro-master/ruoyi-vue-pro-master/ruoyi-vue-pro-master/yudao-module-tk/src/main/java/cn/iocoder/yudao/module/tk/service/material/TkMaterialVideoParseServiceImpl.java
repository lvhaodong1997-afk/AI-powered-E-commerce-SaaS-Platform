package cn.iocoder.yudao.module.tk.service.material;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkMaterialVideoParseServiceImpl implements TkMaterialVideoParseService {

    private static final int FILE_DOWNLOAD_TIMEOUT_MILLIS = 120_000;
    private static final int PROCESS_TIMEOUT_MINUTES = 5;

    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private FileApi fileApi;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkLocalUploadStorageService localUploadStorageService;

    @Override
    public void submit(Long tenantId, Long videoId) {
        executorService.submit(() -> TenantUtils.execute(tenantId, () -> parse(videoId)));
    }

    private void parse(Long videoId) {
        try {
            TkMaterialVideoDO video = videoMapper.selectById(videoId);
            if (video == null) {
                return;
            }
            videoMapper.updateById(new TkMaterialVideoDO()
                    .setId(videoId)
                    .setStatus(TkMaterialVideoStatusEnum.PARSING)
                    .setFailReason(null));

            File taskDir = FileUtil.mkdir(resolveWorkDir(video));
            File source = resolveSource(video, new File(taskDir, safeName(video.getFileName())));
            VideoMetadata metadata = probe(source);
            String coverUrl = extractCover(video, source, taskDir);

            videoMapper.updateById(new TkMaterialVideoDO()
                    .setId(videoId)
                    .setDuration(metadata.durationSeconds)
                    .setDurationMs(metadata.durationMillis)
                    .setResolution(metadata.resolution)
                    .setCoverUrl(coverUrl)
                    .setStatus(TkMaterialVideoStatusEnum.AVAILABLE)
                    .setFailReason(null));
        } catch (Exception ex) {
            log.error("[parse][videoId({}) 素材视频解析失败]", videoId, ex);
            videoMapper.updateById(new TkMaterialVideoDO()
                    .setId(videoId)
                    .setStatus(TkMaterialVideoStatusEnum.FAILED)
                    .setFailReason(StrUtils.maxLength(normalizeFailReason(ex), 512)));
        }
    }

    private String normalizeFailReason(Exception ex) {
        String message = ex.getMessage();
        if (StrUtil.containsIgnoreCase(message, "moov atom not found")
                || StrUtil.containsIgnoreCase(message, "Invalid data found when processing input")) {
            return "视频文件不完整或无法识别，请重新导出或转码后再上传";
        }
        return message;
    }

    private VideoMetadata probe(File source) throws Exception {
        String output = runCommand(Arrays.asList(ffprobe(), "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "json",
                source.getAbsolutePath()));
        JsonNode root = JsonUtils.parseTree(output);
        JsonNode stream = root.path("streams").path(0);
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("FFprobe 未识别到视频分辨率");
        }
        BigDecimal duration = new BigDecimal(root.path("format").path("duration").asText("0"));
        long durationMillis = Math.max(1L, duration.multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.HALF_UP).longValue());
        long durationSeconds = Math.max(1L, (durationMillis + 999L) / 1000L);
        return new VideoMetadata(durationSeconds, durationMillis, width + "x" + height);
    }

    private String extractCover(TkMaterialVideoDO video, File source, File taskDir) throws Exception {
        File cover = new File(taskDir, "cover-" + video.getId() + ".jpg");
        runCommand(Arrays.asList(ffmpeg(), "-y", "-i", source.getAbsolutePath(),
                "-frames:v", "1", "-q:v", "2", cover.getAbsolutePath()));
        String directory = StrUtil.format("tk/{}/{}/material-covers", video.getTenantId(), video.getCompanyId());
        return fileApi.createFile(Files.readAllBytes(cover.toPath()),
                StrUtil.format("cover-{}.jpg", video.getId()), directory, "image/jpeg");
    }

    private File resolveSource(TkMaterialVideoDO video, File target) {
        Optional<Path> localPath = localUploadStorageService.resolveLocalPath(video.getFileUrl());
        if (localPath.isPresent() && Files.isRegularFile(localPath.get())) {
            return localPath.get().toFile();
        }
        try {
            byte[] content = fileApi.getFileContentByUrl(video.getFileUrl());
            if (content != null && content.length > 0) {
                FileUtil.writeBytes(content, target);
                return target;
            }
        } catch (Exception ex) {
            log.warn("[resolveSource][videoId({}) 文件存储内部读取失败，回退 HTTP 下载：{}]", video.getId(), video.getFileUrl(), ex);
        }
        log.warn("[resolveSource][videoId({}) 无法从文件存储内部读取，回退 HTTP 下载：{}]", video.getId(), video.getFileUrl());
        return download(video.getFileUrl(), target);
    }

    private File download(String url, File target) {
        if (StrUtil.isBlank(url) || !(StrUtil.startWithIgnoreCase(url, "http://") || StrUtil.startWithIgnoreCase(url, "https://"))) {
            throw new IllegalStateException("文件 URL 不是可下载的 HTTP 地址：" + url);
        }
        try (HttpResponse response = HttpRequest.get(url).timeout(FILE_DOWNLOAD_TIMEOUT_MILLIS).execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("下载文件失败，HTTP {}：{}", response.getStatus(), url));
            }
            FileUtil.writeBytes(response.bodyBytes(), target);
            return target;
        }
    }

    private String runCommand(java.util.List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(IoUtil.readBytes(process.getInputStream()), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("命令执行超时：" + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(output);
        }
        return output;
    }

    private File resolveWorkDir(TkMaterialVideoDO video) {
        String workDir = generationProperties.getFfmpeg().getWorkDir();
        if (StrUtil.isBlank(workDir)) {
            workDir = System.getProperty("java.io.tmpdir") + "/tk-generation";
        }
        workDir = workDir.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        return new File(workDir, "material-video-" + video.getId());
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private String safeName(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "video.mp4");
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @PreDestroy
    public void destroy() {
        executorService.shutdown();
    }

    private static class VideoMetadata {

        private final Long durationSeconds;
        private final Long durationMillis;
        private final String resolution;

        private VideoMetadata(Long durationSeconds, Long durationMillis, String resolution) {
            this.durationSeconds = durationSeconds;
            this.durationMillis = durationMillis;
            this.resolution = resolution;
        }

    }

}
