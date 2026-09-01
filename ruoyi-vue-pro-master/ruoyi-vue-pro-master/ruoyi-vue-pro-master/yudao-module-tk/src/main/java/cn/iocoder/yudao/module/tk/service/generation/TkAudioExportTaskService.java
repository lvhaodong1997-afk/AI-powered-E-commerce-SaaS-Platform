package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskRespVO;

public interface TkAudioExportTaskService {

    TkAudioExportTaskRespVO export(TkAudioExportTaskCreateReqVO reqVO);
}
