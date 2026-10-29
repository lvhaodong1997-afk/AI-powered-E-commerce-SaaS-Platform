package cn.iocoder.yudao.module.tk.job;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.tiktokshop.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;

@Component
public class TkTiktokShopPublishStatusJob {
    private final TkTiktokShopProperties properties;private final TkTiktokShopPublishTaskMapper tasks;private final TkTiktokShopAuthSessionMapper sessions;
    private final TkTiktokShopPublishWorker worker;private final TkTiktokShopMediaService media;private final ThreadPoolExecutor pool;
    public TkTiktokShopPublishStatusJob(TkTiktokShopProperties properties,TkTiktokShopPublishTaskMapper tasks,TkTiktokShopAuthSessionMapper sessions,TkTiktokShopPublishWorker worker,TkTiktokShopMediaService media){this.properties=properties;this.tasks=tasks;this.sessions=sessions;this.worker=worker;this.media=media;int count=Math.max(1,Math.min(4,properties.getWorkers()));pool=new ThreadPoolExecutor(count,count,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(count),r->{Thread thread=new Thread(r,"tk-shop-publish");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());}
    @Scheduled(fixedDelay=5000,initialDelay=15000) public void tick(){
        if(!properties.isPublishingConfigured())return;
        List<TkTiktokShopPublishTaskDO> due=TenantUtils.executeIgnore(()->tasks.due(now()));
        for(TkTiktokShopPublishTaskDO task:due)try{pool.execute(()->{try{worker.runOne(task);}catch(Exception ignored){/* Durable stage and lease handle recovery; never log response bodies. */}});}catch(RejectedExecutionException ignored){break;}
    }
    @Scheduled(fixedDelay=3600000,initialDelay=60000) public void cleanup(){
        if(!properties.isConfigured())return;
        TenantUtils.executeIgnore(()->sessions.cleanupExpired(now().minusDays(7)));
        // Terminal tasks are immutable. Never delete pending, sending, processing or unknown-outcome media.
        List<TkTiktokShopPublishTaskDO> old=TenantUtils.executeIgnore(()->tasks.cleanupCandidates(now().minusDays(7),now()));
        for(TkTiktokShopPublishTaskDO t:old)TenantUtils.execute(t.getTenantId(),()->{if(media.deleteSnapshot(t.getSourcePath()))tasks.markCleaned(t.getId(),t.getTenantId(),now());});
    }
    @PreDestroy public void close(){pool.shutdownNow();}
    private static LocalDateTime now(){return LocalDateTime.now(ZoneOffset.UTC);}
}
