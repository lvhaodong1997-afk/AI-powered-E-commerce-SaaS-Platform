package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

public interface TkVideoTailQualityService {

    TkVideoTailQualityReport inspect(TkGenerationTaskDO task, TkRenderResult renderResult);

}
