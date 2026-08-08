package cn.iocoder.yudao.module.tk.service.reference.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TkReferenceAiAnalysisResult {
    private String provider;
    private String model;
    private String content;
}
