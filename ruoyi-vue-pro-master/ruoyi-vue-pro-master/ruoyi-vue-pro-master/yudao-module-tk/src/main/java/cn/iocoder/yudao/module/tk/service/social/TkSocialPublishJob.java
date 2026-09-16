package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishDetailMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Component
@Slf4j
@ConditionalOnProperty(prefix="tk.social",name="enabled",havingValue="true")
public class TkSocialPublishJob {
    private final TkSocialPublishDetailMapper details;
    private final TkSocialPublishWorker worker;
    private final Set<Long> queued=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),r -> { Thread t=new Thread(r,"tk-meta-publish"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    public TkSocialPublishJob(TkSocialPublishDetailMapper details,TkSocialPublishWorker worker) {
        this.details=details; this.worker=worker;
    }
    @Scheduled(fixedDelay=5000,initialDelay=10000)
    public void tick() {
        try {
            List<TkSocialPublishDetailDO> expired=TenantUtils.executeIgnore(() -> details.selectList(
                    new QueryWrapper<TkSocialPublishDetailDO>().in("status","PROCESSING","UNKNOWN")
                            .le("lease_until",LocalDateTime.now()).orderByAsc("lease_until").last("LIMIT 32")));
            for (TkSocialPublishDetailDO detail:expired) {
                try { TenantUtils.execute(detail.getTenantId(),() -> worker.expire(detail)); }
                catch (RuntimeException ex) { log.warn("Meta lease recovery failed, detailId={}",detail.getId()); }
            }
            List<TkSocialPublishDetailDO> due=TenantUtils.executeIgnore(() -> details.selectList(
                    new QueryWrapper<TkSocialPublishDetailDO>().in("status","PENDING","UNKNOWN")
                            .le("next_retry_time",LocalDateTime.now()).orderByAsc("next_retry_time","id").last("LIMIT 32")));
            for (TkSocialPublishDetailDO detail:due) {
                if (!queued.add(detail.getId())) continue;
                try {
                    executor.execute(() -> {
                        try { TenantUtils.execute(detail.getTenantId(),() -> worker.process(detail.getId(),detail.getTenantId())); }
                        catch (RuntimeException ex) { log.warn("Meta worker failed, detailId={}",detail.getId()); }
                        finally { queued.remove(detail.getId()); }
                    });
                } catch (RejectedExecutionException ex) { queued.remove(detail.getId()); break; }
            }
        } catch (RuntimeException ex) { log.warn("Meta queue scan failed; check database migration and connectivity"); }
    }
    @PreDestroy public void close() {
        executor.shutdown();
        try { if (!executor.awaitTermination(30,TimeUnit.SECONDS)) executor.shutdownNow(); }
        catch (InterruptedException ex) { executor.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
