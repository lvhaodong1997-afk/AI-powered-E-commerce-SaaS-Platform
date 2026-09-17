package cn.iocoder.yudao.module.tk.service.social;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TkSocialVideoInspectorTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void malformedPathInspectionCleansProbeOutputButKeepsSource() throws Exception {
        java.nio.file.Path source = temp.resolve("broken.mp4");
        java.nio.file.Files.write(source, "malformed video with no container".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(source));
        assertTrue(java.nio.file.Files.exists(source));
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.list(temp)) { assertEquals(1, paths.count()); }
    }
    @Test void normalizationPreservesSourceAndConvertsAudioTo48k() throws Exception {
        String ffmpeg = cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver.ffmpeg("ffmpeg");
        java.nio.file.Path source = temp.resolve("audio-source.mp4"), output = temp.resolve("normalized.mp4");
        Process process = new ProcessBuilder(ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i", "color=c=black:s=64x96:r=25",
                "-f", "lavfi", "-i", "sine=frequency=1000:sample_rate=44100", "-t", "1", "-c:v", "libx264", "-c:a", "aac", source.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        byte[] original = java.nio.file.Files.readAllBytes(source);
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        assertEquals(output, inspector.prepareForPublish(source, output));
        TkSocialVideoInspector.Metadata metadata = inspector.inspect(output);
        assertEquals("aac", metadata.getAudioCodec()); assertEquals(48000, metadata.getAudioSampleRate());
        assertEquals(25, metadata.getFrameRate(), 0.001);
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(source));
    }
    @Test void workspaceEnforcesConcurrencyBudgetAndCleansOnFailure() throws Exception {
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(inspector, "tempDirectory", temp.toString());
        java.nio.file.Path directory;
        try (TkSocialVideoInspector.Workspace workspace = inspector.openWorkspace()) {
            directory = workspace.getDirectory();
            java.nio.file.Files.write(directory.resolve("partial.mp4"), new byte[]{1});
            assertThrows(IllegalStateException.class, inspector::openWorkspace);
        }
        assertFalse(java.nio.file.Files.exists(directory));
        org.springframework.test.util.ReflectionTestUtils.setField(inspector, "diskBudgetBytes", 1024L);
        assertThrows(IllegalStateException.class, inspector::openWorkspace);
    }
    @Test void workspaceRecoversAbandonedProbeDirectoriesOnly() throws Exception {
        java.nio.file.Path stale = java.nio.file.Files.createDirectory(temp.resolve("probe-abandoned"));
        java.nio.file.Files.write(stale.resolve("source.mp4"), new byte[]{1});
        java.nio.file.Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 10800000));
        java.nio.file.Path unrelated = java.nio.file.Files.createDirectory(temp.resolve("other-data"));
        java.nio.file.Files.setLastModifiedTime(unrelated, java.nio.file.attribute.FileTime.fromMillis(0));
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        org.springframework.test.util.ReflectionTestUtils.setField(inspector, "tempDirectory", temp.toString());
        try (TkSocialVideoInspector.Workspace ignored = inspector.openWorkspace()) {
            assertFalse(java.nio.file.Files.exists(stale)); assertTrue(java.nio.file.Files.exists(unrelated));
        }
    }
    @Test void pathInspectionPreservesInputAndReportsRealRationalFps() throws Exception {
        assertTrue(java.util.Arrays.stream(TkSocialVideoInspector.class.getMethods()).anyMatch(m ->
                m.getName().equals("inspect") && java.util.Arrays.equals(m.getParameterTypes(), new Class<?>[]{java.nio.file.Path.class})),
                "Large uploads require a Path inspection API");
        String ffmpeg = cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver.ffmpeg("ffmpeg");
        java.nio.file.Path source = temp.resolve("source.mp4");
        Process process = new ProcessBuilder(ffmpeg, "-y", "-v", "error", "-f", "lavfi", "-i",
                "color=c=black:s=64x96:r=24000/1001", "-t", "0.5", "-c:v", "libx264", "-pix_fmt", "yuv420p", source.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        byte[] original = java.nio.file.Files.readAllBytes(source);
        TkSocialVideoInspector inspector = new TkSocialVideoInspector(new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        TkSocialVideoInspector.Metadata value = (TkSocialVideoInspector.Metadata) TkSocialVideoInspector.class.getMethod("inspect", java.nio.file.Path.class).invoke(inspector, source);
        assertEquals(23.976, value.getFrameRate(), 0.001);
        assertEquals(64, value.getWidth()); assertEquals(96, value.getHeight());
        assertArrayEquals(original, java.nio.file.Files.readAllBytes(source));
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.list(temp)) { assertEquals(1, paths.count()); }
    }
    @Test void exposesActualCodecsAndAudioProperties() {
        TkSocialVideoInspector.Metadata value=TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":1080,\"height\":1920,\"avg_frame_rate\":\"24000/1001\",\"bit_rate\":\"8000000\"},{\"codec_type\":\"audio\",\"codec_name\":\"aac\",\"bit_rate\":\"96000\",\"sample_rate\":\"48000\"}],\"format\":{\"duration\":\"30.5\"}}"));
        org.springframework.beans.BeanWrapperImpl bean = new org.springframework.beans.BeanWrapperImpl(value);
        assertTrue(bean.isReadableProperty("videoCodec"), "Codec evidence must be retained");
        assertEquals("h264", bean.getPropertyValue("videoCodec"));
        assertEquals("aac", bean.getPropertyValue("audioCodec"));
        assertEquals(8000000L, bean.getPropertyValue("videoBitrate"));
        assertEquals(96000L, bean.getPropertyValue("audioBitrate"));
        assertEquals(48000, bean.getPropertyValue("audioSampleRate"));
        assertEquals(23.976, value.getFrameRate(), 0.001);
    }
    @Test void unknownFrameRateIsRejectedRatherThanInvented() {
        assertThrows(IllegalArgumentException.class, () -> TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":1080,\"height\":1920,\"avg_frame_rate\":\"0/0\"}],\"format\":{\"duration\":\"30.5\"}}")));
    }
    @Test void parsesFractionalFrameRate() {
        TkSocialVideoInspector.Metadata value=TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":1080,\"height\":1920,\"avg_frame_rate\":\"30000/1001\"}],\"format\":{\"duration\":\"30.5\"}}"));
        assertEquals(29.970,value.getFrameRate(),0.001);
        assertEquals(30.5,value.getDurationSeconds());
    }
    @Test void rejectsMissingVideoAndWrongAudio() {
        assertThrows(IllegalArgumentException.class,() -> TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"audio\",\"codec_name\":\"mp3\"}],\"format\":{\"duration\":\"3\"}}")));
    }
    @Test void facebookRejectsLandscapeBeforeTaskCreation() {
        TkSocialMediaDO media=video(1920,1080,30,30);
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(media,"FACEBOOK_PAGE"));
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(media,"INSTAGRAM"));
    }
    @Test void durationAndFpsBoundariesArePlatformSpecific() {
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(video(1080,1920,60,30),"FACEBOOK_PAGE"));
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(video(1080,1920,61,30),"FACEBOOK_PAGE"));
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(video(1080,1920,61,30),"INSTAGRAM"));
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(video(1080,1920,30,15),"INSTAGRAM"));
    }
    private static TkSocialMediaDO video(int w,int h,double seconds,double fps) {
        TkSocialMediaDO value=new TkSocialMediaDO(); value.setMediaType("VIDEO"); value.setWidth(w); value.setHeight(h);
        value.setDurationSeconds(seconds); value.setFrameRate(fps); return value;
    }
}
