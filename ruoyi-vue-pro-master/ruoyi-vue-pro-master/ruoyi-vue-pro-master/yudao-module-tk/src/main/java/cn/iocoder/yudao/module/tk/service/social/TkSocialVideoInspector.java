package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import java.io.IOException;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;

@Component
public class TkSocialVideoInspector {
    private final TkGenerationProperties properties;
    private final Semaphore slot = new Semaphore(1);
    @Value("${tk.social.probe.temp-directory:}") private String tempDirectory = "";
    @Value("${tk.social.probe.disk-budget-bytes:3221225472}") private long diskBudgetBytes = 3221225472L;
    public TkSocialVideoInspector(TkGenerationProperties properties) { this.properties = properties; }

    /** One slot shared by request imports and background inspection, with a two-file disk reservation. */
    public Workspace openWorkspace() {
        if (!slot.tryAcquire()) throw new CapacityBusyException();
        try {
            Path root = tempDirectory == null || tempDirectory.trim().isEmpty()
                    ? Paths.get(System.getProperty("java.io.tmpdir"), "tk-social-probe") : Paths.get(tempDirectory);
            Files.createDirectories(root);
            cleanupAbandonedWorkspaces(root.toAbsolutePath().normalize());
            long reservation = 2 * TkSocialMediaService.MAX_VIDEO_BYTES + 1048576;
            long used;
            try (Stream<Path> paths = Files.walk(root)) {
                used = paths.filter(Files::isRegularFile).mapToLong(p -> {
                    try { return Files.size(p); } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }).sum();
            }
            if (diskBudgetBytes - used < reservation || Files.getFileStore(root).getUsableSpace() < reservation)
                throw new IllegalStateException("视频检查临时磁盘空间不足");
            return new Workspace(Files.createTempDirectory(root, "probe-"));
        } catch (IOException | RuntimeException ex) {
            slot.release(); throw new IllegalStateException("视频检查临时目录不可用或空间不足");
        }
    }
    /** Raised only before any workspace or media work starts; the queue can defer without spending a retry. */
    public static final class CapacityBusyException extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private CapacityBusyException() { super("视频检查繁忙，请稍后重试"); }
    }
    private static void cleanupAbandonedWorkspaces(Path root) throws IOException {
        // Every operation is bounded well below twenty minutes. Two hours excludes active work,
        // including other application instances using this dedicated temporary directory.
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);
        try (java.nio.file.DirectoryStream<Path> entries = Files.newDirectoryStream(root, "probe-*")) {
            for (Path entry : entries) {
                Path directory = entry.toAbsolutePath().normalize();
                if (!directory.getParent().equals(root) || Files.isSymbolicLink(directory)
                        || !Files.isDirectory(directory) || Files.getLastModifiedTime(directory).toMillis() >= cutoff) continue;
                try (Stream<Path> paths = Files.walk(directory)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(TkSocialVideoInspector::delete);
                }
            }
        }
    }
    public final class Workspace implements AutoCloseable {
        @Getter private final Path directory;
        private boolean closed;
        private Workspace(Path directory) { this.directory = directory; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(TkSocialVideoInspector::delete);
            } catch (IOException ignored) { /* Leftovers still count against the budget. */ }
            finally { slot.release(); }
        }
    }
    private static void delete(Path path) {
        if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
    private static void stop(Process process) {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        }
    }
    public Metadata inspect(Path video) { return parse(probe(video)); }

    /** The source is never modified. Caller owns both source and output lifecycle. */
    public Path prepareForPublish(Path source, Path output) {
        if (source.equals(output)) throw new IllegalArgumentException("发布副本不得覆盖源文件");
        JsonNode metadata = probe(source);
        Metadata original = parse(metadata, false);
        boolean convert = false;
        for (JsonNode stream : metadata.path("streams")) {
            if ("audio".equals(stream.path("codec_type").asText())
                    && (!"aac".equals(stream.path("codec_name").asText()) || stream.path("sample_rate").asInt() != 48000
                    || stream.path("bit_rate").asLong(0) > 128000)) convert = true;
        }
        if (!convert) return source;
        Process process = null;
        boolean success = false;
        try {
            process = new ProcessBuilder(TkFfmpegExecutableResolver.ffmpeg(properties.getFfmpeg().getFfmpegPath()),
                    "-y", "-v", "error", "-protocol_whitelist", "file,pipe", "-i", source.toString(),
                    "-map", "0:v:0", "-map", "0:a:0?", "-c:v", "copy", "-c:a", "aac", "-ar", "48000", "-ac", "2",
                    "-b:a", "96k", "-fs", Long.toString(TkSocialMediaService.MAX_VIDEO_BYTES + 1),
                    "-movflags", "+faststart", output.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0)
                throw new IllegalArgumentException("发布副本音频转换失败或超时");
            Metadata actual = inspect(output);
            if (Math.abs(actual.getDurationSeconds() - original.getDurationSeconds()) > 0.25)
                throw new IllegalArgumentException("发布副本时长不一致");
            success = true;
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("发布副本准备已中断");
        } catch (IOException ex) { throw new IllegalStateException("发布副本音频转换不可用，请检查 FFmpeg 配置"); }
        finally { stop(process); if (!success) delete(output); }
    }

    private JsonNode probe(Path video) {
        Path output = null;
        Process process = null;
        try {
            if (!Files.isRegularFile(video) || Files.size(video) < 12 || Files.size(video) > TkSocialMediaService.MAX_VIDEO_BYTES)
                throw new IllegalArgumentException("视频大小无效或超过 1GB");
            output = Files.createTempFile(video.toAbsolutePath().getParent(), "probe-", ".json");
            process = new ProcessBuilder(TkFfmpegExecutableResolver.ffprobe(properties.getFfmpeg().getFfprobePath()),
                    "-v", "error", "-protocol_whitelist", "file,pipe", "-show_entries",
                    "stream=codec_type,codec_name,width,height,avg_frame_rate,bit_rate,sample_rate:format=duration,format_name",
                    "-of", "json", video.toString()).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(output.toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalArgumentException("视频检查超时，请重新上传");
            if (process.exitValue() != 0 || Files.size(output) > 65536) throw new IllegalArgumentException("无法解析 MP4 视频");
            JsonNode result = JsonUtils.parseTree(Files.readString(output));
            if (result == null || !result.path("format").path("format_name").asText().contains("mp4"))
                throw new IllegalArgumentException("文件不是有效的 MP4 视频");
            return result;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("视频检查已中断");
        } catch (IOException ex) { throw new IllegalStateException("视频检查工具不可用，请检查后端 ffprobe 配置"); }
        finally { stop(process); delete(output); }
    }

    public Metadata inspect(byte[] bytes) {
        return parse(probe(bytes));
    }

    /** Preserve video packets; normalize incompatible audio only in the independent publish copy. */
    public byte[] prepareForPublish(byte[] bytes) {
        JsonNode source=probe(bytes);
        parse(source,false);
        boolean convert=false;
        for (JsonNode stream:source.path("streams")) {
            if ("audio".equals(stream.path("codec_type").asText())
                    && (!"aac".equals(stream.path("codec_name").asText()) || stream.path("sample_rate").asInt()!=48000
                    || stream.path("bit_rate").asLong(0)>128000)) convert=true;
        }
        if (!convert) return bytes;
        Path input=null,output=null;
        Process process=null;
        try {
            input=Files.createTempFile("tk-social-audio-source-",".mp4");
            output=Files.createTempFile("tk-social-audio-publish-",".mp4");
            Files.write(input,bytes);
            process=new ProcessBuilder(TkFfmpegExecutableResolver.ffmpeg(properties.getFfmpeg().getFfmpegPath()),
                    "-y","-v","error","-protocol_whitelist","file,pipe","-i",input.toString(),"-map","0:v:0","-map","0:a:0?",
                    "-c:v","copy","-c:a","aac","-ar","48000","-ac","2","-b:a","96k","-movflags","+faststart",output.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(120,TimeUnit.SECONDS) || process.exitValue()!=0)
                throw new IllegalArgumentException("发布副本音频转换失败或超时");
            if (Files.size(output)>TkSocialMediaService.MAX_VIDEO_BYTES) throw new IllegalArgumentException("转换后视频不能超过 1GB");
            return Files.readAllBytes(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("发布副本准备已中断");
        } catch (java.io.IOException ex) { throw new IllegalStateException("发布副本音频转换不可用，请检查 FFmpeg 配置"); }
        finally {
            if (process!=null && process.isAlive()) process.destroyForcibly();
            for (Path path:new Path[]{input,output}) if(path!=null) try { Files.deleteIfExists(path); } catch(java.io.IOException ignored) { }
        }
    }

    private JsonNode probe(byte[] bytes) {
        Path video = null;
        Path output = null;
        Process process = null;
        try {
            video = Files.createTempFile("tk-social-probe-", ".mp4");
            output = Files.createTempFile("tk-social-probe-", ".json");
            Files.write(video, bytes);
            process = new ProcessBuilder(TkFfmpegExecutableResolver.ffprobe(properties.getFfmpeg().getFfprobePath()),
                    "-v", "error", "-protocol_whitelist","file,pipe","-show_entries", "stream=codec_type,codec_name,width,height,avg_frame_rate,bit_rate,sample_rate:format=duration",
                    "-of", "json", video.toString()).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(output.toFile()).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) throw new IllegalArgumentException("视频检查超时，请重新上传");
            if (process.exitValue() != 0 || Files.size(output) > 65536) throw new IllegalArgumentException("无法解析 MP4 视频");
            return JsonUtils.parseTree(Files.readString(output));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("视频检查已中断");
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("视频检查工具不可用，请检查后端 ffprobe 配置");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            for (Path path : new Path[]{video, output}) {
                if (path != null) try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { /* OS cleanup may be delayed. */ }
            }
        }
    }

    static Metadata parse(JsonNode root) {
        return parse(root,true);
    }
    private static Metadata parse(JsonNode root,boolean validateAudio) {
        JsonNode video = null, audio = null;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText()) && video == null) video = stream;
            if ("audio".equals(stream.path("codec_type").asText()) && audio == null) audio = stream;
            if (validateAudio && "audio".equals(stream.path("codec_type").asText()) && !"aac".equals(stream.path("codec_name").asText())) {
                throw new IllegalArgumentException("请使用 AAC 音频编码的 MP4 视频");
            }
            if (validateAudio && "audio".equals(stream.path("codec_type").asText())
                    && (stream.path("bit_rate").asLong(0)>128000 || stream.path("sample_rate").asInt()!=48000)) {
                throw new IllegalArgumentException("请使用 48kHz、码率不超过 128kbps 的 AAC 音频");
            }
        }
        double duration = root.path("format").path("duration").asDouble(0);
        if (video == null || !"h264".equals(video.path("codec_name").asText()) || !Double.isFinite(duration)
                || duration <= 0 || video.path("width").asInt() <= 0 || video.path("height").asInt() <= 0) {
            throw new IllegalArgumentException("请使用有效的 H.264 MP4 视频");
        }
        String[] ratio=video.path("avg_frame_rate").asText("0/1").split("/");
        double fps;
        try { fps=Double.parseDouble(ratio[0])/(ratio.length>1 ? Double.parseDouble(ratio[1]) : 1); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("无法解析视频帧率"); }
        if (!Double.isFinite(fps) || fps<=0 || video.path("bit_rate").asLong(0)>25_000_000) {
            throw new IllegalArgumentException("请使用有效帧率、码率不超过 25Mbps 的视频");
        }
        return new Metadata(video.path("width").asInt(), video.path("height").asInt(), duration, fps,
                video.path("codec_name").asText(), audio == null ? null : audio.path("codec_name").asText(),
                positiveLong(video, "bit_rate"), audio == null ? null : positiveLong(audio, "bit_rate"),
                audio == null || audio.path("sample_rate").asInt() <= 0 ? null : audio.path("sample_rate").asInt());
    }
    private static Long positiveLong(JsonNode node, String field) {
        long value = node.path(field).asLong(0); return value > 0 ? value : null;
    }

    @Getter @AllArgsConstructor
    public static class Metadata {
        private final int width;
        private final int height;
        private final double durationSeconds;
        private final double frameRate;
        private final String videoCodec;
        private final String audioCodec;
        private final Long videoBitrate;
        private final Long audioBitrate;
        private final Integer audioSampleRate;
        public Metadata(int width, int height, double durationSeconds, double frameRate) {
            this(width, height, durationSeconds, frameRate, null, null, null, null, null);
        }
    }
}
