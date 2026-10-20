package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import lombok.Getter;
import java.util.Collections;
import java.util.List;

/** Describes the actual renderer decision; stored in the existing quality report and result log. */
@Getter
public class TkSubtitleKaraokeQuality {
    private final String status;
    private final String reason;
    private final int reliableSegments;
    private final int totalSegments;

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
        return new TkSubtitleKaraokeQuality(status, reason, reliable, segments.size());
    }
}
