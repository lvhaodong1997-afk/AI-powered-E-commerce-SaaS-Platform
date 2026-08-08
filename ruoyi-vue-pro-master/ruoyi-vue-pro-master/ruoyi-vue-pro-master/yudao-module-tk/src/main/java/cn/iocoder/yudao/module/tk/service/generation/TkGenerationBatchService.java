package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchDetailRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;

public interface TkGenerationBatchService {

    PageResult<TkGenerationBatchDO> getBatchPage(TkGenerationBatchPageReqVO reqVO);

    TkGenerationBatchDetailRespVO getBatchDetail(Long id);

    void refreshBatchProgress(Long batchId);

    Integer retryFailedTasks(Long batchId);
}
