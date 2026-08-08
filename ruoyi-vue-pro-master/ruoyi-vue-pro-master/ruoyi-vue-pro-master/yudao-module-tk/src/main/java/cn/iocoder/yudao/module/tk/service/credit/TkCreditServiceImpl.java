package cn.iocoder.yudao.module.tk.service.credit;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkCreditBalanceRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditRechargeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditSaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCreditLogDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTenantCreditAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCreditLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTenantCreditAccountMapper;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_CREDIT_NOT_ENOUGH;
import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.GENERATION_TASK;
import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.REFERENCE_ANALYSIS;
import static cn.iocoder.yudao.module.tk.enums.TkCreditBizTypeEnum.TENANT_RECHARGE;
import static cn.iocoder.yudao.module.tk.enums.TkCreditLogStatusEnum.*;

@Service
@Validated
public class TkCreditServiceImpl implements TkCreditService {

    private static final long DEFAULT_WARNING_THRESHOLD = 100L;
    private static final String PROVIDER_CREDIT = "CREDIT";
    private static final String KEY_REFERENCE_ANALYSIS_COST = "reference-analysis-cost";
    private static final String KEY_GENERATION_TASK_COST = "generation-task-cost";

    @Resource
    private TkTenantCreditAccountMapper creditAccountMapper;
    @Resource
    private TkCreditLogMapper creditLogMapper;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;
    @Resource
    private TenantService tenantService;
    @Resource
    private TenantMapper tenantMapper;
    @Resource
    private TkBusinessLogService businessLogService;

    @Override
    public TkCreditBalanceRespVO getCurrentTenantBalance() {
        Long tenantId = getVisitTenantId();
        if (tenantId == null) {
            tenantId = normalizeTenantId(TenantContextHolder.getTenantId());
        }
        if (tenantId == null) {
            LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
            tenantId = loginUser == null ? null : normalizeTenantId(loginUser.getTenantId());
        }
        if (tenantId == null) {
            throw new IllegalStateException("当前登录用户缺少租户编号，无法读取积分余额");
        }
        Long currentTenantId = tenantId;
        return TenantUtils.execute(currentTenantId, () -> getTenantBalance(currentTenantId));
    }

    @Override
    public TkCreditBalanceRespVO getTenantBalance(Long tenantId) {
        tenantId = requireValidTenantId(tenantId);
        return buildBalance(ensureAccount(tenantId));
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public void saveTenantCredit(TkTenantCreditSaveReqVO reqVO) {
        TkTenantCreditAccountDO account = ensureAccount(reqVO.getTenantId());
        long totalCredits = Math.min(Integer.MAX_VALUE, Math.max(0L, reqVO.getTotalCredits()));
        long oldTotal = account.getTotalCredits() == null ? 0L : account.getTotalCredits();
        long delta = totalCredits - oldTotal;
        long remaining = Math.max(0L, nullToZero(account.getRemainingCredits()) + delta);
        account.setTotalCredits(totalCredits);
        account.setRemainingCredits(remaining);
        account.setWarningThreshold(reqVO.getWarningThreshold() == null ? DEFAULT_WARNING_THRESHOLD : reqVO.getWarningThreshold());
        creditAccountMapper.updateById(account);
        tenantMapper.updateById(TenantDO.builder()
                .id(reqVO.getTenantId())
                .accountCount(Math.toIntExact(totalCredits))
                .build());
    }

    @Override
    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public void rechargeTenantCredit(TkTenantCreditRechargeReqVO reqVO) {
        long credits = reqVO.getCredits();
        TkTenantCreditAccountDO beforeAccount = ensureAccount(reqVO.getTenantId());
        long beforeTotal = nullToZero(beforeAccount.getTotalCredits());
        long beforeRemaining = nullToZero(beforeAccount.getRemainingCredits());
        long beforeFrozen = nullToZero(beforeAccount.getFrozenCredits());
        if (credits > Integer.MAX_VALUE - beforeTotal) {
            throw new IllegalArgumentException("充值后积分总额度不能超过 " + Integer.MAX_VALUE);
        }
        if (creditAccountMapper.rechargeCredits(reqVO.getTenantId(), credits, (long) Integer.MAX_VALUE) <= 0) {
            throw new IllegalArgumentException("积分充值失败，请刷新后重试");
        }
        TkTenantCreditAccountDO afterAccount = creditAccountMapper.selectByTenantId(reqVO.getTenantId());
        long afterTotal = nullToZero(afterAccount.getTotalCredits());
        long afterRemaining = nullToZero(afterAccount.getRemainingCredits());
        long afterFrozen = nullToZero(afterAccount.getFrozenCredits());
        tenantMapper.updateById(TenantDO.builder()
                .id(reqVO.getTenantId())
                .accountCount(Math.toIntExact(afterTotal))
                .build());
        TkCreditLogDO log = TkCreditLogDO.builder()
                .bizType(TENANT_RECHARGE)
                .action("RECHARGE")
                .credits(credits)
                .status(SETTLED)
                .beforeRemainingCredits(beforeRemaining)
                .afterRemainingCredits(afterRemaining)
                .beforeFrozenCredits(beforeFrozen)
                .afterFrozenCredits(afterFrozen)
                .remark(StrUtil.maxLength(StrUtil.blankToDefault(reqVO.getRemark(), "管理员增加积分"), 2000))
                .build();
        log.setTenantId(reqVO.getTenantId());
        creditLogMapper.insert(log);
        businessLogService.info("CREDIT", log.getId(), "RECHARGE", SETTLED,
                StrUtil.format("管理员增加积分：{} 积分，总额度 {} -> {}", credits, beforeTotal, afterTotal), log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long freezeForReferenceAnalysis(Long tenantId) {
        return freeze(tenantId, REFERENCE_ANALYSIS, configuredCost(KEY_REFERENCE_ANALYSIS_COST, COST_REFERENCE_ANALYSIS));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long freezeForGenerationTask(Long tenantId) {
        return freeze(tenantId, GENERATION_TASK, configuredCost(KEY_GENERATION_TASK_COST, COST_GENERATION_TASK));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindBusiness(Long logId, Long bizId) {
        if (logId == null || bizId == null) {
            return;
        }
        creditLogMapper.updateById(new TkCreditLogDO().setId(logId).setBizId(bizId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleByLogId(Long logId) {
        TkCreditLogDO log = creditLogMapper.selectInProgressById(logId);
        if (log == null) {
            return;
        }
        settleLog(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundByLogId(Long logId, String reason) {
        TkCreditLogDO log = creditLogMapper.selectInProgressById(logId);
        if (log == null) {
            return;
        }
        refundLog(log, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settle(String bizType, Long bizId) {
        TkCreditLogDO log = creditLogMapper.selectInProgress(bizType, bizId);
        if (log == null) {
            return;
        }
        settleLog(log);
    }

    private void settleLog(TkCreditLogDO log) {
        TenantUtils.execute(log.getTenantId(), () -> {
            if (creditAccountMapper.settleFrozenCredits(log.getTenantId(), log.getCredits()) <= 0) {
                return;
            }
            TkTenantCreditAccountDO account = creditAccountMapper.selectByTenantId(log.getTenantId());
            creditLogMapper.updateById(new TkCreditLogDO()
                    .setId(log.getId())
                    .setStatus(SETTLED)
                    .setAfterRemainingCredits(nullToZero(account.getRemainingCredits()))
                    .setAfterFrozenCredits(nullToZero(account.getFrozenCredits())));
            businessLogService.info("CREDIT", log.getId(), "SETTLE", SETTLED,
                    StrUtil.format("积分结算：{} 积分", log.getCredits()), log);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(String bizType, Long bizId, String reason) {
        TkCreditLogDO log = creditLogMapper.selectInProgress(bizType, bizId);
        if (log == null) {
            return;
        }
        refundLog(log, reason);
    }

    private void refundLog(TkCreditLogDO log, String reason) {
        TenantUtils.execute(log.getTenantId(), () -> {
            if (creditAccountMapper.refundFrozenCredits(log.getTenantId(), log.getCredits()) <= 0) {
                return;
            }
            TkTenantCreditAccountDO account = creditAccountMapper.selectByTenantId(log.getTenantId());
            creditLogMapper.updateById(new TkCreditLogDO()
                    .setId(log.getId())
                    .setStatus(REFUNDED)
                    .setAfterRemainingCredits(nullToZero(account.getRemainingCredits()))
                    .setAfterFrozenCredits(nullToZero(account.getFrozenCredits()))
                    .setRemark(StrUtil.maxLength(StrUtil.blankToDefault(reason, "任务失败返还积分"), 2000)));
            businessLogService.warn("CREDIT", log.getId(), "REFUND", REFUNDED,
                    StrUtil.format("积分返还：{} 积分，{}", log.getCredits(), StrUtil.blankToDefault(reason, "任务失败返还积分")), log);
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public Long freeze(Long tenantId, String bizType, long credits) {
        ensureAccount(tenantId);
        if (creditAccountMapper.freezeCredits(tenantId, credits) <= 0) {
            throw exception(TK_CREDIT_NOT_ENOUGH, credits);
        }
        TkTenantCreditAccountDO account = creditAccountMapper.selectByTenantId(tenantId);
        TkCreditLogDO log = TkCreditLogDO.builder()
                .bizType(bizType)
                .action("FREEZE")
                .credits(credits)
                .status(IN_PROGRESS)
                .beforeRemainingCredits(nullToZero(account.getRemainingCredits()) + credits)
                .afterRemainingCredits(nullToZero(account.getRemainingCredits()))
                .beforeFrozenCredits(Math.max(0L, nullToZero(account.getFrozenCredits()) - credits))
                .afterFrozenCredits(nullToZero(account.getFrozenCredits()))
                .remark("任务提交冻结积分")
                .build();
        log.setTenantId(tenantId);
        creditLogMapper.insert(log);
        businessLogService.info("CREDIT", log.getId(), "FREEZE", IN_PROGRESS,
                StrUtil.format("冻结积分：{} 积分", credits), log);
        return log.getId();
    }

    @TenantIgnore
    @Transactional(rollbackFor = Exception.class)
    public TkTenantCreditAccountDO ensureAccount(Long tenantId) {
        tenantId = requireValidTenantId(tenantId);
        long tenantAccountCredits = resolveTenantAccountCredits(tenantId, 0L);
        TkTenantCreditAccountDO account = creditAccountMapper.selectByTenantId(tenantId);
        if (account != null) {
            syncAccountTotal(account, tenantAccountCredits);
            return account;
        }
        account = TkTenantCreditAccountDO.builder()
                .totalCredits(tenantAccountCredits)
                .remainingCredits(tenantAccountCredits)
                .frozenCredits(0L)
                .warningThreshold(DEFAULT_WARNING_THRESHOLD)
                .build();
        account.setTenantId(tenantId);
        try {
            creditAccountMapper.insert(account);
        } catch (RuntimeException ignored) {
            account = creditAccountMapper.selectByTenantId(tenantId);
            if (account != null) {
                syncAccountTotal(account, tenantAccountCredits);
                return account;
            }
            throw ignored;
        }
        return account;
    }

    private Long getVisitTenantId() {
        HttpServletRequest request = WebFrameworkUtils.getRequest();
        return request == null ? null : normalizeTenantId(WebFrameworkUtils.getVisitTenantId(request));
    }

    private Long normalizeTenantId(Long tenantId) {
        return tenantId == null || tenantId <= 0 ? null : tenantId;
    }

    private Long requireValidTenantId(Long tenantId) {
        tenantId = normalizeTenantId(tenantId);
        if (tenantId == null) {
            throw new IllegalStateException("当前租户编号无效，无法读取积分余额");
        }
        return tenantId;
    }

    private void syncAccountTotal(TkTenantCreditAccountDO account, long totalCredits) {
        long oldTotal = nullToZero(account.getTotalCredits());
        if (oldTotal == totalCredits) {
            return;
        }
        long remaining = Math.max(0L, nullToZero(account.getRemainingCredits()) + totalCredits - oldTotal);
        account.setTotalCredits(totalCredits);
        account.setRemainingCredits(remaining);
        creditAccountMapper.updateById(account);
    }

    private long resolveTenantAccountCredits(Long tenantId, Long fallback) {
        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant != null && tenant.getAccountCount() != null) {
            return Math.max(0L, tenant.getAccountCount().longValue());
        }
        return Math.max(0L, fallback == null ? 0L : fallback);
    }

    private TkCreditBalanceRespVO buildBalance(TkTenantCreditAccountDO account) {
        TkCreditBalanceRespVO respVO = new TkCreditBalanceRespVO();
        respVO.setTenantId(account.getTenantId());
        respVO.setTotalCredits(nullToZero(account.getTotalCredits()));
        respVO.setRemainingCredits(nullToZero(account.getRemainingCredits()));
        respVO.setFrozenCredits(nullToZero(account.getFrozenCredits()));
        respVO.setWarningThreshold(account.getWarningThreshold() == null ? DEFAULT_WARNING_THRESHOLD : account.getWarningThreshold());
        respVO.setLowBalance(respVO.getRemainingCredits() < respVO.getWarningThreshold());
        return respVO;
    }

    private long configuredCost(String configKey, int defaultValue) {
        String value = apiKeyConfigService.getValue(PROVIDER_CREDIT, configKey);
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

}
