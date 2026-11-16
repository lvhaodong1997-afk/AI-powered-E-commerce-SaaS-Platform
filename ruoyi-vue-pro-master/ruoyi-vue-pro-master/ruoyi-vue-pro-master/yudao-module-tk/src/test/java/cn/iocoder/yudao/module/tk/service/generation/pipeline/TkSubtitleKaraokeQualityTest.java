package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSubtitleKaraokeQualityTest {
    @Test void rejectsNegativeTiming() {
        TkSubtitleSegment s = segment("hello", -1, 1);
        assertFalse(DefaultTkAssSubtitleRenderService.canRenderKaraoke(s));
    }
    @Test void rejectsMismatchedWordText() {
        TkSubtitleSegment s = segment("hello", 0, 1);
        s.getWords().get(0).setText("different");
        assertFalse(DefaultTkAssSubtitleRenderService.canRenderKaraoke(s));
    }
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings={"zero","backward","nan-start","nan-end","infinite","outside","overlap","empty-words","null-words","estimated","negative-segment"})
    void rejectsUntrustworthyTiming(String kind) {
        TkSubtitleSegment s=segment("hello",0,1);
        TkSubtitleWord w=s.getWords().get(0);
        switch(kind) {
            case "zero": w.setEnd(0); break;
            case "backward": w.setEnd(-1); break;
            case "nan-start": w.setStart(Double.NaN); break;
            case "nan-end": w.setEnd(Double.NaN); break;
            case "infinite": w.setEnd(Double.POSITIVE_INFINITY); break;
            case "outside": w.setEnd(2); break;
            case "overlap": s.getWords().add(new TkSubtitleWord("hello",0.5,1,false)); break;
            case "empty-words": s.setWords(Collections.emptyList()); break;
            case "null-words": s.setWords(null); break;
            case "estimated": s.setWordTimingReliable(false); break;
            case "negative-segment": s.setStart(-1); break;
        }
        assertFalse(DefaultTkAssSubtitleRenderService.canRenderKaraoke(s),kind);
    }
    @org.junit.jupiter.params.ParameterizedTest(name="valid {0}")
    @org.junit.jupiter.params.provider.ValueSource(strings={"hello","你好","120","12.5%","你好，世界！","Hello 世界","go go","A/B","“新鲜”","Hi!"})
    void acceptsActualGroupedWords(String text) {
        assertTrue(DefaultTkAssSubtitleRenderService.canRenderKaraoke(segment(text,0,1)));
    }
    private TkSubtitleSegment segment(String text, double start, double end) {
        return new TkSubtitleSegment(text,start,end,"bottom",0,0,
                new ArrayList<>(Collections.singletonList(new TkSubtitleWord(text,start,end,false))));
    }
}
