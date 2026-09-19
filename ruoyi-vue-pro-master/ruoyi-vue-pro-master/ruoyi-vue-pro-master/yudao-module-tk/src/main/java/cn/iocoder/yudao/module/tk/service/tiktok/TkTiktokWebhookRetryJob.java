package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TkTiktokWebhookRetryJob {
    private final TkTiktokWebhookServiceImpl webhookService;

    public TkTiktokWebhookRetryJob(TkTiktokWebhookServiceImpl webhookService) {
        this.webhookService = webhookService;
    }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.tiktok.webhook.retry-delay-ms:60000}",
            initialDelayString = "${tk.tiktok.webhook.retry-initial-delay-ms:60000}")
    public void retry() {
        webhookService.retryPending(100);
    }
}
