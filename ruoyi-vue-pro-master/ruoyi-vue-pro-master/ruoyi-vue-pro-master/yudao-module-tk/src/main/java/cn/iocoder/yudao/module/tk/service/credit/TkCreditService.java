package cn.iocoder.yudao.module.tk.service.credit;

import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkCreditBalanceRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditRechargeReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditSaveReqVO;

public interface TkCreditService {

    int COST_REFERENCE_ANALYSIS = 1;
    int COST_GENERATION_TASK = 1;

    TkCreditBalanceRespVO getCurrentTenantBalance();

    TkCreditBalanceRespVO getTenantBalance(Long tenantId);

    void saveTenantCredit(TkTenantCreditSaveReqVO reqVO);

    void rechargeTenantCredit(TkTenantCreditRechargeReqVO reqVO);

    Long freezeForReferenceAnalysis(Long tenantId);

    Long freezeForGenerationTask(Long tenantId);

    void bindBusiness(Long logId, Long bizId);

    void settleByLogId(Long logId);

    void refundByLogId(Long logId, String reason);

    void settle(String bizType, Long bizId);

    void refund(String bizType, Long bizId, String reason);

}
