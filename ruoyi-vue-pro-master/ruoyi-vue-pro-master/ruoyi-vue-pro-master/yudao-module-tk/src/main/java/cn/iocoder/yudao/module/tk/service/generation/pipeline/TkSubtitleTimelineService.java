package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;

import java.io.File;
import java.util.List;

public interface TkSubtitleTimelineService {

    TkSubtitleTimeline buildTimeline(TkGenerationTaskDO task, String scriptText, File audioFile, List<String> keywords);

}
