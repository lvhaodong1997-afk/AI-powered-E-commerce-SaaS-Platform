package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkRenderResult {

    private String outputUrl;
    private String subtitleUrl;
    private String subtitleTimelineUrl;
    private String subtitleVisualAnalysisUrl;
    private String subtitleLayoutUrl;
    private String subtitleAssUrl;
    private String subtitleAsrRawUrl;
    private String subtitleQualityUrl;
    private String subtitleKaraokeStatus;
    private String subtitleKaraokeReason;

    public TkRenderResult(String outputUrl, String subtitleUrl, String subtitleTimelineUrl,
                          String subtitleVisualAnalysisUrl, String subtitleLayoutUrl, String subtitleAssUrl,
                          String subtitleAsrRawUrl, String subtitleQualityUrl) {
        this.outputUrl = outputUrl;
        this.subtitleUrl = subtitleUrl;
        this.subtitleTimelineUrl = subtitleTimelineUrl;
        this.subtitleVisualAnalysisUrl = subtitleVisualAnalysisUrl;
        this.subtitleLayoutUrl = subtitleLayoutUrl;
        this.subtitleAssUrl = subtitleAssUrl;
        this.subtitleAsrRawUrl = subtitleAsrRawUrl;
        this.subtitleQualityUrl = subtitleQualityUrl;
    }

}
