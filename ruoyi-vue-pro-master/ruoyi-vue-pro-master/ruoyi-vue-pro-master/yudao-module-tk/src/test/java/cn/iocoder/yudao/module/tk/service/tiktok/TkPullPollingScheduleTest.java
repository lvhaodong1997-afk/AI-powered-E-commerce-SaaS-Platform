package cn.iocoder.yudao.module.tk.service.tiktok;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishStatusJob;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;
class TkPullPollingScheduleTest {
 @Test void statusLanesScanEveryTenSeconds() throws Exception {
  assertTrue(TkOpenTiktokPublishStatusJob.class.getMethod("sync").getAnnotation(Scheduled.class).fixedDelayString().endsWith(":10000}"));
  assertTrue(TkTiktokPublishStatusSyncJob.class.getMethod("syncProcessingStatus").getAnnotation(Scheduled.class).fixedDelayString().endsWith(":10000}"));
 }
 @Test void legacyDispatchHasAnIndependentTimer() throws Exception {
  assertNotNull(TkTiktokPublishStatusSyncJob.class.getMethod("dispatchScheduled").getAnnotation(Scheduled.class));
 }
 @Test void fasterStatusTimerDoesNotDispatchPendingAndDispatchFailureDoesNotStopPending() throws Exception {
  TkTiktokPublishService service=org.mockito.Mockito.mock(TkTiktokPublishService.class);
  TkTiktokPublishStatusSyncJob job=new TkTiktokPublishStatusSyncJob();
  java.lang.reflect.Field field=TkTiktokPublishStatusSyncJob.class.getDeclaredField("publishService");field.setAccessible(true);field.set(job,service);
  job.syncProcessingStatus();
  org.mockito.Mockito.verify(service).syncStaleProcessingStatus(50);
  org.mockito.Mockito.verify(service,org.mockito.Mockito.never()).dispatchDueScheduled(50);
  org.mockito.Mockito.verify(service,org.mockito.Mockito.never()).resumePendingPublishTasks(50);
  org.mockito.Mockito.when(service.dispatchDueScheduled(50)).thenThrow(new IllegalStateException("deferred"));
  job.dispatchScheduled();org.mockito.Mockito.verify(service).resumePendingPublishTasks(50);
 }
 @Test void overlappingStatusScansDoNotQueue() throws Exception {
  TkTiktokPublishService service=org.mockito.Mockito.mock(TkTiktokPublishService.class);
  TkTiktokPublishStatusSyncJob job=new TkTiktokPublishStatusSyncJob();
  java.lang.reflect.Field field=TkTiktokPublishStatusSyncJob.class.getDeclaredField("publishService");field.setAccessible(true);field.set(job,service);
  java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  org.mockito.Mockito.when(service.syncStaleProcessingStatus(50)).thenAnswer(call->{started.countDown();release.await(3,java.util.concurrent.TimeUnit.SECONDS);return 0;});
  Thread thread=new Thread(job::syncProcessingStatus);thread.start();
  try {assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));job.syncProcessingStatus();job.dispatchScheduled();org.mockito.Mockito.verify(service).dispatchDueScheduled(50);org.mockito.Mockito.verify(service,org.mockito.Mockito.times(1)).syncStaleProcessingStatus(50);}
  finally {release.countDown();thread.join(2000);}
 }
}
