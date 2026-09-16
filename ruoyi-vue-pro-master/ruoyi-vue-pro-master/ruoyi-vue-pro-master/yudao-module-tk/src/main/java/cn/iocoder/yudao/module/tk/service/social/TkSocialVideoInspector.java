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

@Component
public class TkSocialVideoInspector {
    private final TkGenerationProperties properties;
    public TkSocialVideoInspector(TkGenerationProperties properties) { this.properties = properties; }

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
            if (Files.size(output)>TkSocialMediaService.MAX_VIDEO_BYTES) throw new IllegalArgumentException("转换后视频不能超过 100MB");
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
        JsonNode video = null;
        for (JsonNode stream : root.path("streams")) {
            if ("video".equals(stream.path("codec_type").asText()) && video == null) video = stream;
            if (validateAudio && "audio".equals(stream.path("codec_type").asText()) && !"aac".equals(stream.path("codec_name").asText())) {
                throw new IllegalArgumentException("请使用 AAC 音频编码的 MP4 视频");
            }
            if (validateAudio && "audio".equals(stream.path("codec_type").asText())
                    && (stream.path("bit_rate").asLong(0)>128000 || stream.path("sample_rate").asInt(48000)!=48000)) {
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
        return new Metadata(video.path("width").asInt(), video.path("height").asInt(), duration, fps);
    }

    @Getter @AllArgsConstructor
    public static class Metadata {
        private final int width;
        private final int height;
        private final double durationSeconds;
        private final double frameRate;
    }
}
