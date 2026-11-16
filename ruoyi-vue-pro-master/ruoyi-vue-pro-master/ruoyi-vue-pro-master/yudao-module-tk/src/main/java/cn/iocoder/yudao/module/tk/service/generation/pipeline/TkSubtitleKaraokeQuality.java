package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import lombok.Getter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;

/** Describes the actual renderer decision; stored in the existing quality report and result log. */
@Getter
public class TkSubtitleKaraokeQuality {
    private final String status;
    private final String reason;
    private final int reliableSegments;
    private final int totalSegments;
    private int reliableSpokenCharacters;
    private int totalSpokenCharacters;
    private double reliableSpokenTextCoverage;
    private final List<Map<String, Object>> segments = new ArrayList<>();

    static String spokenText(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }


    private TkSubtitleKaraokeQuality(String status, String reason, int reliableSegments, int totalSegments) {
        this.status = status;
        this.reason = reason;
        this.reliableSegments = reliableSegments;
        this.totalSegments = totalSegments;
    }

    static TkSubtitleKaraokeQuality inspect(TkGenerationTaskDO task, TkSubtitleTimeline timeline) {
        List<TkSubtitleSegment> segments = timeline == null || timeline.getSegments() == null
                ? Collections.emptyList() : timeline.getSegments();
        int reliable = (int) segments.stream().filter(DefaultTkAssSubtitleRenderService::canRenderKaraoke).count();
        boolean requested = !Boolean.FALSE.equals(task.getSubtitleEnabled())
                && Boolean.TRUE.equals(task.getSubtitleKaraokeEnabled());
        String status = !requested ? "off" : reliable == 0 ? "disabled"
                : reliable == segments.size() ? "enabled" : "partial";
        String reason = !requested || "enabled".equals(status) ? ""
                : "WORD_TIMING_UNRELIABLE: " + (segments.size() - reliable) + "/" + segments.size()
                + " segments use plain captions; ASR words were unavailable, mismatched, interpolated or invalid";
        TkSubtitleKaraokeQuality quality = new TkSubtitleKaraokeQuality(status, reason, reliable, segments.size());
        for (int i = 0; i < segments.size(); i++) {
            TkSubtitleSegment segment = segments.get(i);
            String decision = DefaultTkAssSubtitleRenderService.karaokeTimingReason(segment);
            int characters = spokenText(segment.getText()).codePointCount(0, spokenText(segment.getText()).length());
            int trusted = "OK".equals(decision) ? characters : 0;
            quality.totalSpokenCharacters += characters;
            quality.reliableSpokenCharacters += trusted;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("index", i);
            detail.put("reason", decision);
            detail.put("reliableSpokenCharacters", trusted);
            detail.put("totalSpokenCharacters", characters);
            detail.put("reliableSpokenTextCoverage", characters == 0 ? 0D : (double) trusted / characters);
            quality.segments.add(detail);
        }
        quality.reliableSpokenTextCoverage = quality.totalSpokenCharacters == 0 ? 0D
                : (double) quality.reliableSpokenCharacters / quality.totalSpokenCharacters;
        return quality;
    }
}
