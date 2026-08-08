package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.util.List;

public interface TkClipPlannerService {

    List<TkClipPlanItem> plan(TkGenerationTaskDO task, String scriptText);

    default List<TkClipPlanItem> plan(TkGenerationTaskDO task, String scriptText, Integer effectiveTargetDuration) {
        return plan(task, scriptText);
    }

    default List<TkClipPlanItem> replanTailForLowDynamic(TkGenerationTaskDO task, List<TkClipPlanItem> originalPlan,
                                                         Integer effectiveTargetDuration) {
        return originalPlan;
    }

}
