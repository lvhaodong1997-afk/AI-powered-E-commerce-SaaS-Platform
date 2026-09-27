package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class TkScheduledMediaCleanupJob {

    @Resource
    private TkScheduledMediaCleanupService cleanupService;
    @Resource
    private TkGenerationProperties generationProperties;

    @TenantIgnore
    @Scheduled(cron = "${tk.generation.cleanup.scheduled-media-cron:0 */10 * * * ?}")
    public void cleanupTerminalScheduledMedia() {
        if (generationProperties.getCleanup() == null
                || !Boolean.TRUE.equals(generationProperties.getCleanup().getEnabled())
                || Boolean.TRUE.equals(generationProperties.getCleanup().getDryRun())) {
            return;
        }
        try {
            int batchSize = generationProperties.getCleanup().getBatchSize() == null
                    ? 100 : generationProperties.getCleanup().getBatchSize();
            int cleaned = cleanupService.cleanupTerminalScheduledMedia(batchSize);
            if (cleaned > 0) {
                log.info("[cleanupTerminalScheduledMedia][cleanedCount({})]", cleaned);
            }
        } catch (Exception ex) {
            log.warn("[cleanupTerminalScheduledMedia][定时发布素材补偿清理失败]", ex);
        }
    }
}
