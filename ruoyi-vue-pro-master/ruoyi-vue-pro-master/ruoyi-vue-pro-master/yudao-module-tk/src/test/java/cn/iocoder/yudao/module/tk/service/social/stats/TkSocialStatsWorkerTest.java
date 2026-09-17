package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialStatsDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialStatsRepository;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkSocialStatsWorkerTest {
    @Test void executorIsIndependentBoundedToTwoAndInstallsTenantContext() throws Exception {
        Class<?> type=assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsWorker"));
        TkSocialStatsRepository repository=mock(TkSocialStatsRepository.class);
        TkSocialStatsService service=mock(TkSocialStatsService.class);
        TkSocialStatsProperties config=new TkSocialStatsProperties(); config.setEnabled(true);
        TkSocialProperties social=new TkSocialProperties(); social.setEnabled(true);
        Object worker=type.getConstructor(TkSocialStatsRepository.class,TkSocialStatsService.class,TkSocialStatsProperties.class,TkSocialProperties.class)
                .newInstance(repository,service,config,social);
        TkSocialStatsDO row=new TkSocialStatsDO(); row.setTenantId(2L); row.setObjectId(4L);
        when(repository.due(anyBoolean(),anyInt(),any(LocalDateTime.class))).thenReturn(Arrays.asList(row,row));
        when(repository.claim(anyBoolean(),eq(2L),eq(4L),anyString(),any(LocalDateTime.class),any(LocalDateTime.class))).thenReturn(true);
        CountDownLatch started=new CountDownLatch(2), release=new CountDownLatch(1), finished=new CountDownLatch(2);
        AtomicInteger calls=new AtomicInteger(),correctContext=new AtomicInteger();
        doAnswer(call->{ calls.incrementAndGet(); if(Long.valueOf(2L).equals(TenantContextHolder.getTenantId()) && !TenantContextHolder.isIgnore()) correctContext.incrementAndGet();
            started.countDown(); try { release.await(5,TimeUnit.SECONDS); } finally { finished.countDown(); }
            return null; }).when(service).refresh(anyBoolean(),any(TkSocialStatsDO.class),anyString());
        try {
            type.getMethod("poll").invoke(worker);
            assertTrue(started.await(3,TimeUnit.SECONDS));
            type.getMethod("poll").invoke(worker); type.getMethod("poll").invoke(worker);
            assertEquals(2,calls.get()); assertEquals(2,correctContext.get());
        } finally {
            release.countDown();
            try { assertTrue(finished.await(3,TimeUnit.SECONDS)); }
            finally { type.getMethod("close").invoke(worker); }
        }
    }
}
