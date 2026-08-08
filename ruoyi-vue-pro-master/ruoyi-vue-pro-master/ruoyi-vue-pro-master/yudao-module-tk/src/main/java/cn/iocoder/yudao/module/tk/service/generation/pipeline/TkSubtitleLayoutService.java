package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

public interface TkSubtitleLayoutService {

    TkSubtitleLayout layout(TkGenerationTaskDO task, TkSubtitleTimeline timeline, TkVisualAnalysis visualAnalysis);

}
