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

}
