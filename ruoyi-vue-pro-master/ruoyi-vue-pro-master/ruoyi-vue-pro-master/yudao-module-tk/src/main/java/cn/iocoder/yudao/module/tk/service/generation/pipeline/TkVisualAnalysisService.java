package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import java.util.List;
import java.io.File;

public interface TkVisualAnalysisService {

    TkVisualAnalysis analyze(File videoFile, List<TkClipPlanItem> clipPlan);

}
