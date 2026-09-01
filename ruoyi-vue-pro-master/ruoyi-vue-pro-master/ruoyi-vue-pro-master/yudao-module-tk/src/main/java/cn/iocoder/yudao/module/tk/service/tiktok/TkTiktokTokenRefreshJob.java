package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
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

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.tiktok.token-refresh.fixed-delay-ms:1800000}",
            initialDelayString = "${tk.tiktok.token-refresh.initial-delay-ms:120000}")
    public void refreshExpiringTokens() {
        try {
            int count = tokenService.refreshExpiringActiveAccounts(BATCH_LIMIT);
            if (count > 0) {
                log.info("[refreshExpiringTokens][count({})]", count);
            }
        } catch (Exception ex) {
            log.warn("[refreshExpiringTokens][TikTok Token 预刷新失败，errorType({})]",
                    ex.getClass().getSimpleName());
        }
    }

}
