package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchDetailRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationStepLogRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskSummaryRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationBatchMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationStepLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_GENERATION_TASK_NOT_EXISTS;

@Service
@Validated
public class TkGenerationBatchServiceImpl implements TkGenerationBatchService {

    @Resource
    private TkGenerationBatchMapper batchMapper;
    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkGenerationStepLogMapper stepLogMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkGenerationTaskService generationTaskService;

    @Override
    public PageResult<TkGenerationBatchDO> getBatchPage(TkGenerationBatchPageReqVO reqVO) {
        return batchMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public TkGenerationBatchDetailRespVO getBatchDetail(Long id) {
        TkGenerationBatchDO batch = validateBatchReadable(id);
        TkUserScope scope = dataScopeService.getCurrentScope();
        List<TkGenerationTaskDO> tasks = taskMapper.selectListByBatchId(id, scope);
        return new TkGenerationBatchDetailRespVO(
                BeanUtils.toBean(batch, TkGenerationBatchRespVO.class),
                BeanUtils.toBean(tasks, TkGenerationTaskSummaryRespVO.class),
                BeanUtils.toBean(stepLogMapper.selectListByBatchId(id), TkGenerationStepLogRespVO.class)
        );
    }

    @Override
    public void refreshBatchProgress(Long batchId) {
        if (batchId == null) {
            return;
        }
        TkGenerationBatchDO batch = batchMapper.selectById(batchId);
        if (batch == null) {
            return;
        }
        TkUserScope scope = new TkUserScope(null, batch.getTenantId(), "PLATFORM_ADMIN", null);
        List<TkGenerationTaskDO> tasks = taskMapper.selectListByBatchId(batchId, scope);
        TkGenerationBatchProgressSupport.BatchProgress progress =
                TkGenerationBatchProgressSupport.summarize(batch.getExpectedVideoCount(), tasks);
        batchMapper.updateById(new TkGenerationBatchDO()
                .setId(batchId)
                .setCreatedTaskCount(progress.getCreatedCount())
                .setSuccessTaskCount(progress.getSuccessCount())
                .setFailedTaskCount(progress.getFailedCount())
                .setRunningTaskCount(progress.getRunningCount())
                .setProgressPercent(progress.getProgressPercent())
                .setStatus(progress.getStatus())
                .setFailSummary(buildFailSummary(tasks)));
    }

    @Override
    public Integer retryFailedTasks(Long batchId) {
        validateBatchReadable(batchId);
        List<TkGenerationTaskDO> failedTasks = taskMapper.selectListByBatchId(batchId, dataScopeService.getCurrentScope())
                .stream()
                .filter(task -> TkGenerationStatusEnum.FAILED.equals(task.getStatus()))
                .collect(Collectors.toList());
        for (TkGenerationTaskDO task : failedTasks) {
            generationTaskService.retryGenerationTask(task.getId());
        }
        refreshBatchProgress(batchId);
        return failedTasks.size();
    }

    private TkGenerationBatchDO validateBatchReadable(Long id) {
        TkGenerationBatchDO batch = batchMapper.selectById(id);
        if (batch == null) {
            throw exception(TK_GENERATION_TASK_NOT_EXISTS);
        }
        dataScopeService.validateReadable(batch.getTenantId(), batch.getCompanyId(), batch.getCreator());
        return batch;
    }

    private String buildFailSummary(List<TkGenerationTaskDO> tasks) {
        return tasks.stream()
                .filter(task -> TkGenerationStatusEnum.FAILED.equals(task.getStatus()))
                .map(task -> StrUtil.blankToDefault(task.getFailCode(), task.getFailReason()))
                .filter(StrUtil::isNotBlank)
                .limit(3)
                .collect(Collectors.joining("; "));
    }
}
