package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokConnectionTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class TkTiktokTokenRefreshJob {

    private static final int BATCH_LIMIT = 50;

    @Resource
    private TkTiktokTokenService tokenService;
    @Resource
    private TkOpenTiktokConnectionTokenService openConnectionTokenService;

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.tiktok.token-refresh.fixed-delay-ms:1800000}",
            initialDelayString = "${tk.tiktok.token-refresh.initial-delay-ms:120000}")
    public void refreshExpiringTokens() {
        int count = 0;
        int openCount = 0;
        try {
            count = tokenService.refreshExpiringActiveAccounts(BATCH_LIMIT);
        } catch (Exception ex) {
            log.warn("[refreshExpiringTokens][TikTok account token pre-refresh failed, errorType({})]",
                    ex.getClass().getSimpleName());
        }
        try {
            openCount = openConnectionTokenService.refreshExpiringAuthorizedConnections(BATCH_LIMIT);
        } catch (Exception ex) {
            log.warn("[refreshExpiringTokens][Open TikTok connection token pre-refresh failed, errorType({})]",
                    ex.getClass().getSimpleName());
        }
        if (count > 0 || openCount > 0) {
            log.info("[refreshExpiringTokens][accountCount({}) openConnectionCount({})]", count, openCount);
        }
    }

}
