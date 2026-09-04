package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TkOpenTiktokPublishStatusJob {
    private final TkOpenTiktokPublishService publishService;
    public TkOpenTiktokPublishStatusJob(TkOpenTiktokPublishService publishService) { this.publishService = publishService; }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.publish.status-delay-ms:120000}", initialDelay = 60000)
    public void sync() {
        publishService.resumeStalePending(100);
        publishService.syncStale(100);
    }
}
