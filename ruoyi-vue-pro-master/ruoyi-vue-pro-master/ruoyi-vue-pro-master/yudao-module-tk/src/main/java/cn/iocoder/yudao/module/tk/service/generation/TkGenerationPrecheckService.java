package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;

public interface TkGenerationPrecheckService {

    TkGenerationPrecheckRespVO precheck(TkGenerationTaskCreateReqVO createReqVO);

}
