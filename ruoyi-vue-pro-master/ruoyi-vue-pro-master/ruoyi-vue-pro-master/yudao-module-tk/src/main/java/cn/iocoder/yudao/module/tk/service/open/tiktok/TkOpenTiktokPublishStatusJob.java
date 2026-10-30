package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.PreDestroy;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component @Slf4j
public class TkOpenTiktokPublishStatusJob {
    private final TkOpenTiktokPublishService publishService;
    private final ExecutorService status = lane("tk-publish-status");
    private final ExecutorService recovery = lane("tk-publish-recovery");
    private final ExecutorService watchdog = lane("tk-publish-watchdog");
    private final ExecutorService callbacks = lane("tk-publish-callback-repair");
    public TkOpenTiktokPublishStatusJob(TkOpenTiktokPublishService publishService) { this.publishService = publishService; }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.publish.status-delay-ms:10000}", initialDelay = 60000)
    public void sync() {
        submit(status, () -> {
            run("journal", () -> publishService.recoverJournal(100));
            run("status", () -> publishService.syncStale(100));
        });
        submit(recovery, () -> run("recovery", () -> publishService.reconcileRecoveryRequired(100)));
        submit(callbacks, () -> run("callbacks", () -> publishService.reconcileTerminalCallbacks(100)));
    }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.publish.watchdog-delay-ms:30000}", initialDelay = 30000)
    public void watchdog() {
        submit(watchdog, () -> run("watchdog", () -> publishService.recoverExpiredLeases(100)));
    }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.publish.schedule-delay-ms:10000}", initialDelay = 10000)
    public void dispatchScheduled() {
        run("scheduled", () -> publishService.dispatchDueScheduled(100));
        run("pending", () -> publishService.resumeStalePending(100));
    }

    private static ExecutorService lane(String name) {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                runnable -> new Thread(runnable, name), new ThreadPoolExecutor.AbortPolicy());
    }

    private void submit(ExecutorService lane, Runnable action) {
        try { lane.execute(() -> TenantUtils.executeIgnore(action)); }
        catch (RejectedExecutionException busy) { /* One scan is already running; no timer backlog. */ }
    }

    @PreDestroy public void destroy() {
        for (ExecutorService lane : Arrays.asList(status, recovery, watchdog, callbacks)) lane.shutdownNow();
    }
    private void run(String phase, Runnable action) {
        try { action.run(); }
        catch (Exception ex) { log.warn("[sync][phase({}) deferred; other recovery phases continue]", phase, ex); }
    }
}
