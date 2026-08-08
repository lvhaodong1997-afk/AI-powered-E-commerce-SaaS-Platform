package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface TkGenerationTaskService {

    Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO);

    List<Long> createGenerationTasks(TkGenerationTaskCreateReqVO createReqVO);

    Long createGenerationTask(TkGenerationTaskCreateReqVO createReqVO, MultipartFile openingVideoFile);

    void retryGenerationTask(Long id);

    PageResult<TkGenerationTaskDO> getGenerationTaskPage(TkGenerationTaskPageReqVO pageReqVO);

    PageResult<TkGenerationTaskDO> getGenerationTaskSummaryPage(TkGenerationTaskPageReqVO pageReqVO);

    Map<Long, Integer> getDailyUserVideoNoMap(Collection<TkGenerationTaskDO> tasks);

    List<TkGenerationTaskDO> getGenerationTaskStatusBatch(Collection<Long> ids);

    TkGenerationTaskDO getGenerationTask(Long id);

}
