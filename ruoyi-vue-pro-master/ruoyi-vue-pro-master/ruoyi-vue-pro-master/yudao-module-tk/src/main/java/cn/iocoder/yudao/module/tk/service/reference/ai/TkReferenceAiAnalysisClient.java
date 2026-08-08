package cn.iocoder.yudao.module.tk.service.reference.ai;

public interface TkReferenceAiAnalysisClient {
    String provider();
    TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context);
}
