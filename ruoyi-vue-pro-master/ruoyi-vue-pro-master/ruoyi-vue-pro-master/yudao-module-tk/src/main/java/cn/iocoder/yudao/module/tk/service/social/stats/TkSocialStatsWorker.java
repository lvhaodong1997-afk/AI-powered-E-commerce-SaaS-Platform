package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialStatsDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialStatsRepository;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
public class TkSocialStatsWorker {
    private final TkSocialStatsRepository repository;
    private final TkSocialStatsService service;
    private final TkSocialStatsProperties properties;
    private final TkSocialProperties social;
    private final Semaphore slots=new Semaphore(2);
    private final ExecutorService executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{
        Thread t=new Thread(r,"meta-statistics"); t.setDaemon(true); return t;
    },new ThreadPoolExecutor.AbortPolicy());
    private boolean nextMedia;
    private volatile long lastWarning;

    public TkSocialStatsWorker(TkSocialStatsRepository repository,TkSocialStatsService service,TkSocialStatsProperties properties,TkSocialProperties social) {
        this.repository=repository; this.service=service; this.properties=properties; this.social=social;
    }

    @Scheduled(fixedDelayString="${tk.social.stats.poll-delay-ms:5000}",initialDelayString="${tk.social.stats.initial-delay-ms:30000}")
    public synchronized void poll() {
        if(!properties.isEnabled() || !social.isEnabled() || executor.isShutdown()) return;
        try {
            int seeded=repository.seed(properties.getSeedBatchSize());
            if(seeded>0) log.info("Meta statistics initialized {} snapshot rows",seeded);
            // Alternate first choice so a large account backlog cannot starve published media.
            boolean first=nextMedia; nextMedia=!nextMedia;
            for(boolean isMedia:new boolean[]{first,!first}) {
                if(!slots.tryAcquire()) break;
                boolean submitted=false;
                try {
                    LocalDateTime now=LocalDateTime.now();
                    List<TkSocialStatsDO> due=repository.due(isMedia,1,now);
                    if(due.isEmpty()) continue;
                    TkSocialStatsDO row=due.get(0); String lease=UUID.randomUUID().toString();
                    // At most 8 sequential provider reads x (10s connect + 20s read). Keep >=10min.
                    if(!repository.claim(isMedia,row.getTenantId(),row.getObjectId(),lease,now,now.plusSeconds(Math.max(600,properties.getLeaseSeconds())))) continue;
                    executor.execute(()->{
                        try { TenantUtils.execute(row.getTenantId(),()->service.refresh(isMedia,row,lease)); }
                        catch(RuntimeException ignored) { warn(); /* Lease expiry safely recovers DB/worker failures. */ }
                        finally { slots.release(); }
                    });
                    submitted=true;
                } finally { if(!submitted) slots.release(); }
            }
        } catch(RuntimeException ignored) { warn(); }
    }

    private void warn() {
        long now=System.currentTimeMillis();
        if(now-lastWarning>60000) {
            lastWarning=now;
            log.warn("Meta statistics polling could not complete; check statistics migration and database availability");
        }
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
