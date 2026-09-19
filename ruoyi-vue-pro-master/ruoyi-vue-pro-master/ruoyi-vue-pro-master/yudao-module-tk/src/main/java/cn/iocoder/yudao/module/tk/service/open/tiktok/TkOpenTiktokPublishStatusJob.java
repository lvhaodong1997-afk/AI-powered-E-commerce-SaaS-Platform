package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component @Slf4j
public class TkOpenTiktokPublishStatusJob {
    private final TkOpenTiktokPublishService publishService;
    public TkOpenTiktokPublishStatusJob(TkOpenTiktokPublishService publishService) { this.publishService = publishService; }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.publish.status-delay-ms:120000}", initialDelay = 60000)
    public void sync() {
        run("journal", () -> publishService.recoverJournal(100));
        run("pending", () -> publishService.resumeStalePending(100));
        run("status", () -> publishService.syncStale(100));
        run("recovery", () -> publishService.reconcileRecoveryRequired(100));
        run("callbacks", () -> publishService.reconcileTerminalCallbacks(100));
    }
    private void run(String phase, Runnable action) {
        try { action.run(); }
        catch (Exception ex) { log.warn("[sync][phase({}) deferred; other recovery phases continue]", phase, ex); }
    }
}
