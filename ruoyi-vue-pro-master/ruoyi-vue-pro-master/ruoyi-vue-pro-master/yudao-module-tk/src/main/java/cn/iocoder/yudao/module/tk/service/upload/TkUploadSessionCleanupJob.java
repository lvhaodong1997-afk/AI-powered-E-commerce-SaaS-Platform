package cn.iocoder.yudao.module.tk.service.upload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class TkUploadSessionCleanupJob {

    @Resource
    private TkUploadSessionService uploadSessionService;

    @Scheduled(cron = "${tk.generation.upload.session-cleanup-cron:0 25 * * * ?}")
    public void cleanup() {
        try {
            int count = uploadSessionService.expireSessions();
            if (count > 0) {
                log.info("[cleanup][expiredUploadSessions({})]", count);
            }
        } catch (Exception ex) {
            log.warn("[cleanup][expired upload session cleanup failed]", ex);
        }
    }
}
