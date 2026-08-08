package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TkGeneratedScript {

    private String title;
    private String content;
    private String segmentTimeline;
    private Integer referenceDuration;
    private Integer targetDuration;

    public TkGeneratedScript(String title, String content, Integer referenceDuration, Integer targetDuration) {
        this(title, content, null, referenceDuration, targetDuration);
    }

    public TkGeneratedScript(String title, String content, String segmentTimeline,
                             Integer referenceDuration, Integer targetDuration) {
        this.title = title;
        this.content = content;
        this.segmentTimeline = segmentTimeline;
        this.referenceDuration = referenceDuration;
        this.targetDuration = targetDuration;
    }

}
