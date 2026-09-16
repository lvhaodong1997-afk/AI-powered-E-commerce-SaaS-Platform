package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialVideoFileTest {
    @TempDir Path temp;
    @Test void probesRealH264AacMp4() throws Exception {
        Path video=createVideo(48000,"96k");
        TkSocialVideoInspector.Metadata metadata=new TkSocialVideoInspector(new TkGenerationProperties()).inspect(Files.readAllBytes(video));
        assertEquals(540,metadata.getWidth()); assertEquals(960,metadata.getHeight());
        assertEquals(30,metadata.getFrameRate(),0.01); assertEquals(4,metadata.getDurationSeconds(),0.1);
    }
    @Test void existingGenerationAudioIsConvertedOnlyInPublishCopy() throws Exception {
        Path video=createVideo(44100,"192k");
        byte[] original=Files.readAllBytes(video);
        TkSocialVideoInspector inspector=new TkSocialVideoInspector(new TkGenerationProperties());
        assertThrows(IllegalArgumentException.class,() -> inspector.inspect(original));
        byte[] prepared=inspector.prepareForPublish(original);
        TkSocialVideoInspector.Metadata metadata=inspector.inspect(prepared);
        assertEquals(540,metadata.getWidth()); assertEquals(960,metadata.getHeight());
        assertEquals(4,metadata.getDurationSeconds(),0.1);
        assertArrayEquals(original,Files.readAllBytes(video));
    }
    private Path createVideo(int sampleRate,String bitrate) throws Exception {
        String ffmpeg;
        try { ffmpeg=TkFfmpegExecutableResolver.ffmpeg("ffmpeg"); TkFfmpegExecutableResolver.ffprobe("ffprobe"); }
        catch (IllegalStateException ex) { Assumptions.assumeTrue(false,"FFmpeg/ffprobe not installed"); return null; }
        Path video=temp.resolve("fixture.mp4");
        Process process=new ProcessBuilder(ffmpeg,"-v","error","-f","lavfi","-i","color=c=blue:s=540x960:r=30",
                "-f","lavfi","-i","sine=frequency=440:sample_rate="+sampleRate,"-t","4","-c:v","libx264","-preset","ultrafast",
                "-pix_fmt","yuv420p","-c:a","aac","-b:a",bitrate,"-movflags","+faststart",video.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try { assertTrue(process.waitFor(30,TimeUnit.SECONDS)); assertEquals(0,process.exitValue()); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
        return video;
    }
}
