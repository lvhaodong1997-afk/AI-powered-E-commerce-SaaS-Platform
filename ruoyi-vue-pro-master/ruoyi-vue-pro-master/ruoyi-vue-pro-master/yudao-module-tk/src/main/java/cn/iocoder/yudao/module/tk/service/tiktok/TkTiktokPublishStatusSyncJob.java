package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class TkTiktokPublishStatusSyncJob {

    private static final int SCAN_LIMIT = 50;

    @Resource
    private TkTiktokPublishService publishService;

    @TenantIgnore
    @Scheduled(fixedDelay = 2 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void syncProcessingStatus() {
        try {
            int scheduledCount = publishService.dispatchDueScheduled(SCAN_LIMIT);
            int pendingCount = publishService.resumePendingPublishTasks(SCAN_LIMIT);
            int count = publishService.syncStaleProcessingStatus(SCAN_LIMIT);
            if (scheduledCount > 0 || pendingCount > 0 || count > 0) {
                log.info("[syncProcessingStatus][scheduledCount({}), pendingCount({}), processingCount({})]",
                        scheduledCount, pendingCount, count);
            }
        } catch (Exception ex) {
            log.warn("[syncProcessingStatus][TikTok 发布状态自动同步失败]", ex);
        }
    }

}
