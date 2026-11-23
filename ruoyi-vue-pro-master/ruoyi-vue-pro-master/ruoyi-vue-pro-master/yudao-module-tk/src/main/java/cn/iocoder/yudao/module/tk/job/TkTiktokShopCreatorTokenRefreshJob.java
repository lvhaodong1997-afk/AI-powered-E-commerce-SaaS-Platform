package cn.iocoder.yudao.module.tk.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAccountMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopApiException;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class TkTiktokShopCreatorTokenRefreshJob {
    private final TkTiktokShopProperties properties;
    private final TkTiktokShopAccountMapper accounts;
    private final TkTiktokShopTokenService tokens;
    private final int leadMinutes;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tk-shop-creator-renewal");
        thread.setDaemon(true);
        return thread;
    });

    public TkTiktokShopCreatorTokenRefreshJob(TkTiktokShopProperties properties, TkTiktokShopAccountMapper accounts,
            TkTiktokShopTokenService tokens, @Value("${tk.tiktok-shop.creator-refresh.lead-minutes:30}") int leadMinutes) {
        this.properties = properties;
        this.accounts = accounts;
        this.tokens = tokens;
        this.leadMinutes = Math.max(0, Math.min(120, leadMinutes));
    }

    @Scheduled(fixedDelayString = "${tk.tiktok-shop.creator-refresh.interval-ms:600000}", initialDelay = 60000)
    public void tick() {
        if (leadMinutes == 0 || !properties.isConfigured() || worker.isShutdown() || !busy.compareAndSet(false, true)) return;
        worker.execute(() -> {
            try { scan(); }
            catch (RuntimeException e) { log.warn("TikTok Shop creator renewal scan failed: {}", e.getClass().getSimpleName()); }
            finally { busy.set(false); }
        });
    }

    void scan() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<TkTiktokShopAccountDO> due = TenantUtils.executeIgnore(() -> accounts.dueForRefresh(properties.getAppKey(), now, now.plusMinutes(leadMinutes)));
        for (TkTiktokShopAccountDO account : due) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                TenantUtils.execute(account.getTenantId(), () -> tokens.refreshInBackground(
                        account.getTenantId(), account.getCompanyId(), account.getId(), leadMinutes));
            } catch (TkTiktokShopApiException e) {
                log.warn("TikTok Shop creator renewal account={} code={} requestId={}", account.getId(), e.getCode(), e.getRequestId());
            } catch (RuntimeException e) {
                log.warn("TikTok Shop creator renewal account={} failed: {}", account.getId(), e.getClass().getSimpleName());
            }
        }
    }

    @PreDestroy
    public void close() { worker.shutdownNow(); }
}
