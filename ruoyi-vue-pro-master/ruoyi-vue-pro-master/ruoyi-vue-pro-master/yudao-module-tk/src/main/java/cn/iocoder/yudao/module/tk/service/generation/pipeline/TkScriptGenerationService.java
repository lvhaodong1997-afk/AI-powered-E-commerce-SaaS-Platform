package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;

public interface TkScriptGenerationService {

    TkGeneratedScript generateScript(TkGenerationTaskDO task, TkMaterialLibraryDO library);

}
