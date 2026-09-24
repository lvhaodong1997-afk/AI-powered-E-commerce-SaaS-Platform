package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishAttemptMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenTiktokPublishTerminalServiceTest {
    private TkOpenTiktokPublishDetailMapper details;
    private TkOpenTiktokPublishTaskMapper tasks;
    private TkOpenTiktokPublishAttemptMapper attempts;
    private TkOpenApiCallbackService callbacks;
    private TkOpenTiktokPublishTerminalService service;
    private TkOpenTiktokPublishDetailDO current;
    private TkOpenTiktokPublishDetailDO expected;

    @BeforeEach
    void setup() {
        for (Class<?> type : Arrays.asList(TkOpenTiktokPublishDetailDO.class,
                TkOpenTiktokPublishTaskDO.class, TkOpenTiktokPublishAttemptDO.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
        details = mock(TkOpenTiktokPublishDetailMapper.class);
        tasks = mock(TkOpenTiktokPublishTaskMapper.class);
        attempts = mock(TkOpenTiktokPublishAttemptMapper.class);
        callbacks = mock(TkOpenApiCallbackService.class);
        service = new TkOpenTiktokPublishTerminalService(details, tasks, callbacks, attempts);
        current = snapshot();
        expected = snapshot();
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(
                TkOpenTiktokPublishTaskDO.builder().id(2L).taskId("t").clientId("c")
                        .externalRequestId("external").build());
        when(details.selectOne(any(Wrapper.class))).thenReturn(current);
        when(details.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(current));
        when(details.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(tasks.update(isNull(), any(Wrapper.class))).thenReturn(1);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void locksTaskBeforeScopedDetailAndKeepsExactCallbackContract() {
        assertTrue(service.confirm(expected, "PUBLISH_COMPLETE", "post", null, "WEBHOOK"));
        InOrder locks = inOrder(tasks, details);
        locks.verify(tasks).selectByClientAndTaskIdForUpdate("c", "t");
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        locks.verify(details).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("client_id"));
        assertTrue(sql.contains("task_id"));
        assertTrue(sql.contains("detail_id"));
        assertTrue(sql.endsWith("FOR UPDATE"));
        ArgumentCaptor<Map> payload = ArgumentCaptor.forClass(Map.class);
        verify(callbacks).enqueueOnce(eq("c"), eq("publish.success"), eq("PUBLISH_DETAIL"),
                eq("d"), payload.capture(), eq(0));
        Map<String, Object> body = payload.getValue();
        assertEquals(new java.util.LinkedHashSet<>(Arrays.asList("taskId", "detailId", "connectionId",
                "externalRequestId", "status", "publishId", "publicPostId", "publishUrl", "failReason")),
                body.keySet());
        assertEquals("t", body.get("taskId"));
        assertEquals("d", body.get("detailId"));
        assertEquals("connection", body.get("connectionId"));
        assertEquals("external", body.get("externalRequestId"));
        assertEquals("SUCCESS", body.get("status"));
        assertEquals("publish", body.get("publishId"));
        assertEquals("post", body.get("publicPostId"));
        assertNull(body.get("publishUrl"));
        assertNull(body.get("failReason"));
    }

    @Test
    void rejectsSnapshotFromPreviousRetryEvenIfPublishIdWasReused() {
        current.setRetryCount(1);
        assertFalse(service.confirm(expected, "FAILED", null, "rejected", "STATUS_API"));
        verify(details, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(attempts, callbacks);
    }

    @Test
    void rejectsSnapshotWithPreviousPublishId() {
        current.setPublishId("new-publish");
        assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        verifyNoInteractions(attempts, callbacks);
    }

    @Test
    void rejectsUnsupportedEvidenceAndNonExactTerminalStatuses() {
        for (String source : Arrays.asList("VIDEO_LIST", "TIMEOUT", "DEADLINE", "", null)) {
            assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", null, null, source));
        }
        for (String status : Arrays.asList("SUCCESS", "SEND_TO_USER_INBOX", "publish_complete",
                "FAILED_TIMEOUT", "REJECTED", "PROCESSING_UPLOAD", null)) {
            assertFalse(service.confirm(expected, status, null, "reason", "STATUS_API"));
        }
        verifyNoInteractions(attempts, callbacks);
        verify(details, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void localEvidenceCannotReportSuccessOrFailAnInitializedPublish() {
        for (String source : Arrays.asList("PRE_INIT_FAILURE", "INIT_REJECTED")) {
            assertFalse(service.confirm(expected, "FAILED", null, "reason", source));
            assertFalse(service.confirmLocal(expected, "owner", "reason", source));
            current.setPublishId(null);
            expected.setPublishId(null);
            assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", null, null, source));
            current.setPublishId("publish");
            expected.setPublishId("publish");
        }
        verifyNoInteractions(attempts, callbacks);
    }

    @Test
    void ordinaryConfirmRejectsLocalEvidenceEvenWithoutPublishId() {
        current.setPublishId(null);
        expected.setPublishId(null);
        for (String source : Arrays.asList("PRE_INIT_FAILURE", "INIT_REJECTED")) {
            assertFalse(service.confirm(expected, "FAILED", null, "reason", source));
        }
        verifyNoInteractions(tasks, details, attempts, callbacks);
    }

    @Test
    void localConfirmationRequiresOwnerAndLocalSource() {
        current.setPublishId(null);
        expected.setPublishId(null);
        for (String owner : Arrays.asList(null, "", " ")) {
            assertFalse(service.confirmLocal(expected, owner, "reason", "PRE_INIT_FAILURE"));
        }
        for (String source : Arrays.asList("STATUS_API", "WEBHOOK", "TIMEOUT", null)) {
            assertFalse(service.confirmLocal(expected, "owner", "reason", source));
        }
        verifyNoInteractions(tasks, details, attempts, callbacks);
    }

    @Test
    void staleOwnerCannotFailNewWorkersSameAttempt() {
        localAttempt("MATERIAL_PREPARATION").setOwnerToken("new-owner");
        assertFalse(service.confirmLocal(expected, "old-owner", "stale failure", "PRE_INIT_FAILURE"));
        assertEquals("PROCESSING", current.getStatus());
        verify(details, never()).update(isNull(), any(Wrapper.class));
        verify(attempts, never()).update(isNull(), any(Wrapper.class));
        verify(tasks, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(callbacks);
    }

    @Test
    void localConfirmationRejectsMissingAttemptAndMismatchedAttemptIdentity() {
        current.setPublishId(null);
        expected.setPublishId(null);
        for (String source : Arrays.asList("PRE_INIT_FAILURE", "INIT_REJECTED")) {
            assertFalse(service.confirmLocal(expected, "owner", "reason", source));
        }
        TkOpenTiktokPublishAttemptDO attempt = localAttempt("READY_TO_INIT");
        attempt.setAttemptNo(1);
        assertFalse(service.confirmLocal(expected, "owner", "reason", "PRE_INIT_FAILURE"));
        attempt.setAttemptNo(0).setPublishId("accepted-publish");
        assertFalse(service.confirmLocal(expected, "owner", "reason", "PRE_INIT_FAILURE"));
        verify(details, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(callbacks);
    }

    @Test
    void localFailureRequiresExactPersistedPhaseForItsEvidence() {
        TkOpenTiktokPublishAttemptDO attempt = localAttempt("MATERIAL_PREPARATION");
        for (String phase : Arrays.asList("INIT_SENT", "INIT_OUTCOME_UNKNOWN", "PUBLISH_ID_SAVED",
                "UPLOADING", "POLLING", "RECOVERY_REQUIRED", "CONFIRMED_TERMINAL", "", null)) {
            attempt.setPhase(phase);
            assertFalse(service.confirmLocal(expected, "owner", "reason", "PRE_INIT_FAILURE"), phase);
        }
        for (String phase : Arrays.asList("MATERIAL_PREPARATION", "READY_TO_INIT", "INIT_OUTCOME_UNKNOWN",
                "PUBLISH_ID_SAVED", "UPLOADING", "POLLING", "RECOVERY_REQUIRED", "CONFIRMED_TERMINAL", "", null)) {
            attempt.setPhase(phase);
            assertFalse(service.confirmLocal(expected, "owner", "reason", "INIT_REJECTED"), phase);
        }
        verify(details, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(callbacks);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void localFailureLocksScopedAttemptAfterDetailBeforeWriting() {
        localAttempt("READY_TO_INIT");
        when(attempts.update(isNull(), any(Wrapper.class))).thenReturn(1);
        assertTrue(service.confirmLocal(expected, "owner", "invalid media", "PRE_INIT_FAILURE"));
        InOrder locks = inOrder(tasks, details, attempts);
        locks.verify(tasks).selectByClientAndTaskIdForUpdate("c", "t");
        locks.verify(details).selectOne(any(Wrapper.class));
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        locks.verify(attempts).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        for (String column : Arrays.asList("client_id", "task_id", "detail_id", "attempt_no")) {
            assertTrue(sql.contains(column), sql);
        }
        assertTrue(sql.endsWith("FOR UPDATE"));
        locks.verify(details).update(isNull(), any(Wrapper.class));
        assertEquals("FAILED", current.getStatus());
    }

    @Test
    void remoteEvidenceNeedsAPublishId() {
        expected.setPublishId(null);
        current.setPublishId(null);
        assertFalse(service.confirm(expected, "FAILED", null, "reason", "STATUS_API"));
        assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", null, null, "WEBHOOK"));
        verifyNoInteractions(attempts, callbacks);
    }

    @Test
    void confirmsExpiredUploadOnlyWithOwnedIncompleteUploadEvidence() {
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(3L).clientId("c").taskId("t").detailId("d").attemptNo(0)
                .ownerToken("owner").phase("UPLOADING").publishId("publish")
                .uploadSource("FILE_UPLOAD")
                .fileSize(100L).lastError("TikTok 分片上传失败，HTTP 403").build();
        when(attempts.selectOne(any(Wrapper.class))).thenReturn(attempt);
        when(attempts.update(isNull(), any(Wrapper.class))).thenReturn(1);

        assertTrue(service.confirmUploadExpired(expected, "owner", 40L,
                "TikTok publish failed: upload URL expired before all bytes were uploaded"));

        assertEquals("FAILED", current.getStatus());
        assertEquals("PROCESSING_UPLOAD", current.getTiktokStatus());
        assertEquals("UPLOAD_EXPIRED_CONFIRMED", attempt.getTerminalSource());
        verify(callbacks).enqueueOnce(eq("c"), eq("publish.failed"), eq("PUBLISH_DETAIL"),
                eq("d"), anyMap(), eq(0));
    }

    @Test
    void rejectsExpiredUploadWithoutExactOwnedIncompleteEvidence() {
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(3L).clientId("c").taskId("t").detailId("d").attemptNo(0)
                .ownerToken("owner").phase("UPLOADING").publishId("publish")
                .uploadSource("FILE_UPLOAD")
                .fileSize(100L).lastError("TikTok 分片上传失败，HTTP 500").build();
        when(attempts.selectOne(any(Wrapper.class))).thenReturn(attempt);

        assertFalse(service.confirmUploadExpired(expected, "owner", 40L, "expired"));
        attempt.setLastError("TikTok 分片上传失败，HTTP 403");
        assertFalse(service.confirmUploadExpired(expected, "stale-owner", 40L, "expired"));
        assertFalse(service.confirmUploadExpired(expected, "owner", 100L, "expired"));

        assertEquals("PROCESSING", current.getStatus());
        verify(details, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(callbacks);
    }

    @Test
    void missingTaskOrDetailDoesNotWriteAnything() {
        when(tasks.selectByClientAndTaskIdForUpdate("c", "t")).thenReturn(null);
        assertFalse(service.confirm(expected, "FAILED", null, "reason", "STATUS_API"));
        assertFalse(service.repairCallback(expected));
        verifyNoInteractions(details, attempts, callbacks);
    }

    @Test
    void lostDetailWriteAbortsBeforeAggregateOrCallback() {
        when(details.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertFalse(service.confirm(expected, "PUBLISH_COMPLETE", null, null, "STATUS_API"));
        verify(tasks, never()).update(isNull(), any(Wrapper.class));
        verifyNoInteractions(callbacks);
    }

    private TkOpenTiktokPublishDetailDO snapshot() {
        return TkOpenTiktokPublishDetailDO.builder().id(1L).clientId("c").taskId("t").detailId("d")
                .connectionId("connection").status("PROCESSING").publishId("publish").retryCount(0).build();
    }

    private TkOpenTiktokPublishAttemptDO localAttempt(String phase) {
        current.setPublishId(null);
        expected.setPublishId(null);
        TkOpenTiktokPublishAttemptDO attempt = TkOpenTiktokPublishAttemptDO.builder()
                .id(3L).clientId("c").taskId("t").detailId("d").attemptNo(0)
                .ownerToken("owner").phase(phase).build();
        when(attempts.selectOne(any(Wrapper.class))).thenReturn(attempt);
        return attempt;
    }
}
