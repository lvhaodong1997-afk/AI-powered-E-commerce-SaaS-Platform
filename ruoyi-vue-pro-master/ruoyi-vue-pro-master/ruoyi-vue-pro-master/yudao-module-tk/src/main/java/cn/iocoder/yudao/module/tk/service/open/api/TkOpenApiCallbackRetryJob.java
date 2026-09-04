package cn.iocoder.yudao.module.tk.service.open.api;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TkOpenApiCallbackRetryJob {
    private final TkOpenApiCallbackService callbackService;

    public TkOpenApiCallbackRetryJob(TkOpenApiCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @TenantIgnore
    @Scheduled(fixedDelayString = "${tk.open-api.callback.retry-delay-ms:60000}",
            initialDelayString = "${tk.open-api.callback.retry-initial-delay-ms:60000}")
    public void retry() {
        callbackService.deliverPending(100);
    }
}
