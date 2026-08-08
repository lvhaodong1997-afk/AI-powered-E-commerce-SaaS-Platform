package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class TkFileCleanupJob {

    @Resource
    private TkFileCleanupService cleanupService;

    @TenantIgnore
    @Scheduled(cron = "${tk.generation.cleanup.cron:0 5 * * * ?}")
    public void cleanupExpiredFiles() {
        try {
            TkFileCleanupService.CleanupResult result = cleanupService.cleanupExpiredFiles();
            if (result.getGeneratedFileCount() > 0 || result.getReferenceFileCount() > 0) {
                log.info("[cleanupExpiredFiles][generatedFileCount({}) referenceFileCount({})]",
                        result.getGeneratedFileCount(), result.getReferenceFileCount());
            }
        } catch (Exception ex) {
            log.warn("[cleanupExpiredFiles][TK 过期文件自动清理失败]", ex);
        }
    }
}
