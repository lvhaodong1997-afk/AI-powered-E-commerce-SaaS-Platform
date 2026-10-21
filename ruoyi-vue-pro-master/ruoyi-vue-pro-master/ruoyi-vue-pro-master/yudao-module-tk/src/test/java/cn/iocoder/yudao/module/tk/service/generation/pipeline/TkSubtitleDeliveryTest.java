package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSubtitleDeliveryTest {
    @TempDir Path dir;
    final DefaultTkAssSubtitleRenderService renderer = new DefaultTkAssSubtitleRenderService();
    String render(String size, String style, boolean karaoke, TkSubtitleSegment segment) throws Exception {
        Path ass = dir.resolve(size + "-" + style + "-" + karaoke + ".ass");
        renderer.render(TkGenerationTaskDO.builder().subtitleFontSize(size).subtitleStyle(style)
                .subtitleKaraokeEnabled(karaoke).subtitleActiveColor("#35F27A").subtitleKeywordColor("#FFD84D")
                .subtitleKeywordEnabled(true).build(), new TkSubtitleLayout(List.of(segment)), ass.toFile());
        return Files.readString(ass);
    }
    TkSubtitleSegment caption() {
        return new TkSubtitleSegment("Hello useful world", 2, 5, "bottom_center", 540, 1450,
                List.of(new TkSubtitleWord("Hello", 2.3, 2.8, false),
                        new TkSubtitleWord("useful", 3.4, 3.8, true), new TkSubtitleWord("world", 4.4, 4.9, false)));
    }
    String[] style(String ass) {
        return ass.lines().filter(s -> s.startsWith("Style:")).findFirst().orElseThrow().split(",");
    }
    @Test void fourFinalSizesReachActualAssAcrossEveryPreset() throws Exception {
        String[] sizes = {"small", "medium", "large", "extra_large"};
        int[] expected = {58, 68, 86, 96};
        for (int i = 0; i < sizes.length; i++) {
            for (String preset : List.of("classic_white", "yellow_keyword", "tiktok_large", "promo_bold",
                    "clean_product", "neon_pop", "yellow_story", "price_flash", "step_card", "brand_minimal", "comment_bubble")) {
                assertEquals(expected[i], Integer.parseInt(style(render(sizes[i], preset, false, caption()))[2]),
                        sizes[i] + " / " + preset);
            }
        }
    }
    @Test void fontsUseRealSansAndSerifWithRegularMinimalWeight() throws Exception {
        assertEquals("Noto Sans CJK SC", style(render("medium", "classic_white", false, caption()))[1]);
        String[] brand = style(render("medium", "brand_minimal", false, caption()));
        assertEquals("Noto Serif CJK SC", brand[1]);
        assertEquals("0", brand[7]);
    }
    @Test void karaokeSweepsFromPlainIntoActiveAndKeywordWinsBothColors() throws Exception {
        String ass = render("medium", "classic_white", true, caption());
        assertEquals("&H007AF235", style(ass)[3]);
        assertEquals("&H00FFFFFF", style(ass)[4]);
        assertTrue(ass.contains("\\1c&H004DD8FF\\2c&H004DD8FF"));
        String plain = render("medium", "classic_white", false, caption());
        assertEquals("&H00FFFFFF", style(plain)[3]);
        assertFalse(plain.contains("\\kf"));
    }
    @Test void absoluteKaraokeOffsetsPreserveInitialAndInterwordGapsAcrossWraps() throws Exception {
        TkSubtitleSegment c = caption();
        c.setText("Hello useful wonderful world today");
        c.setWords(List.of(new TkSubtitleWord("Hello",2.3,2.8,false), new TkSubtitleWord("useful",3.4,3.8,false),
                new TkSubtitleWord("wonderful",4,4.2,false),new TkSubtitleWord("world",4.4,4.6,false),new TkSubtitleWord("today",4.7,4.9,false)));
        String ass=render("extra_large","tiktok_large",true,c);
        assertTrue(ass.contains("{\\k30}{\\kf50}"));
        assertTrue(ass.contains("{\\k60}{\\kf40}"));
        assertTrue(ass.contains("\\N"));
        assertTrue(ass.contains("{\\k20}{\\kf20}"));
    }
    @Test void invalidWordTimesCannotProduceKaraoke() throws Exception {
        TkSubtitleSegment c=caption(); c.setWords(List.of(new TkSubtitleWord("Hello",Double.NaN,3,false)));
        assertFalse(render("medium","classic_white",true,c).contains("\\kf"));
    }
    @Test void mismatchOnlyDisablesItsOwnAuthoredSegment() {
        TkSubtitleTimeline t=rebuild("Good fruit\nFresh daily", List.of("Good","wrong","Fresh","daily"));
        assertFalse(t.getSegments().get(0).getWordTimingReliable());
        assertTrue(t.getSegments().get(1).getWordTimingReliable());
        assertEquals("Good fruit",t.getSegments().get(0).getText());
    }
    @Test void phraseTimingAndEqualLengthSubstitutionsAreNeverExact() {
        assertFalse(rebuild("Good fruit",List.of("Bad","fruit")).getSegments().get(0).getWordTimingReliable());
        assertFalse(rebuild("Good fruit",List.of("Good fruit")).getSegments().get(0).getWordTimingReliable());
    }
    @Test void extraLargeTwoLineTopCaptionStaysInsideSafeRegion() {
        TkSubtitleTimeline t=new TkSubtitleTimeline();t.setSegments(List.of(caption(),caption()));
        TkSubtitleLayout l=new DefaultTkSubtitleLayoutService().layout(TkGenerationTaskDO.builder()
                .subtitlePositionMode("alternate").subtitleFontSize("extra_large").subtitleStyle("tiktok_large").build(),t,null);
        assertTrue(l.getSegments().get(1).getY() >= 380);
    }
    @Test void fallbackDiagnosticExplicitlyReportsDisabledKaraoke() throws Exception {
        cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties properties=new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties();
        properties.getSubtitle().getAsr().setEnabled(false); properties.getSubtitle().setLeadSeconds(0D);
        properties.getSubtitle().setDetectLeadingSilenceEnabled(false);
        DefaultTkSubtitleTimelineService service=new DefaultTkSubtitleTimelineService();ReflectionTestUtils.setField(service,"generationProperties",properties);
        Path audio=dir.resolve("missing.wav");
        service.buildTimeline(TkGenerationTaskDO.builder().targetDuration(3).subtitleKaraokeEnabled(true).build(),
                "Good fruit",audio.toFile(),List.of());
        com.fasterxml.jackson.databind.JsonNode report=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(Files.readString(dir.resolve("subtitle-quality.json")));
        assertEquals("disabled",report.path("karaokeStatus").asText());
        assertFalse(report.path("karaokeReason").asText().isEmpty());
    }
    @Test void chineseAsrWordGroupsKeepTheirOriginalTimes() {
        TkSubtitleTimeline t=rebuild("清新水果\n每天新鲜",List.of("清新","水果","每天","新鲜"));
        assertTrue(t.getSegments().get(0).getWordTimingReliable());
        assertEquals(2,t.getSegments().get(0).getWords().size());
        assertEquals("清新",t.getSegments().get(0).getWords().get(0).getText());
        assertEquals(.7,t.getSegments().get(0).getWords().get(0).getEnd());
    }
    @Test void extraLargeEnglishTimelineSplitsBeforeAThirdLineIsNeeded() {
        String script="Wonderful products improve everyday comfort with breathable material and lasting support";
        TkGenerationTaskDO task=TkGenerationTaskDO.builder().subtitleFontSize("extra_large").subtitleStyle("tiktok_large").build();
        List<String> chunks=ReflectionTestUtils.invokeMethod(new DefaultTkSubtitleTimelineService(),"splitSentences",task,script);
        assertEquals(script,String.join(" ",chunks));
        for(String chunk:chunks) {
            int lines=1,width=0;
            for(String word:chunk.split(" ")) {
                if(width>0 && width+1+word.length()>13) {lines++;width=0;}
                width+=word.length()+(width>0?1:0);
            }
            assertTrue(lines<=2,"Extra large caption requires "+lines+" lines: "+chunk);
        }
    }
    @Test void finalAssFilterIncludesFontDirectoryAndEscapesSpecialPathCharacters() {
        java.io.File subtitle=new java.io.File("pause [probe],it's.ass");
        List<String> command=DefaultTkVideoRenderService.buildFinalRenderCommand("ffmpeg",new java.io.File("in.mp4"),null,
                null,.1,3,subtitle,new java.io.File("out.mp4"),"fast",0,"fonts [licensed],it's");
        String filter=command.get(command.indexOf("-vf")+1);
        assertTrue(filter.contains(":fontsdir="));
        assertTrue(filter.contains("\\[licensed\\]\\,it\\\\\\'s"));
        assertFalse(filter.contains("\\kt"));
    }
    @Test void emitReproducibleJavaAssSamples() throws Exception {
        String output=System.getProperty("tk.subtitle.samples");
        Path destination=output==null?dir:Path.of(output);Files.createDirectories(destination);
        for(String size:List.of("small","medium","large","extra_large")) {
        for(String style:List.of("classic_white","tiktok_large","yellow_story","brand_minimal","promo_bold","clean_product")) {
            TkGenerationTaskDO task=TkGenerationTaskDO.builder().subtitleEnabled(true).subtitleFontSize(size)
                    .subtitleStyle(style).subtitlePositionMode("fixed_bottom").subtitleKaraokeEnabled(true)
                    .subtitleKeywordEnabled(true).subtitleActiveColor("#35F27A").subtitleKeywordColor("#FFD84D").build();
            List<TkSubtitleSegment> segments=new ArrayList<>();
            segments.add(sample("只选成熟芒果厚切烘干锁住自然果香",List.of("只选","成熟","芒果","厚切","烘干","锁住","自然","果香"),0,7));
            segments.add(sample("Fresh fruit daily comfort",List.of("Fresh","fruit","daily","comfort"),8,13));
            TkSubtitleSegment estimated=sample("时间不足时保留普通字幕",List.of("时间","不足","时","保留","普通","字幕"),14,20);
            estimated.setWordTimingReliable(false);segments.add(estimated);
            TkSubtitleSegment price = new TkSubtitleSegment("一斤装 39.8",21,24,"bottom_center",540,1450,
                    List.of(new TkSubtitleWord("39.",21,22,true),new TkSubtitleWord("8",22,23,true)));
            price.setWordTimingReliable(false);segments.add(price);
            TkSubtitleTimeline timeline=new TkSubtitleTimeline();timeline.setSegments(segments);
            Path file=destination.resolve("task-1-sample-"+size+"-"+style+".ass");
            renderer.render(task,new DefaultTkSubtitleLayoutService().layout(task,timeline,null),file.toFile());
            String ass=Files.readString(file);
            assertTrue(ass.contains("{\\k40}{\\kf45}"));assertFalse(ass.contains("\\kt"));
            assertTrue(ass.contains("\\N"));assertTrue(ass.contains("Noto "));
            assertEquals("partial",TkSubtitleKaraokeQuality.inspect(task,timeline).getStatus());
        }
        }
        TkSubtitleSegment legacy=sample("Wonderful products improve everyday comfort with breathable material and lasting support",
                List.of("Wonderful","products","improve","everyday","comfort","with","breathable","material","and","lasting","support"),0,10);
        Path legacyFile=destination.resolve("task-1-sample-legacy-english-xl.ass");
        renderer.render(TkGenerationTaskDO.builder().subtitleFontSize("extra_large").subtitleStyle("tiktok_large")
                .subtitleKaraokeEnabled(true).build(),new TkSubtitleLayout(List.of(legacy)),legacyFile.toFile());
        String legacyAss=Files.readString(legacyFile);
        assertTrue(legacyAss.lines().filter(line->line.startsWith("Dialogue:")).count()>=3);
        assertFalse(legacyAss.contains("\\fscx18"));
        assertTrue(legacyAss.contains("Wonderful"));
        assertTrue(legacyAss.contains("support"));
    }
    TkSubtitleSegment sample(String text,List<String> units,double start,double end) {
        List<TkSubtitleWord> words=new ArrayList<>();
        for(int i=0;i<units.size();i++)words.add(new TkSubtitleWord(units.get(i),start+.4+i*.7,start+.85+i*.7,i==2));
        return new TkSubtitleSegment(text,start,end,"bottom_center",540,1450,words);
    }
    TkSubtitleTimeline rebuild(String script,List<String> tokens) {
        List<TkSubtitleWord> words=new ArrayList<>();
        for(int i=0;i<tokens.size();i++) words.add(new TkSubtitleWord(tokens.get(i),i,i+.7,false));
        TkSubtitleTimeline asr=new TkSubtitleTimeline();asr.setAudioDuration(tokens.size());
        asr.setSegments(List.of(new TkSubtitleSegment(String.join(" ",tokens),0,tokens.size(),null,0,0,words)));
        return ReflectionTestUtils.invokeMethod(new DefaultTkSubtitleTimelineService(),"rebuildExactTimelineWithScriptText",
                TkGenerationTaskDO.builder().targetLanguage("en-us").build(),script,asr,Collections.emptyList());
    }
}
