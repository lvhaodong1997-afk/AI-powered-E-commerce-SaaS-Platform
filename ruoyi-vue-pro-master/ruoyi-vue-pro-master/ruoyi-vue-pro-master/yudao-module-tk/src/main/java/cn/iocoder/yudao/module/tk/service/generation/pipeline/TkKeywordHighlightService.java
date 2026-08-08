package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.util.List;

public interface TkKeywordHighlightService {

    List<String> resolveKeywords(TkGenerationTaskDO task, String scriptText);

}
