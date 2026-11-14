package cn.iocoder.yudao.module.system.service.tenant;

/** Optional product extension. Must join the caller's Spring transaction, retaining locks through tenant writes. */
public interface TenantCreditQuotaUpdater {
    void applyAbsoluteQuota(Long tenantId, Integer totalCredits);
}
