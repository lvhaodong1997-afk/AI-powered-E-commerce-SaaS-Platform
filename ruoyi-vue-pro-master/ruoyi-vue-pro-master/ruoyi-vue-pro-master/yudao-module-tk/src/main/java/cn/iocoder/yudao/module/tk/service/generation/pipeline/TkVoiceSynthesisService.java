package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

public interface TkVoiceSynthesisService {

    TkAudioAsset synthesize(TkGenerationTaskDO task, String scriptText);

}
