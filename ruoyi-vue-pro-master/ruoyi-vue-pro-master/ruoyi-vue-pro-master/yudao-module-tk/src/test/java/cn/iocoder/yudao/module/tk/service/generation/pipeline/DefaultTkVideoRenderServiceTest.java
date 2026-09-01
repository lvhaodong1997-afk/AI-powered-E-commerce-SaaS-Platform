package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkVideoRenderServiceTest {

    @Test
    void finalRenderCommandUsesHighQualityAudioSettings() {
        List<String> command = DefaultTkVideoRenderService.buildFinalRenderCommand(
                "ffmpeg", new File("merged-video.mp4"), new File("voice.wav"),
                null, 0.10D, 26.0D, new File("subtitle.ass"), new File("final-video.mp4"));

        assertTrue(command.contains("-c:a"));
        assertTrue(command.contains("aac"));
        assertTrue(command.contains("-ar"));
        assertTrue(command.contains("44100"));
        assertTrue(command.contains("-ac"));
        assertTrue(command.contains("2"));
        assertTrue(command.contains("-b:a"));
        assertTrue(command.contains("192k"));
        assertTrue(command.contains("-af"));
        assertTrue(command.contains("loudnorm=I=-16:TP=-1.5:LRA=11"));
        assertFalse(command.contains("-shortest"));
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("26.000"));
        assertFalse(String.join(" ", command).contains("tpad"));
    }

    @Test
    void finalRenderCommandUsesConfiguredFfmpegPreset() {
        List<String> command = DefaultTkVideoRenderService.buildFinalRenderCommand(
                "ffmpeg", new File("merged-video.mp4"), new File("voice.wav"),
                null, 0.10D, 26.0D, null, new File("final-video.mp4"), "ultrafast");

        assertTrue(command.contains("-preset"));
        assertEquals("ultrafast", command.get(command.indexOf("-preset") + 1));
    }

    @Test
    void finalRenderCommandMixesBgmWhenProvided() {
        List<String> command = DefaultTkVideoRenderService.buildFinalRenderCommand(
                "ffmpeg", new File("merged-video.mp4"), new File("voice.wav"),
                new File("bgm.mp3"), 0.10D, 26.0D, null, new File("final-video.mp4"));

        assertTrue(command.contains("-stream_loop"));
        assertTrue(command.contains("-1"));
        assertTrue(command.stream().anyMatch(item -> item.endsWith("bgm.mp3")));
        assertTrue(command.contains("-filter_complex"));
        String joined = String.join(" ", command);
        assertTrue(joined.contains("[1:a]loudnorm=I=-16:TP=-1.5:LRA=11[voice]"));
        assertTrue(joined.contains("[2:a]volume=0.100"));
        assertTrue(joined.contains("afade=t=in:ss=0:d=1"));
        assertTrue(joined.contains("afade=t=out:st=25.000:d=1"));
        assertTrue(joined.contains("[voice][bgm]amix=inputs=2:duration=first:dropout_transition=0[aout]"));
        assertTrue(joined.contains("-map [aout]"));
        assertTrue(command.contains("192k"));
    }

    @Test
    void finalRenderCommandUsesOnlyBgmWhenVoiceAudioIsDisabled() {
        List<String> command = DefaultTkVideoRenderService.buildFinalRenderCommand(
                "ffmpeg", new File("merged-video.mp4"), null,
                new File("bgm.mp3"), 0.12D, 18.0D, null, new File("final-video.mp4"));

        String joined = String.join(" ", command);
        assertTrue(command.contains("-stream_loop"));
        assertTrue(command.stream().anyMatch(item -> item.endsWith("bgm.mp3")));
        assertTrue(command.contains("-filter_complex"));
        assertTrue(joined.contains("[1:a]volume=0.120"));
        assertTrue(joined.contains("afade=t=out:st=17.000:d=1"));
        assertTrue(joined.contains("-map 0:v:0"));
        assertTrue(joined.contains("-map [aout]"));
        assertFalse(command.stream().anyMatch(item -> item.endsWith("voice.wav")));
    }

    @Test
    void finalRenderCommandOutputsSilentVideoWhenVoiceAndBgmAreDisabled() {
        List<String> command = DefaultTkVideoRenderService.buildFinalRenderCommand(
                "ffmpeg", new File("merged-video.mp4"), null,
                null, 0.10D, 18.0D, new File("subtitle.ass"), new File("final-video.mp4"));

        String joined = String.join(" ", command);
        assertTrue(joined.contains("-map 0:v:0"));
        assertTrue(command.contains("-an"));
        assertTrue(command.contains("-vf"));
        assertTrue(command.stream().anyMatch(item -> item.contains("subtitle.ass")));
        assertFalse(command.stream().anyMatch(item -> item.endsWith("voice.wav")));
        assertFalse(command.contains("-c:a"));
    }

    @Test
    void normalizeFullSourceCommandDoesNotTrimSourceVideo() {
        List<String> command = DefaultTkVideoRenderService.buildNormalizeFullSourceCommand(
                "ffmpeg", new File("source.mp4"), new File("normalized.mp4"));

        assertFalse(command.contains("-ss"));
        assertFalse(command.contains("-t"));
        assertTrue(command.contains("-i"));
        assertTrue(command.contains("scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1"));
    }

    @Test
    void normalizeFullSourceCommandUsesConfiguredFfmpegPreset() {
        List<String> command = DefaultTkVideoRenderService.buildNormalizeFullSourceCommand(
                "ffmpeg", new File("source.mp4"), new File("normalized.mp4"), "ultrafast");

        assertEquals("ultrafast", command.get(command.indexOf("-preset") + 1));
    }

    @Test
    void normalizeClipCommandUsesExactMillisecondTrim() {
        List<String> command = DefaultTkVideoRenderService.buildNormalizeClipCommand(
                "ffmpeg", new File("source.mp4"), new File("clip.mp4"), 0.0D, 2.243D, "veryfast", false);

        assertTrue(command.contains("-ss"));
        assertTrue(command.contains("0.000"));
        assertTrue(command.contains("-t"));
        assertTrue(command.contains("2.243"));
        assertFalse(String.join(" ", command).contains("tpad"));
    }

    @Test
    void normalizeClipCommandNeverMirrorsMaterialWhenLegacyVariantIsRequested() {
        List<String> command = DefaultTkVideoRenderService.buildNormalizeClipCommand(
                "ffmpeg", new File("source.mp4"), new File("clip.mp4"), 0.0D, 2.243D, "veryfast", true);

        assertFalse(String.join(" ", command).contains("hflip"));
    }

    @Test
    void adaptClipCommandStretchesOneUniqueSourceToTargetDuration() {
        List<String> command = DefaultTkVideoRenderService.buildAdaptClipCommand(
                "ffmpeg", new File("source.mp4"), new File("clip.mp4"), 0.0D, 2.0D, 3.0D, "veryfast");

        String joined = String.join(" ", command);
        assertTrue(joined.contains("setpts=PTS/0.666667"));
        assertFalse(joined.contains("hflip"));
        assertFalse(joined.contains("tpad"));
    }

    @Test
    void compressSectionCommandSpeedsWholeSectionToTargetDurationWithoutTrimArguments() {
        List<String> command = DefaultTkVideoRenderService.buildCompressSectionCommand(
                "ffmpeg", new File("section-raw.mp4"), new File("section.mp4"), 8.0D, 7.0D);

        assertFalse(command.contains("-ss"));
        assertFalse(command.contains("-t"));
        assertTrue(String.join(" ", command).contains("setpts=PTS/1.142857"));
    }

    @Test
    void padSectionCommandSlowsUniqueMaterialInsteadOfCloningLastFrame() {
        List<String> command = DefaultTkVideoRenderService.buildPadSectionCommand(
                "ffmpeg", new File("section-raw.mp4"), new File("section.mp4"), 23.337D, 27.0D);

        String joined = String.join(" ", command);
        assertFalse(joined.contains("tpad=stop_mode=clone"));
        assertTrue(joined.contains("setpts=PTS/0.864333"));
    }

    @Test
    void resolveDownloadUrlUsesInternalBaseUrlForOwnPublicFileUrl() {
        String resolved = DefaultTkVideoRenderService.resolveDownloadUrl(
                "https://tkassetplant.fnn.net.cn/admin-api/infra/file/29/get/tk/166/166/generation-tasks/105/20260721/voice-105.mp3",
                "https://tkassetplant.fnn.net.cn",
                "http://127.0.0.1:48080");

        assertEquals("http://127.0.0.1:48080/admin-api/infra/file/29/get/tk/166/166/generation-tasks/105/20260721/voice-105.mp3",
                resolved);
    }

    @Test
    void resolveDownloadUrlKeepsExternalMaterialUrlUnchanged() {
        String materialUrl = "https://tk-material-factory.oss-cn-beijing.aliyuncs.com/tk/166/166/material-videos/demo.mp4";

        String resolved = DefaultTkVideoRenderService.resolveDownloadUrl(
                materialUrl,
                "https://tkassetplant.fnn.net.cn",
                "http://127.0.0.1:48080");

        assertEquals(materialUrl, resolved);
    }

}
