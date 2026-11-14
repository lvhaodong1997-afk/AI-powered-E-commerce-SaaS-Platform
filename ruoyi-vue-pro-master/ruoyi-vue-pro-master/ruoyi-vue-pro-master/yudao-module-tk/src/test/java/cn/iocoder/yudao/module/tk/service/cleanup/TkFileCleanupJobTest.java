package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkFileCleanupJobTest {
    @Test void blockedCleanupReleasesSchedulerAndCoalescesDailyAndHourlyTriggers() throws Exception {
        TkFileCleanupService service = mock(TkFileCleanupService.class);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        when(service.cleanupExpiredFiles()).thenAnswer(call -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new TkFileCleanupService.CleanupResult(0, 0, 0);
        });
        TkFileCleanupJob job = new TkFileCleanupJob();
        ReflectionTestUtils.setField(job, "cleanupService", service);
        ExecutorService scheduler = Executors.newSingleThreadExecutor();
        try {
            Future<?> trigger = scheduler.submit(job::cleanupExpiredFiles);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> trigger.get(500, TimeUnit.MILLISECONDS), "slow cleanup must not occupy the shared scheduler");
            for (int i = 0; i < 10; i++) { job.cleanupExpiredFiles(); job.cleanupExpiredDailyFiles(); }
            verify(service, times(1)).cleanupExpiredFiles();
            verify(service, never()).cleanupExpiredPublishMedia();
        } finally {
            release.countDown(); scheduler.shutdownNow();
            job.close();
        }
    }

    @Test void cleanupWorkerIgnoresTenantAndFailureAllowsNextRun() throws Exception {
        TkFileCleanupService service = mock(TkFileCleanupService.class);
        CountDownLatch first = new CountDownLatch(1), second = new CountDownLatch(1);
        when(service.cleanupExpiredFiles()).thenAnswer(call -> {
            assertTrue(TenantContextHolder.isIgnore());
            first.countDown(); throw new IllegalStateException("candidate query timed out");
        });
        when(service.cleanupExpiredPublishMedia()).thenAnswer(call -> {
            assertTrue(TenantContextHolder.isIgnore()); second.countDown(); return 0;
        });
        TkFileCleanupJob job = new TkFileCleanupJob(); ReflectionTestUtils.setField(job, "cleanupService", service);
        try {
            job.cleanupExpiredFiles(); assertTrue(first.await(2, TimeUnit.SECONDS));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (second.getCount() > 0 && System.nanoTime() < deadline) {
                job.cleanupExpiredDailyFiles(); second.await(20, TimeUnit.MILLISECONDS);
            }
            assertEquals(0, second.getCount(), "failed cleanup must release the sole worker");
            verify(service, timeout(1000)).cleanupExpiredTranscriptAudio();
            assertFalse(TenantContextHolder.isIgnore());
        } finally { job.close(); }
    }
}
