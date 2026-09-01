package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.util.List;

public interface TkVideoRenderService {

    TkRenderResult render(TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan);

    default TkRenderResult render(TkGenerationTaskDO task, List<TkClipPlanItem> clipPlan,
                                  TkRenderProgressReporter progressReporter) {
        return render(task, clipPlan);
    }

}
