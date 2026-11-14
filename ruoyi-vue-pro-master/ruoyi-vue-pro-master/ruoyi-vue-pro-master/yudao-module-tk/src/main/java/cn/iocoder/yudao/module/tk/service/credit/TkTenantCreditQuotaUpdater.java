package cn.iocoder.yudao.module.tk.service.credit;

import cn.iocoder.yudao.module.system.service.tenant.TenantCreditQuotaUpdater;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkTenantCreditSaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTenantCreditAccountDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class TkTenantCreditQuotaUpdater implements TenantCreditQuotaUpdater {
    private final TkCreditServiceImpl credits;
    public TkTenantCreditQuotaUpdater(@org.springframework.context.annotation.Lazy TkCreditServiceImpl credits){this.credits=credits;}
    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void applyAbsoluteQuota(Long tenantId,Integer totalCredits){
        if(totalCredits==null || totalCredits<0)throw new IllegalArgumentException("积分额度无效");
        TkTenantCreditAccountDO account=credits.ensureAccount(tenantId);
        TkTenantCreditSaveReqVO request=new TkTenantCreditSaveReqVO();request.setTenantId(tenantId);request.setTotalCredits(totalCredits.longValue());request.setWarningThreshold(account.getWarningThreshold());
        credits.saveTenantCredit(request);
    }
}
