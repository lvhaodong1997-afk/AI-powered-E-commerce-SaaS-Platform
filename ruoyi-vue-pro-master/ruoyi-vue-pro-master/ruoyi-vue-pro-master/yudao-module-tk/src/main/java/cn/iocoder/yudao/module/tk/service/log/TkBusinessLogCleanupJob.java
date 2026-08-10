package cn.iocoder.yudao.module.tk.service.log;

import cn.iocoder.yudao.module.tk.dal.mysql.TkBusinessLogMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Component
@Slf4j
public class TkBusinessLogCleanupJob {

    @Resource
    private TkBusinessLogMapper businessLogMapper;
    @Resource
    private TkGenerationProperties generationProperties;

    @Scheduled(cron = "${tk.generation.cleanup.business-log-cron:0 45 3 * * ?}")
    public void cleanup() {
        TkGenerationProperties.Cleanup cleanup = generationProperties.getCleanup();
        if (cleanup == null || !Boolean.TRUE.equals(cleanup.getEnabled()) || Boolean.TRUE.equals(cleanup.getDryRun())) {
            return;
        }
        int retentionDays = cleanup.getBusinessLogRetentionDays() == null
                ? 30 : Math.max(1, cleanup.getBusinessLogRetentionDays());
        LocalDateTime deadline = LocalDateTime.now().minusDays(retentionDays);
        int deleted;
        int total = 0;
        do {
            deleted = TenantUtils.executeIgnore(() -> businessLogMapper.deleteExpired(deadline, 1000));
            total += deleted;
        } while (deleted == 1000);
        if (total > 0) {
            log.info("[cleanup][businessLogs({})]", total);
        }
    }
}
