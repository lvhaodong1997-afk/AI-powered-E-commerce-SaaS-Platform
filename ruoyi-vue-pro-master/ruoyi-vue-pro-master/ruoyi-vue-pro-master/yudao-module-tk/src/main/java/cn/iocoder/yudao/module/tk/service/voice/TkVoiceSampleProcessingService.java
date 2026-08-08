package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_VOICE_FILE_INVALID;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_VOICE_VIDEO_AUDIO_TOO_SHORT;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_VOICE_VIDEO_FFMPEG_FAILED;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_VOICE_VIDEO_NO_AUDIO;

@Service
public class TkVoiceSampleProcessingService {

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList("mp3", "wav", "m4a"));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList("mp4", "mov", "webm"));
    private static final int PROCESS_TIMEOUT_SECONDS = 180;
    private static final double MIN_EFFECTIVE_SECONDS = 10D;

    private final TkGenerationProperties generationProperties;

    public TkVoiceSampleProcessingService(TkGenerationProperties generationProperties) {
        this.generationProperties = generationProperties == null ? new TkGenerationProperties() : generationProperties;
    }

    public TkVoiceProcessedSample process(MultipartFile file) {
        String filename = StrUtil.blankToDefault(file.getOriginalFilename(), "voice.mp3");
        if (!isVideo(filename)) {
            if (!isAudio(filename)) {
                throw exception(TK_VOICE_FILE_INVALID);
            }
            try {
                return new TkVoiceProcessedSample(file.getBytes(), filename, file.getContentType());
            } catch (Exception ex) {
                throw exception(TK_VOICE_VIDEO_FFMPEG_FAILED, ex.getMessage());
            }
        }
        return extractAudioFromVideo(file, filename);
    }

    public static boolean isVideo(String filename) {
        return VIDEO_EXTENSIONS.contains(extension(filename));
    }

    public static boolean isAudio(String filename) {
        return AUDIO_EXTENSIONS.contains(extension(filename));
    }

    private TkVoiceProcessedSample extractAudioFromVideo(MultipartFile file, String filename) {
        File workDir = FileUtil.mkdir(new File(resolveWorkDir(), "voice-sample-" + UUID.randomUUID()));
        File input = new File(workDir, safeName(filename));
        File output = new File(workDir, baseName(filename) + "-voice.wav");
        try {
            file.transferTo(input);
            assertAudioTrack(input);
            runCommand(Arrays.asList(ffmpeg(), "-y", "-loglevel", "error",
                    "-i", input.getAbsolutePath(),
                    "-map", "0:a:0",
                    "-vn",
                    "-t", "60",
                    "-af", "silenceremove=start_periods=1:start_duration=0.3:start_threshold=-45dB:"
                            + "stop_periods=-1:stop_duration=0.5:stop_threshold=-45dB,"
                            + "loudnorm=I=-16:TP=-1:LRA=11",
                    "-ac", "1",
                    "-ar", "24000",
                    "-sample_fmt", "s16",
                    output.getAbsolutePath()));
            double duration = probeDuration(output);
            if (duration < MIN_EFFECTIVE_SECONDS) {
                throw exception(TK_VOICE_VIDEO_AUDIO_TOO_SHORT);
            }
            return new TkVoiceProcessedSample(Files.readAllBytes(output.toPath()), output.getName(), "audio/wav");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(TK_VOICE_VIDEO_FFMPEG_FAILED, StrUtil.blankToDefault(ex.getMessage(), "unknown"));
        } finally {
            FileUtil.del(workDir);
        }
    }

    private void assertAudioTrack(File input) throws Exception {
        String output = runCommand(Arrays.asList(ffprobe(), "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_type",
                "-of", "csv=p=0",
                input.getAbsolutePath()));
        if (StrUtil.isBlank(output)) {
            throw exception(TK_VOICE_VIDEO_NO_AUDIO);
        }
    }

    private double probeDuration(File input) throws Exception {
        String output = runCommand(Arrays.asList(ffprobe(), "-v", "error",
                "-show_entries", "format=duration",
                "-of", "json",
                input.getAbsolutePath()));
        JsonNode root = JsonUtils.parseTree(output);
        return root.path("format").path("duration").asDouble(0D);
    }

    private String runCommand(java.util.List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(IoUtil.readBytes(process.getInputStream()), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw exception(TK_VOICE_VIDEO_FFMPEG_FAILED, "timeout");
        }
        if (process.exitValue() != 0) {
            throw exception(TK_VOICE_VIDEO_FFMPEG_FAILED, StrUtil.maxLength(output, 512));
        }
        return output;
    }

    private File resolveWorkDir() {
        String workDir = generationProperties.getFfmpeg().getWorkDir();
        if (StrUtil.isBlank(workDir)) {
            workDir = System.getProperty("java.io.tmpdir") + "/tk-generation";
        }
        workDir = workDir.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        return FileUtil.mkdir(workDir);
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private String safeName(String fileName) {
        return StrUtil.blankToDefault(fileName, "voice-video.mp4").replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String baseName(String fileName) {
        String name = safeName(fileName);
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    private static String extension(String filename) {
        String name = StrUtil.blankToDefault(filename, "");
        int index = name.lastIndexOf('.');
        return index >= 0 ? name.substring(index + 1).toLowerCase(Locale.ROOT) : "";
    }

}
