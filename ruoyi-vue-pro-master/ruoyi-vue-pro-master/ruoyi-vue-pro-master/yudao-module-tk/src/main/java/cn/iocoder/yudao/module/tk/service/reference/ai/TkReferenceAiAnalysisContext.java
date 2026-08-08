package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkAiImageInput;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TkReferenceAiAnalysisContext {
    private String prompt;
    private String resolvedVideoUrl;
    private List<TkAiImageInput> images;
}
