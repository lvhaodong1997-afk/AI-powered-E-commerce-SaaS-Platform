package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.io.File;

public interface TkAssSubtitleRenderService {

    File render(TkGenerationTaskDO task, TkSubtitleLayout layout, File targetFile);

}
