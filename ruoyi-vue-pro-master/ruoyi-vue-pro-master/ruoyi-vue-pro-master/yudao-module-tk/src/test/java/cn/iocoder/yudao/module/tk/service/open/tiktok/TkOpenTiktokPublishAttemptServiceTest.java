package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenTiktokPublishAttemptServiceTest {
    @Test void fullUploadLaneCanReturnOnlyOwnedUnsentWork() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PROCESSING", null);
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(attempts.update(any(), any())).thenReturn(1);
        when(details.update(any(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);

        service.deferUnsent(detail, attempt("READY_TO_INIT", "owner"));

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper> update =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(attempts).update(isNull(), update.capture());
        String sql = update.getValue().getSqlSegment();
        assertTrue(sql.contains("owner_token"));
        assertTrue(sql.contains("phase IN"));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("READY_TO_INIT"));
        assertFalse(update.getValue().getParamNameValuePairs().containsValue("INIT_SENT"));
        verify(details).update(isNull(), any());
        verify(tasks).update(isNull(), any());
    }

    @Test void publishedPendingDetailIsMovedToRecoveryWithoutASecondClaim() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PENDING", "official-id");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(details.update(isNull(), any())).thenReturn(1);
        when(tasks.update(isNull(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);

        assertNull(service.claim(detail));
        verify(attempts, never()).insert(any(TkOpenTiktokPublishAttemptDO.class));
        verify(attempts, never()).update(any(), any());
        verify(details).update(isNull(), any());
        verify(tasks).update(isNull(), any());
    }

    @Test void expiredPublishedWorkerIsFencedAndMarkedForStatusRecovery() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PROCESSING", "official-id");
        TkOpenTiktokPublishAttemptDO current = attempt("UPLOADING", "old-owner")
                .setPublishId("official-id").setHeartbeatTime(LocalDateTime.now().minusMinutes(10));
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectByClientAndDetailIdForUpdate("c", "t", "d")).thenReturn(detail);
        when(attempts.selectCurrent("d", 0)).thenReturn(current);
        when(attempts.update(isNull(), any())).thenReturn(1);
        when(details.update(isNull(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);

        assertTrue(service.recoverExpired(detail, LocalDateTime.now().minusMinutes(3), "lease expired"));
        verify(attempts).update(isNull(), any());
        verify(details).update(isNull(), any());
        assertEquals("RECOVERY_REQUIRED", current.getPhase());
    }

    @Test void fencedWorkerCannotReturnDetailToPending() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PROCESSING", null);
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);

        assertThrows(IllegalStateException.class, () -> service.deferUnsent(detail, attempt("READY_TO_INIT", "old")));
        verify(details, never()).update(any(), any());
    }

    @org.junit.jupiter.api.BeforeEach
    void initMybatisLambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishAttemptDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishDetailDO.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishTaskDO.class);
    }
    @Test void onlyDefinitelyUnsentPhasesCanRestart() {
        assertTrue(TkOpenTiktokPublishAttemptService.canRestart("MATERIAL_PREPARATION"));
        assertTrue(TkOpenTiktokPublishAttemptService.canRestart("READY_TO_INIT"));
        for (String phase : new String[]{"INIT_SENT", "PUBLISH_ID_SAVED", "UPLOADING", "PLATFORM_PROCESSING",
                "CONFIRMED_TERMINAL", "RECOVERY_REQUIRED", null}) {
            assertFalse(TkOpenTiktokPublishAttemptService.canRestart(phase), String.valueOf(phase));
        }
    }

    @Test void activeHeartbeatPreventsRecovery() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PENDING", null);
        TkOpenTiktokPublishAttemptDO current = attempt("MATERIAL_PREPARATION", "owner");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(attempts.selectCurrent("d", 0)).thenReturn(current);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        assertFalse(service.recoverUnsent(detail, LocalDateTime.now().minusMinutes(5)));
        verify(attempts, never()).update(any(), any());
    }

    @Test void fencedWorkerCannotStageOrInit() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        TkOpenTiktokPublishAttemptDO worker = attempt("READY_TO_INIT", "old-owner");
        when(attempts.update(any(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.stage(worker, "INIT_SENT"));
        assertThrows(IllegalStateException.class, () -> service.ready(worker, "PULL_FROM_URL", 0L, null));
        verify(attempts, times(2)).update(any(), any());
    }

    @Test void staleLeaseCannotAdvanceToRemoteInit() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        TkOpenTiktokPublishAttemptDO worker = attempt("READY_TO_INIT", "owner")
                .setHeartbeatTime(LocalDateTime.now().minusMinutes(5));
        when(attempts.update(any(), any())).thenReturn(0);

        assertThrows(IllegalStateException.class,
                () -> service.beginRemoteInit(worker, LocalDateTime.now().minusMinutes(3)));
        verify(attempts).update(any(), any());
    }

    @Test void uploadRecoveryRequiresCurrentProcessingDetail() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        TkOpenTiktokPublishDetailDO expected = detail("PROCESSING", "publish");
        TkOpenTiktokPublishDetailDO terminal = detail("SUCCESS", "publish");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(terminal);

        assertNull(service.claimUpload(expected, LocalDateTime.now().minusMinutes(3)));
        verify(attempts, never()).selectCurrent(anyString(), anyInt());
        verify(attempts, never()).update(any(), any());
    }

    @Test void firstResponsePersistenceFailureCanRetrySameOfficialId() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PROCESSING", null);
        TkOpenTiktokPublishAttemptDO worker = attempt("INIT_SENT", "owner");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(attempts.selectCurrent("d", 0)).thenReturn(worker);
        when(attempts.update(any(), any())).thenReturn(1);
        when(details.update(isNull(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        service.saveResponse(detail, worker, "official-id", "encrypted-session");
        service.saveResponse(detail, worker, "official-id", "encrypted-session");
        verify(attempts, times(2)).update(any(), any());
        verify(details, times(2)).update(isNull(), any());
        assertEquals("official-id", worker.getPublishId());
        assertEquals("PUBLISH_ID_SAVED", worker.getPhase());
    }

    @Test void uncertainInitCannotBeReclaimed() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("PROCESSING", null);
        TkOpenTiktokPublishAttemptDO current = attempt("INIT_SENT", "owner");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(attempts.selectCurrent("d", 0)).thenReturn(current);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        assertFalse(service.recoverUnsent(detail, LocalDateTime.now().minusMinutes(5)));
        verify(attempts, never()).update(any(), any());
    }

    @Test void responseAfterTerminalWebhookDoesNotRegressPhase() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        TkOpenTiktokPublishDetailMapper details = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskMapper tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailDO detail = detail("SUCCESS", "official-id").setTiktokStatus("PUBLISH_COMPLETE");
        TkOpenTiktokPublishAttemptDO worker = attempt("CONFIRMED_TERMINAL", "owner").setPublishId("official-id");
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(task());
        when(details.selectOne(any())).thenReturn(detail);
        when(attempts.selectCurrent("d", 0)).thenReturn(worker);
        when(attempts.update(any(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts, details, tasks);
        service.saveResponse(detail, worker, "official-id", "session");
        assertEquals("CONFIRMED_TERMINAL", worker.getPhase());
        verify(details, never()).update(isNull(), any());
    }

    @Test void repeatedErrorsBackOffAndPersistReconcileCount() {
        TkOpenTiktokPublishAttemptMapper attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        when(attempts.update(any(), any())).thenReturn(1);
        TkOpenTiktokPublishAttemptService service = new TkOpenTiktokPublishAttemptService(attempts,
                mock(TkOpenTiktokPublishDetailMapper.class), mock(TkOpenTiktokPublishTaskMapper.class));
        TkOpenTiktokPublishAttemptDO worker = attempt("UPLOADING", "owner").setReconcileCount(0);
        int[] delays = {30, 60, 120, 300, 600, 600};
        for (int index = 0; index < delays.length; index++) {
            LocalDateTime before = LocalDateTime.now();
            service.error(worker, "network timed out");
            assertEquals(index + 1, worker.getReconcileCount());
            assertFalse(worker.getNextReconcileTime().isBefore(before.plusSeconds(delays[index])));
            assertFalse(worker.getNextReconcileTime().isAfter(LocalDateTime.now().plusSeconds(delays[index])));
        }
    }

    private TkOpenTiktokPublishTaskDO task() { return TkOpenTiktokPublishTaskDO.builder().id(1L).taskId("t").clientId("c").build(); }
    private TkOpenTiktokPublishDetailDO detail(String status, String publishId) { return TkOpenTiktokPublishDetailDO.builder().id(2L).detailId("d").taskId("t").clientId("c").retryCount(0).status(status).publishId(publishId).build(); }
    private TkOpenTiktokPublishAttemptDO attempt(String phase, String owner) { return TkOpenTiktokPublishAttemptDO.builder().id(3L).detailId("d").taskId("t").clientId("c").attemptNo(0).phase(phase).ownerToken(owner).heartbeatTime(LocalDateTime.now()).build(); }
}
