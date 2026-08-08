package cn.iocoder.yudao.module.tk.service.credit;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCreditLogDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCreditLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.GENERATION_TASK;
import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.REFERENCE_ANALYSIS;

@Slf4j
@Component
public class TkCreditReconciliationJob {

    private static final int SCAN_LIMIT = 100;
    private static final long GRACE_MINUTES = 10L;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private TkCreditLogMapper creditLogMapper;
    @Resource
    private TkReferenceAnalysisMapper referenceAnalysisMapper;
    @Resource
    private TkGenerationTaskMapper generationTaskMapper;
    @Resource
    private TkCreditService creditService;

    @TenantIgnore
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void reconcileFrozenCredits() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(GRACE_MINUTES);
        List<TkCreditLogDO> logs = creditLogMapper.selectInProgressBefore(deadline, SCAN_LIMIT);
        for (TkCreditLogDO creditLog : logs) {
            try {
                reconcile(creditLog);
            } catch (Exception ex) {
                log.warn("[reconcileFrozenCredits][logId({}) bizType({}) bizId({}) 积分补偿失败]",
                        creditLog.getId(), creditLog.getBizType(), creditLog.getBizId(), ex);
            }
        }
    }

    private void reconcile(TkCreditLogDO creditLog) {
        if (REFERENCE_ANALYSIS.equals(creditLog.getBizType())) {
            reconcileReferenceAnalysis(creditLog);
            return;
        }
        if (GENERATION_TASK.equals(creditLog.getBizType())) {
            reconcileGenerationTask(creditLog);
        }
    }

    private void reconcileReferenceAnalysis(TkCreditLogDO creditLog) {
        TkReferenceAnalysisDO analysis = referenceAnalysisMapper.selectById(creditLog.getBizId());
        if (analysis == null) {
            return;
        }
        if (STATUS_SUCCESS.equals(analysis.getStatus())) {
            creditService.settleByLogId(creditLog.getId());
            return;
        }
        if (STATUS_FAILED.equals(analysis.getStatus())) {
            creditService.refundByLogId(creditLog.getId(), analysis.getFailReason());
        }
    }

    private void reconcileGenerationTask(TkCreditLogDO creditLog) {
        TkGenerationTaskDO task = generationTaskMapper.selectById(creditLog.getBizId());
        if (task == null) {
            return;
        }
        if (TkGenerationStatusEnum.SUCCESS.equals(task.getStatus())) {
            creditService.settleByLogId(creditLog.getId());
            return;
        }
        if (TkGenerationStatusEnum.FAILED.equals(task.getStatus())) {
            creditService.refundByLogId(creditLog.getId(), task.getFailReason());
        }
    }

}
