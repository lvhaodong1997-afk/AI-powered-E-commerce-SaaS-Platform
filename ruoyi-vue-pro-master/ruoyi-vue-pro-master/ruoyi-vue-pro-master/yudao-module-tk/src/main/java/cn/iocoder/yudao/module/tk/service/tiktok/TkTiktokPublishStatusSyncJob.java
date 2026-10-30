package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TkTiktokPublishStatusSyncJob {
    private static final int SCAN_LIMIT = 50;
    private final AtomicBoolean statusRunning = new AtomicBoolean();
    private final AtomicBoolean dispatchRunning = new AtomicBoolean();
    @Resource private TkTiktokPublishService publishService;

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.tiktok.publish.status-delay-ms:10000}", initialDelay = 60000)
    public void syncProcessingStatus() {
        if (!statusRunning.compareAndSet(false, true)) return;
        try {
            publishService.syncStaleProcessingStatus(SCAN_LIMIT);
        } catch (Exception ex) {
            log.warn("[syncProcessingStatus][TikTok status sync deferred]", ex);
        } finally { statusRunning.set(false); }
    }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.tiktok.publish.schedule-delay-ms:120000}", initialDelay = 60000)
    public void dispatchScheduled() {
        if (!dispatchRunning.compareAndSet(false, true)) return;
        try {
            try { publishService.dispatchDueScheduled(SCAN_LIMIT); }
            catch (Exception ex) { log.warn("[dispatchScheduled][scheduled scan deferred]", ex); }
            try { publishService.resumePendingPublishTasks(SCAN_LIMIT); }
            catch (Exception ex) { log.warn("[dispatchScheduled][pending scan deferred]", ex); }
        } finally { dispatchRunning.set(false); }
    }
}
