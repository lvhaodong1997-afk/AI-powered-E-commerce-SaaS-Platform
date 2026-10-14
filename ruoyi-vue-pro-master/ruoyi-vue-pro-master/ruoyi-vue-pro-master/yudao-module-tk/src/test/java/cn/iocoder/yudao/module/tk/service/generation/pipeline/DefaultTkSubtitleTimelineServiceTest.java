package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkSubtitleTimelineServiceTest {

    private final DefaultTkSubtitleTimelineService service = new DefaultTkSubtitleTimelineService();

    @TempDir
    Path tempDir;

    @Test
    void buildTimelinePreservesSpeechAnchorsFromRealMangoNarration() throws Exception {
        byte[] fixtureBytes;
        try (java.io.InputStream stream = getClass().getResourceAsStream("/subtitle/mango-narration-asr.json")) {
            fixtureBytes = stream.readAllBytes();
        }
        com.fasterxml.jackson.databind.JsonNode fixture = JsonUtils.parseTree(fixtureBytes);
        Path script = tempDir.resolve("real-narration-asr.py");
        Files.writeString(script, "print(" + JsonUtils.toJsonString(fixture.path("asr").toString()) + ")\n",
                StandardCharsets.UTF_8);
        ReflectionTestUtils.setField(service, "generationProperties", modelTestProperties(script));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder().targetLanguage("zh-cn").targetDuration(38)
                .subtitleKaraokeEnabled(true).build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, fixture.path("script").asText(), modelTestAudio(),
                Collections.emptyList());

        assertTrue(Files.readString(tempDir.resolve("subtitle-quality.json")).contains("\"mode\":\"ASR_EXACT\""));
        assertEquals(37.9635D, timeline.getAudioDuration(), 0.001D);
        assertEquals(12.18D, phraseStart(timeline, "试试我们家这款"), 0.10D);
        assertEquals(16.58D, phraseStart(timeline, "高温烘干锁香"), 0.10D);
        assertEquals(33.86D, phraseStart(timeline, "喜欢直接拍"), 0.10D);
        assertEquals(fixture.path("script").asText().replaceAll("\\s+", ""),
                String.join("", timeline.getSegments().stream().map(TkSubtitleSegment::getText)
                        .toArray(String[]::new)).replaceAll("\\s+", ""));
    }

    private double phraseStart(TkSubtitleTimeline timeline, String phrase) {
        List<TkSubtitleWord> words = new ArrayList<>();
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            words.addAll(segment.getWords());
        }
        for (int i = 0; i < words.size(); i++) {
            StringBuilder text = new StringBuilder();
            for (int j = i; j < words.size() && text.length() < phrase.length(); j++) {
                text.append(words.get(j).getText());
            }
            if (text.toString().startsWith(phrase)) {
                return words.get(i).getStart();
            }
        }
        throw new AssertionError("Missing narration phrase: " + phrase);
    }

    @Test
    void buildTimelineUsesCachedModelAndPreservesSpeechTimestamps() throws Exception {
        Path modelRoot = Files.createDirectory(tempDir.resolve("local models"));
        Path model = Files.createDirectory(modelRoot.resolve("base"));
        TkGenerationProperties properties = modelTestProperties(writeModelTestScript(false));
        properties.getSubtitle().getAsr().setModel("base");
        properties.getSubtitle().getAsr().setModelCacheDir(modelRoot.toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkSubtitleTimeline timeline = service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(),
                Collections.emptyList());

        assertEquals(model.toAbsolutePath().toString(), Files.readString(tempDir.resolve("model-used.txt")));
        assertEquals(0.25D, timeline.getSegments().get(0).getStart(), 0.001D);
        assertEquals(1.3D, timeline.getSegments().get(0).getWords().get(2).getStart(), 0.001D);
        assertTrue(Files.readString(tempDir.resolve("subtitle-quality.json")).contains("\"mode\":\"ASR_EXACT\""));
    }

    @Test
    void buildTimelineKeepsExplicitModelPathWhenCacheDirectoryIsConfigured() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("explicit model"));
        TkGenerationProperties properties = modelTestProperties(writeModelTestScript(false));
        properties.getSubtitle().getAsr().setModel(model.toString());
        properties.getSubtitle().getAsr().setModelCacheDir(tempDir.resolve("unused cache").toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkSubtitleTimeline timeline = service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(),
                Collections.emptyList());

        assertEquals(model.toAbsolutePath().toString(), Files.readString(tempDir.resolve("model-used.txt")));
        assertEquals(0.25D, timeline.getSegments().get(0).getStart(), 0.001D);
    }

    @Test
    void buildTimelineDoesNotDownloadMissingCachedModelAndReportsFailure() throws Exception {
        TkGenerationProperties properties = modelTestProperties(writeModelTestScript(false));
        properties.getSubtitle().getAsr().setModel("base");
        properties.getSubtitle().getAsr().setModelCacheDir(tempDir.resolve("missing cache").toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkSubtitleTimeline timeline = service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(),
                Collections.emptyList());

        assertFalse(Files.exists(tempDir.resolve("model-used.txt")), "Missing local models must not trigger Hub downloads");
        assertFalse(Boolean.TRUE.equals(timeline.getSegments().get(0).getWordTimingReliable()));
        assertTrue(Files.readString(tempDir.resolve("subtitle-quality.json")).contains("ASR_MODEL_NOT_FOUND"));
    }

    @Test
    void buildTimelineResolvesRetryModelFromSameCacheDirectory() throws Exception {
        Path modelRoot = Files.createDirectory(tempDir.resolve("retry models"));
        Files.createDirectory(modelRoot.resolve("base"));
        Path retryModel = Files.createDirectory(modelRoot.resolve("medium"));
        TkGenerationProperties properties = modelTestProperties(writeModelTestScript(true));
        properties.getSubtitle().getAsr().setModel("base");
        properties.getSubtitle().getAsr().setRetryModel("medium");
        properties.getSubtitle().getAsr().setRetryEnabled(true);
        properties.getSubtitle().getAsr().setModelCacheDir(modelRoot.toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkSubtitleTimeline timeline = service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(),
                Collections.emptyList());

        assertEquals(retryModel.toAbsolutePath().toString(), Files.readString(tempDir.resolve("model-used.txt")));
        assertEquals(0.25D, timeline.getSegments().get(0).getStart(), 0.001D);
        assertTrue(Files.readString(tempDir.resolve("subtitle-quality.json")).contains("\"mode\":\"ASR_RETRY_EXACT\""));
    }

    @Test
    void buildTimelineKeepsAsrJsonSeparateFromProcessWarnings() throws Exception {
        Path model = Files.createDirectory(tempDir.resolve("warning model"));
        Path script = writeModelTestScript(false);
        Files.writeString(script, "import sys\nprint('model warning', file=sys.stderr)\n"
                + Files.readString(script), StandardCharsets.UTF_8);
        TkGenerationProperties properties = modelTestProperties(script);
        properties.getSubtitle().getAsr().setModel(model.toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkSubtitleTimeline timeline = service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(),
                Collections.emptyList());

        assertEquals(0.25D, timeline.getSegments().get(0).getStart(), 0.001D);
        assertTrue(Files.readString(tempDir.resolve("subtitle-quality.json")).contains("\"mode\":\"ASR_EXACT\""));
    }

    @Test
    void buildTimelineRecordsAsrExitCodeWithoutPublishingProcessOutput() throws Exception {
        Path script = tempDir.resolve("failed-asr.py");
        Files.writeString(script, "import sys\nprint('private diagnostic', file=sys.stderr)\nsys.exit(2)\n");
        ReflectionTestUtils.setField(service, "generationProperties", modelTestProperties(script));

        service.buildTimeline(modelTestTask(), "Buy this serum", modelTestAudio(), Collections.emptyList());

        String quality = Files.readString(tempDir.resolve("subtitle-quality.json"));
        assertTrue(quality.contains("ASR_PROCESS_EXIT_2"));
        assertFalse(quality.contains("private diagnostic"));
    }

    private TkGenerationProperties modelTestProperties(Path script) {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(script.toString());
        properties.getSubtitle().getAsr().setRetryEnabled(false);
        return properties;
    }

    private Path writeModelTestScript(boolean mismatchPrimary) throws Exception {
        Path script = tempDir.resolve("model-asr.py");
        Files.writeString(script, "import json, pathlib, sys\n"
                + "model = pathlib.Path(sys.argv[sys.argv.index('--model') + 1])\n"
                + "pathlib.Path(__file__).with_name('model-used.txt').write_text(str(model), encoding='utf-8')\n"
                + "if not model.is_dir(): sys.exit(2)\n"
                + "text = 'unrelated narration' if " + (mismatchPrimary ? "model.name != 'medium'" : "False")
                + " else 'Buy this serum'\n"
                + "print(json.dumps({'language':'en-US','audioDuration':2.5,'segments':[{'text':text,'start':0.25,'end':2.5,"
                + "'words':[{'text':'Buy','start':0.25,'end':0.7},{'text':'this','start':0.7,'end':1.3},"
                + "{'text':'serum','start':1.3,'end':2.5}]}]}))\n", StandardCharsets.UTF_8);
        return script;
    }

    private File modelTestAudio() throws Exception {
        Path audio = tempDir.resolve("voice.mp3");
        Files.write(audio, new byte[]{0});
        return audio.toFile();
    }

    private TkGenerationTaskDO modelTestTask() {
        return TkGenerationTaskDO.builder().targetLanguage("en-US").targetDuration(3).build();
    }

    @Test
    void buildTimelineSplitsLongEnglishTextWithoutPunctuationIntoTimedCaptionChunks() throws Exception {
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(12)
                .build();
        String script = "Stop ignoring that nagging wrist pain our hand splint keeps your hand in a perfect healing posture "
                + "plus the breathable design stays comfortable all day so you can type work and rest with steady support";
        File missingAudio = new File("missing-audio-for-duration-fallback.mp3");

        TkSubtitleTimeline timeline = service.buildTimeline(task, script, missingAudio, Collections.emptyList());

        assertTrue(timeline.getSegments().size() >= 3,
                "Long English scripts without punctuation should not render as one full-video subtitle");
        assertEquals(0D, timeline.getSegments().get(0).getStart());
        for (int i = 0; i < timeline.getSegments().size(); i++) {
            TkSubtitleSegment segment = timeline.getSegments().get(i);
            assertTrue(segment.getEnd() > segment.getStart(), "Each subtitle segment needs its own time window");
            assertTrue(segment.getText().length() <= 60, "Each subtitle chunk should stay readable on a 9:16 video");
            if (i > 0) {
                assertTrue(segment.getStart() >= timeline.getSegments().get(i - 1).getEnd(),
                        "Subtitle chunks should be sequential instead of overlapping from the beginning");
            }
        }
    }

    @Test
    void buildTimelineUsesShorterChunksForLargeSocialSubtitleStyles() throws Exception {
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(10)
                .subtitleStyle("tiktok_large")
                .build();
        String script = "Stop scrolling because this tiny gadget fixes messy cables fast and keeps your desk looking clean";
        File missingAudio = new File("missing-audio-for-social-caption-fallback.mp3");

        TkSubtitleTimeline timeline = service.buildTimeline(task, script, missingAudio, Collections.emptyList());

        assertTrue(timeline.getSegments().size() >= 4,
                "Large social subtitle styles should create punchier short caption chunks");
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            assertTrue(segment.getText().split("\\s+").length <= 5,
                    "TikTok large captions should stay short enough for large text treatment");
        }
    }

    @Test
    void buildTimelineUsesOriginalScriptTextWhenAsrTextHasRecognizedTypos() throws Exception {
        Path asrScript = tempDir.resolve("fake-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"en-US\",\"audioDuration\":3.0,"
                        + "\"segments\":[{\"text\":\"Buy this writing serum today.\",\"start\":0.0,\"end\":3.0,"
                        + "\"words\":[{\"text\":\"Buy\",\"start\":0.0,\"end\":0.4,\"keyword\":False},"
                        + "{\"text\":\"this\",\"start\":0.4,\"end\":0.8,\"keyword\":False},"
                        + "{\"text\":\"writing\",\"start\":0.8,\"end\":1.6,\"keyword\":False},"
                        + "{\"text\":\"serum\",\"start\":1.6,\"end\":2.3,\"keyword\":False},"
                        + "{\"text\":\"today.\",\"start\":2.3,\"end\":3.0,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.55D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(3)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "Buy this whitening serum today.", audioFile.toFile(),
                Collections.emptyList());

        String text = String.join(" ", timeline.getSegments().stream()
                .map(TkSubtitleSegment::getText)
                .toArray(String[]::new));
        assertEquals("Buy this whitening serum today.", text);
        assertTrue(timeline.getSegments().stream().noneMatch(segment -> segment.getText().contains("writing")));
    }

    @Test
    void buildTimelineFallsBackToEstimatedTimelineWhenAsrTextDoesNotMatchOriginalScript() throws Exception {
        Path asrScript = tempDir.resolve("mismatch-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"en-US\",\"audioDuration\":4.0,"
                        + "\"segments\":[{\"text\":\"The voice provider rewrote the entire narration.\","
                        + "\"start\":0.0,\"end\":4.0,"
                        + "\"words\":[{\"text\":\"The\",\"start\":0.0,\"end\":0.4,\"keyword\":False},"
                        + "{\"text\":\"voice\",\"start\":0.4,\"end\":0.8,\"keyword\":False},"
                        + "{\"text\":\"provider\",\"start\":0.8,\"end\":1.4,\"keyword\":False},"
                        + "{\"text\":\"rewrote\",\"start\":1.4,\"end\":2.1,\"keyword\":False},"
                        + "{\"text\":\"everything\",\"start\":2.1,\"end\":4.0,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.55D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(4)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "Buy this whitening serum today.", audioFile.toFile(),
                Collections.emptyList());

        assertEquals("Buy this whitening serum today.", timeline.getSegments().get(0).getText());
        assertEquals(4.0D, timeline.getAudioDuration(), 0.001D);
        String quality = Files.readString(tempDir.resolve("subtitle-quality.json"));
        assertTrue(quality.contains("\"mode\":\"ESTIMATED_AFTER_ASR_MISMATCH\""));
        assertTrue(quality.contains("ASR text does not match original script"));
    }

    @Test
    void buildTimelineRetriesAsrWithConfiguredRetryModelBeforeEstimatedFallback() throws Exception {
        Path asrScript = tempDir.resolve("retry-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json, sys\n"
                        + "model = sys.argv[sys.argv.index('--model') + 1]\n"
                        + "text = 'Buy this whitening serum today.' if model == 'medium' else 'The voice provider rewrote the narration.'\n"
                        + "print(json.dumps({\"language\":\"en-US\",\"audioDuration\":4.0,"
                        + "\"segments\":[{\"text\":text,\"start\":0.0,\"end\":4.0,"
                        + "\"words\":[{\"text\":\"Buy\",\"start\":0.0,\"end\":0.5,\"keyword\":False},"
                        + "{\"text\":\"this\",\"start\":0.5,\"end\":1.0,\"keyword\":False},"
                        + "{\"text\":\"whitening\",\"start\":1.0,\"end\":2.0,\"keyword\":False},"
                        + "{\"text\":\"serum\",\"start\":2.0,\"end\":3.0,\"keyword\":False},"
                        + "{\"text\":\"today.\",\"start\":3.0,\"end\":4.0,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setModel("small");
        properties.getSubtitle().getAsr().setRetryEnabled(true);
        properties.getSubtitle().getAsr().setRetryModel("medium");
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.55D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(4)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "Buy this whitening serum today.", audioFile.toFile(),
                Collections.emptyList());

        assertEquals("Buy this whitening serum today.", timeline.getSegments().get(0).getText());
        assertTrue(Files.exists(tempDir.resolve("asr-retry-raw.json")));
        String quality = Files.readString(tempDir.resolve("subtitle-quality.json"));
        assertTrue(quality.contains("\"mode\":\"ASR_RETRY_EXACT\""));
    }

    @Test
    void buildTimelineMapsAsrWordTimingToOriginalScriptWords() throws Exception {
        Path asrScript = tempDir.resolve("timed-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"en-US\",\"audioDuration\":2.2,"
                        + "\"segments\":[{\"text\":\"Buy this writing serum today\",\"start\":0.0,\"end\":2.2,"
                        + "\"words\":[{\"text\":\"Buy\",\"start\":0.0,\"end\":0.3,\"keyword\":False},"
                        + "{\"text\":\"this\",\"start\":0.3,\"end\":0.6,\"keyword\":False},"
                        + "{\"text\":\"writing\",\"start\":0.6,\"end\":1.2,\"keyword\":False},"
                        + "{\"text\":\"serum\",\"start\":1.2,\"end\":1.8,\"keyword\":False},"
                        + "{\"text\":\"today\",\"start\":1.8,\"end\":2.2,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.55D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(3)
                .subtitleKaraokeEnabled(true)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "Buy this whitening serum today", audioFile.toFile(),
                Collections.emptyList());

        List<TkSubtitleWord> words = timeline.getSegments().get(0).getWords();
        assertEquals("Buy", words.get(0).getText());
        assertEquals("this", words.get(1).getText());
        assertEquals("whitening", words.get(2).getText());
        assertEquals(0.6D, words.get(2).getStart(), 0.001D);
        assertEquals(1.2D, words.get(2).getEnd(), 0.001D);
        assertEquals("serum", words.get(3).getText());
        assertEquals("today", words.get(4).getText());
    }

    @Test
    void buildTimelineAlignsOriginalChineseScriptWhenAsrUsesTraditionalTextAndOmitsWords() throws Exception {
        Path asrScript = tempDir.resolve("traditional-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"zh-CN\",\"audioDuration\":4.2,"
                        + "\"segments\":[{\"text\":\"團隊從五人擴招到八人\",\"start\":0.0,\"end\":4.2,"
                        + "\"words\":[{\"text\":\"團\",\"start\":0.0,\"end\":0.4,\"keyword\":False},"
                        + "{\"text\":\"隊\",\"start\":0.4,\"end\":0.8,\"keyword\":False},"
                        + "{\"text\":\"從\",\"start\":0.8,\"end\":1.2,\"keyword\":False},"
                        + "{\"text\":\"五\",\"start\":1.2,\"end\":1.6,\"keyword\":False},"
                        + "{\"text\":\"人\",\"start\":1.6,\"end\":2.0,\"keyword\":False},"
                        + "{\"text\":\"擴\",\"start\":2.0,\"end\":2.4,\"keyword\":False},"
                        + "{\"text\":\"招\",\"start\":2.4,\"end\":2.8,\"keyword\":False},"
                        + "{\"text\":\"到\",\"start\":2.8,\"end\":3.2,\"keyword\":False},"
                        + "{\"text\":\"八\",\"start\":3.2,\"end\":3.6,\"keyword\":False},"
                        + "{\"text\":\"人\",\"start\":3.6,\"end\":4.2,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.55D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("zh-CN")
                .targetDuration(5)
                .subtitleKaraokeEnabled(true)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "团队马上从5人扩招到8人", audioFile.toFile(),
                Collections.emptyList());

        List<TkSubtitleWord> words = timeline.getSegments().get(0).getWords();
        assertEquals("团", words.get(0).getText());
        assertEquals("5", words.get(5).getText());
        assertEquals(1.2D, words.get(5).getStart(), 0.001D);
        assertEquals(1.6D, words.get(5).getEnd(), 0.001D);
        assertEquals("8", words.get(10).getText());
        assertEquals(3.2D, words.get(10).getStart(), 0.001D);
        assertEquals(3.6D, words.get(10).getEnd(), 0.001D);
        assertEquals(4.2D, words.get(words.size() - 1).getEnd(), 0.001D);
    }

    @Test
    void buildTimelineKeepsAsrExactTimingWithoutGlobalSubtitleLead() throws Exception {
        Path asrScript = tempDir.resolve("lead-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"zh-CN\",\"audioDuration\":2.0,"
                        + "\"segments\":[{\"text\":\"团队提效\",\"start\":0.5,\"end\":1.5,"
                        + "\"words\":[{\"text\":\"团\",\"start\":0.5,\"end\":0.75,\"keyword\":False},"
                        + "{\"text\":\"队\",\"start\":0.75,\"end\":1.0,\"keyword\":False},"
                        + "{\"text\":\"提\",\"start\":1.0,\"end\":1.25,\"keyword\":False},"
                        + "{\"text\":\"效\",\"start\":1.25,\"end\":1.5,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0.2D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("zh-CN")
                .targetDuration(2)
                .subtitleKaraokeEnabled(true)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "团队提效", audioFile.toFile(),
                Collections.emptyList());

        assertEquals(0.5D, timeline.getSegments().get(0).getStart(), 0.001D);
        assertEquals(1.5D, timeline.getSegments().get(0).getEnd(), 0.001D);
        assertEquals(0.5D, timeline.getSegments().get(0).getWords().get(0).getStart(), 0.001D);
    }

    @Test
    void buildTimelineFallsBackWhenAsrAlignmentWouldStartSubtitlesInTheMiddle() throws Exception {
        Path asrScript = tempDir.resolve("late-asr.py");
        Files.write(asrScript, Collections.singletonList(
                "import json\n"
                        + "print(json.dumps({\"language\":\"en-US\",\"audioDuration\":42.0,"
                        + "\"segments\":[{\"text\":\"later words\",\"start\":18.0,\"end\":20.0,"
                        + "\"words\":[{\"text\":\"later\",\"start\":18.0,\"end\":19.0,\"keyword\":False},"
                        + "{\"text\":\"words\",\"start\":19.0,\"end\":20.0,\"keyword\":False}]}]}))"),
                StandardCharsets.UTF_8);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setPython("py");
        properties.getSubtitle().getAsr().setScriptPath(asrScript.toString());
        properties.getSubtitle().getAsr().setMinTextSimilarity(0.2D);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(42)
                .subtitleKaraokeEnabled(true)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "Opening hook before later words", audioFile.toFile(),
                Collections.emptyList());

        assertTrue(timeline.getSegments().get(0).getStart() < 1D,
                "Subtitle fallback should prevent a successful video from having no captions at the start");
        assertTrue(timeline.getSegments().get(timeline.getSegments().size() - 1).getEnd() >= 40D,
                "Fallback subtitles should still cover the full audio duration");
    }

    @Test
    void inspectSubtitleQualityRejectsTask421OverlappingSegments() {
        TkSubtitleTimeline timeline = new TkSubtitleTimeline();
        timeline.setAudioDuration(61.06D);
        timeline.setSegments(Arrays.asList(
                segment("越有本事的员工，", 0D, 1.30D),
                segment("越别急着换！他越不听话，", 0D, 0.31D),
                segment("越可能是你公司最值钱的人。", 0.31D, 0.88D),
                segment("质疑你的规则，甚至当面说这样做不行。", 0.88D, 1.39D),
                segment("后续字幕", 1.39D, 61.0D)
        ));

        Object quality = ReflectionTestUtils.invokeMethod(service, "inspectSubtitleQuality", timeline, null);

        assertFalse((Boolean) ReflectionTestUtils.getField(quality, "acceptable"));
        assertEquals("SUBTITLE_SEGMENT_OVERLAP", ReflectionTestUtils.getField(quality, "reason"));
    }

    @Test
    void buildTimelineFallsBackWhenExactAsrAlignmentIsEnabledButUnavailable() throws Exception {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().getAsr().setEnabled(true);
        properties.getSubtitle().getAsr().setScriptPath(tempDir.resolve("missing-asr.py").toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        Path audioFile = tempDir.resolve("voice.mp3");
        Files.write(audioFile, new byte[]{0});
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("zh-CN")
                .targetDuration(3)
                .subtitleEnabled(true)
                .build();

        TkSubtitleTimeline timeline = service.buildTimeline(task, "请立即下单", audioFile.toFile(), Collections.emptyList());

        assertFalse(timeline.getSegments().isEmpty());
        assertEquals("请立即下单", timeline.getSegments().get(0).getText());
    }

    @Test
    void buildEstimatedTimelineMarksWordTimingAsUnreliable() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .targetLanguage("en-US")
                .targetDuration(3)
                .build();

        TkSubtitleTimeline timeline = ReflectionTestUtils.invokeMethod(service, "buildEstimatedTimeline",
                task, "Limited offer today", null, Collections.emptyList(), 3D);

        assertFalse(timeline.getSegments().get(0).getWordTimingReliable(),
                "Estimated timeline words must not be used for karaoke animation");
    }

    @Test
    void buildSegmentsKeepsLastCaptionWithinAudioDurationAndPreservesEndingText() {
        List<String> sentences = Arrays.asList(
                "你是不是发现公司怎么加人产出",
                "还是上不去，重复的事情全靠人",
                "去堆。",
                "你看一眼这个多维表格的自动化",
                "输出，别再拿人当机器用了。",
                "把行业语料和对标框架喂进去，",
                "把业务逻辑搭好，一个完全不懂",
                "行的小白一天也能稳定出30条中",
                "等质量的文案。",
                "这就叫把个人经验固化成系统能",
                "力。",
                "你不需要每个员工都是行业老手",
                "，只要他们能按标准流程去点执",
                "行按钮就行。",
                "老板最大的浪费就是让团队每天",
                "都在做低水平的重复劳动，真正",
                "该做的是把这些事情交给系统去",
                "跑。",
                "我把这套逻辑整理了一份文档，",
                "98块拿走对照着改，评论提效我发你。");

        List<TkSubtitleSegment> segments = ReflectionTestUtils.invokeMethod(service, "buildSegments",
                sentences, 37.683542D, Collections.emptyList());

        assertEquals("98块拿走对照着改，评论提效我发你。", segments.get(segments.size() - 1).getText());
        assertEquals(37.683542D, segments.get(segments.size() - 1).getEnd(), 0.01D);
        assertTrue(segments.get(segments.size() - 1).getEnd() <= 37.683542D);
    }

    @Test
    void splitSentencesAvoidsBreakingCommonChinesePhrasesAndLeadingPunctuation() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("classic_white")
                .build();
        String script = "这就叫把个人经验固化成系统能力。你不需要每个员工都是行业老手，只要他们能按标准流程去点执行按钮就行。"
                + "我把这套逻辑整理了一份文档，98块拿走对照着改，评论提效我发你。";

        List<String> sentences = ReflectionTestUtils.invokeMethod(service, "splitSentences", task, script);

        assertTrue(sentences.stream().noneMatch(text -> text.startsWith("，") || text.startsWith("、")
                || text.startsWith("。") || text.startsWith("！") || text.startsWith("？") || text.startsWith("；")));
        assertTrue(sentences.stream().noneMatch(text -> text.endsWith("系统能")));
        assertTrue(sentences.stream().noneMatch(text -> text.endsWith("点执")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("系统能力")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("执行按钮")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("评论提效我发你")));
    }

    @Test
    void splitSentencesKeepsLeadGenerationPhrasesReadableForTask188Script() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("classic_white")
                .build();
        String script = "你还在靠招人来堆内容产量吗？招一堆新人，一个人一天也就勉强憋出一条低质视频。"
                + "其实用多维表格把自动化输出跑通，产量和质量马上就能上来。"
                + "我这边跑通的逻辑是，让一个小白不用加班，每天稳定产出30条高质量视频。"
                + "不是靠人盯，是靠系统把重复枯燥的活儿自动转了，人只负责最后的把关。"
                + "我把这套用多维表格提效的底层逻辑整理了一份文档，98块拿走对照着给你团队用上，别再花冤枉钱盲目加人了。";

        List<String> sentences = ReflectionTestUtils.invokeMethod(service, "splitSentences", task, script);

        assertTrue(sentences.stream().noneMatch(text -> text.endsWith("高质")
                || text.startsWith("量视频")
                || text.endsWith("提")
                || text.startsWith("效的")
                || text.endsWith("了一")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("低质视频")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("30条高质量视频")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("多维表格提效")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("底层逻辑")));
        assertTrue(sentences.stream().anyMatch(text -> text.contains("98块")));
    }

    @Test
    void smoothRapidSegmentsMergesFlickerCaptionsButKeepsWordTiming() {
        List<TkSubtitleSegment> segments = Arrays.asList(
                segment("A", 0.0D, 0.3D),
                segment("B", 0.3D, 0.6D),
                segment("C", 0.6D, 1.0D),
                segment("stable caption", 1.0D, 2.5D)
        );

        List<TkSubtitleSegment> smoothed = ReflectionTestUtils.invokeMethod(service, "smoothRapidSegments", segments);

        assertEquals(2, smoothed.size());
        assertEquals("ABC", smoothed.get(0).getText());
        assertEquals(0.0D, smoothed.get(0).getStart(), 0.001D);
        assertEquals(1.0D, smoothed.get(0).getEnd(), 0.001D);
        assertEquals(3, smoothed.get(0).getWords().size());
        assertEquals(0.3D, smoothed.get(0).getWords().get(1).getStart(), 0.001D);
        assertEquals("stable caption", smoothed.get(1).getText());
    }

    @Test
    void applySubtitleLeadMovesSegmentsAndWordsEarlierWithoutNegativeTime() {
        TkSubtitleTimeline timeline = new TkSubtitleTimeline();
        timeline.setAudioDuration(6D);
        List<TkSubtitleWord> words = new ArrayList<>();
        words.add(new TkSubtitleWord("开", 0.12D, 0.50D, false));
        words.add(new TkSubtitleWord("场", 0.50D, 1.20D, true));
        List<TkSubtitleSegment> segments = new ArrayList<>();
        segments.add(new TkSubtitleSegment("开场", 0.12D, 1.20D, null, 0, 0, words));
        segments.add(new TkSubtitleSegment("第二句", 1.40D, 2.80D, null, 0, 0, Collections.emptyList()));
        timeline.setSegments(segments);

        DefaultTkSubtitleTimelineService.applySubtitleLead(timeline, 0.20D);

        assertEquals(0D, timeline.getSegments().get(0).getStart());
        assertEquals(1.0D, timeline.getSegments().get(0).getEnd());
        assertEquals(0D, timeline.getSegments().get(0).getWords().get(0).getStart());
        assertEquals(0.30D, timeline.getSegments().get(0).getWords().get(0).getEnd());
        assertTrue(timeline.getSegments().get(0).getWords().get(1).isKeyword());
        assertEquals(1.20D, timeline.getSegments().get(1).getStart());
    }

    @Test
    void parseLeadingSilenceEndReadsOnlySilenceStartingAtZero() {
        String output = "[silencedetect @ 000001] silence_start: 0\n"
                + "[silencedetect @ 000001] silence_end: 0.317 | silence_duration: 0.317\n"
                + "[silencedetect @ 000001] silence_start: 4.2\n"
                + "[silencedetect @ 000001] silence_end: 4.8 | silence_duration: 0.6\n";

        assertEquals(0.317D, DefaultTkSubtitleTimelineService.parseLeadingSilenceEnd(output), 0.0001D);
        assertEquals(0D, DefaultTkSubtitleTimelineService.parseLeadingSilenceEnd(
                "silence_start: 2.0\nsilence_end: 2.4 | silence_duration: 0.4"));
    }

    @Test
    void asrTimelineMustMatchOriginalScriptBeforeBeingAccepted() {
        TkSubtitleTimeline unrelated = new TkSubtitleTimeline();
        unrelated.setSegments(Collections.singletonList(
                new TkSubtitleSegment(" completely unrelated words ", 0D, 2D, null, 0, 0, Collections.emptyList())));

        assertFalse(DefaultTkSubtitleTimelineService.isAsrTimelineTextAcceptable(
                unrelated, "你还在靠招人来堆内容产量吗？", 0.55D));

        TkSubtitleTimeline matched = new TkSubtitleTimeline();
        matched.setSegments(Collections.singletonList(
                new TkSubtitleSegment("你还在靠招人来堆内容产量吗", 0D, 2D, null, 0, 0, Collections.emptyList())));

        assertTrue(DefaultTkSubtitleTimelineService.isAsrTimelineTextAcceptable(
                matched, "你还在靠招人来堆内容产量吗？", 0.55D));

        TkSubtitleTimeline tooShort = new TkSubtitleTimeline();
        tooShort.setSegments(Collections.singletonList(
                new TkSubtitleSegment("你还在", 0D, 0.6D, null, 0, 0, Collections.emptyList())));

        assertFalse(DefaultTkSubtitleTimelineService.isAsrTimelineTextAcceptable(
                tooShort, "你还在靠招人来堆内容产量吗？", 0.55D));
    }

    private TkSubtitleSegment segment(String text, double start, double end) {
        TkSubtitleSegment segment = new TkSubtitleSegment();
        segment.setText(text);
        segment.setStart(start);
        segment.setEnd(end);
        segment.setWords(Collections.singletonList(new TkSubtitleWord(text, start, end, false)));
        return segment;
    }

}
