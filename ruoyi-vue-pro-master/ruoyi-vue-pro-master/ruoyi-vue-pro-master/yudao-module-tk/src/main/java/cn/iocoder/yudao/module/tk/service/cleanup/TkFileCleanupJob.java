package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;

@Slf4j
@Component
public class TkFileCleanupJob implements AutoCloseable {

    // Both cleanup crons share one worker; slow IO must never hold the shared Spring scheduler.
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(), runnable -> new Thread(runnable, "tk-file-cleanup"),
            new ThreadPoolExecutor.AbortPolicy());

    @Resource
    private TkFileCleanupService cleanupService;

    @TenantIgnore
    @Scheduled(cron = "${tk.generation.cleanup.cron:0 5 * * * ?}")
    public void cleanupExpiredFiles() {
        submit(this::runExpiredFiles);
    }

    private void runExpiredFiles() {
        try {
            TkFileCleanupService.CleanupResult result = cleanupService.cleanupExpiredFiles();
            if (result.getGeneratedFileCount() > 0 || result.getGenerationOpeningFileCount() > 0
                    || result.getReferenceFileCount() > 0) {
                log.info("[cleanupExpiredFiles][generatedFileCount({}) generationOpeningFileCount({}) "
                                + "referenceFileCount({})]",
                        result.getGeneratedFileCount(), result.getGenerationOpeningFileCount(),
                        result.getReferenceFileCount());
            }
        } catch (Exception ex) {
            log.warn("[cleanupExpiredFiles][TK 过期文件自动清理失败]", ex);
        }
    }

    @TenantIgnore
    @Scheduled(cron = "${tk.generation.cleanup.daily-cron:0 10 3 * * ?}")
    public void cleanupExpiredDailyFiles() {
        submit(this::runExpiredDailyFiles);
    }

    private void runExpiredDailyFiles() {
        try {
            int publishMediaCount = cleanupService.cleanupExpiredPublishMedia();
            int transcriptAudioCount = cleanupService.cleanupExpiredTranscriptAudio();
            if (publishMediaCount > 0 || transcriptAudioCount > 0) {
                log.info("[cleanupExpiredDailyFiles][publishMediaCount({}) transcriptAudioCount({})]",
                        publishMediaCount, transcriptAudioCount);
            }
        } catch (Exception ex) {
            log.warn("[cleanupExpiredDailyFiles][TK 每日上传媒体清理失败]", ex);
        }
    }

    private void submit(Runnable action) {
        try { worker.execute(() -> TenantUtils.executeIgnore(action)); }
        catch (RejectedExecutionException busy) {
            log.debug("[submit][cleanup already running or shutting down; skipped trigger]");
        }
    }

    @Override @PreDestroy
    public void close() { worker.shutdownNow(); }
}
