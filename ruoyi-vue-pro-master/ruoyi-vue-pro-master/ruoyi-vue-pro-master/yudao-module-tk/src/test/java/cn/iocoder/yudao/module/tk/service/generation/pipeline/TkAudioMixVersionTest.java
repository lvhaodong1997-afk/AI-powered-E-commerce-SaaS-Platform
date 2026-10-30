package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TkAudioMixVersionTest {
    @Test
    void newMixNormalizesMusicBeforeFullGainAndProtectsOutputWithoutMakeupGain() {
        String filter = filter(command(1D, true, 0D, 6D, 2));
        assertTrue(filter.contains("[2:a]atrim=duration=6.000,loudnorm="));
        assertTrue(filter.contains("volume=1.000"));
        assertTrue(filter.contains("normalize=0"));
        assertTrue(filter.contains("alimiter=limit=0.84:level=false"));
        assertTrue(filter.contains("atrim=start=0.005"), "compensate FFmpeg 4.4 limiter lookahead");
    }

    @Test
    void pureMusicAllowsTrueZeroAndShortVideoFadesStayWithinDuration() {
        String filter = filter(command(0D, false, 0D, 0.2D, 2));
        assertTrue(filter.contains("volume=0.000"));
        assertTrue(filter.contains("afade=t=in:ss=0:d=0.100"));
        assertTrue(filter.contains("afade=t=out:st=0.100:d=0.100"));
        assertFalse(filter.substring(filter.indexOf("volume=")).contains("loudnorm="));
    }

    @Test
    void originalAudioOpeningKeepsDelayedBodyAndExplicitMixScaling() {
        String filter = filter(command(0.6D, true, 1.25D, 6D, 2));
        assertTrue(filter.contains("adelay=1250|1250"));
        assertTrue(filter.contains("[opening][voice][bgm]amix=inputs=3"));
        assertTrue(filter.contains("normalize=0"));
        assertTrue(filter.contains("atrim=duration=4.750,loudnorm="));
    }

    @Test
    void historicalVersionRetainsOriginalClampingAndMixing() {
        String filter = filter(command(1D, true, 0D, 6D, 1));
        assertTrue(filter.contains("[2:a]volume=0.300"));
        assertFalse(filter.contains("normalize=0"));
        assertFalse(filter.contains("alimiter="));
    }

    @Test
    void voiceOnlyAndNativeOnlyAreBoundedAndSilentVideoHasNoAudio() {
        String voice = filter(withoutBgm(true, 0D, 2));
        assertTrue(voice.contains("[1:a]atrim=duration=6.000,loudnorm="));
        assertTrue(voice.contains("alimiter="));
        String opening = filter(withoutBgm(false, 1.25D, 2));
        assertTrue(opening.contains("[opening]apad,atrim=duration=6.000"));
        assertFalse(opening.contains("amix="));
        assertTrue(withoutBgm(false, 0D, 2).contains("-an"));
        String fullOpening = filter(command(1D, true, 6D, 6D, 2));
        assertFalse(fullOpening.contains("[bgm]"));
        assertFalse(fullOpening.contains("[voice]"));
    }

    @Test
    void exportExactCommandFixturesForNativeAudioVerification() throws Exception {
        Map<String,List<String>> fixtures = new LinkedHashMap<>();
        for (int volume : new int[]{0,10,30,60,100}) {
            fixtures.put("music-" + volume, command(volume/100D, false, 0D, 6D, 2));
            fixtures.put("voice-" + volume, command(volume/100D, true, 0D, 6D, 2));
        }
        fixtures.put("native", command(0.6D, true, 1.25D, 6D, 2));
        fixtures.put("native-music", command(0.6D, false, 1.25D, 6D, 2));
        fixtures.put("short", command(1D, false, 0D, 0.2D, 2));
        fixtures.put("legacy", command(1D, true, 0D, 6D, 1));
        fixtures.put("voice-only", withoutBgm(true, 0D, 2));
        fixtures.put("native-only", withoutBgm(false, 1.25D, 2));
        fixtures.put("silent", withoutBgm(false, 0D, 2));
        fixtures.put("full-opening", command(1D, true, 6D, 6D, 2));
        assertEquals(18, fixtures.size());
        String output = System.getProperty("tk.audioFixtureOutput");
        if (output != null) {
            Files.write(Paths.get(output), JsonUtils.toJsonString(fixtures).getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<String> command(double volume, boolean voice, double opening, double duration, int version) {
        return DefaultTkVideoRenderService.buildFinalRenderCommand("ffmpeg", new File("merged.mp4"),
                voice ? new File("voice.wav") : null, new File("bgm.wav"), volume, duration, null,
                new File("output.mp4"), "ultrafast", opening, null, version);
    }

    private String filter(List<String> command) {
        return command.get(command.indexOf("-filter_complex")+1);
    }

    private List<String> withoutBgm(boolean voice, double opening, int version) {
        return DefaultTkVideoRenderService.buildFinalRenderCommand("ffmpeg", new File("merged.mp4"),
                voice ? new File("voice.wav") : null, null, 1D, 6D, null,
                new File("output.mp4"), "ultrafast", opening, null, version);
    }
}
