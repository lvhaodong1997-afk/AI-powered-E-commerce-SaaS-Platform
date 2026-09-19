package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishTerminalService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokWebhookBridgeTest {
    private final TkTiktokWebhookServiceImpl service = new TkTiktokWebhookServiceImpl();
    private final TkTiktokWebhookEventMapper events = mock(TkTiktokWebhookEventMapper.class);
    private final TkOpenTiktokPublishDetailMapper external = mock(TkOpenTiktokPublishDetailMapper.class);
    private final TkOpenTiktokConnectionMapper connections = mock(TkOpenTiktokConnectionMapper.class);
    private final TkOpenTiktokPublishTerminalService terminal = mock(TkOpenTiktokPublishTerminalService.class);
    private final TkTiktokPublishDetailMapper internal = mock(TkTiktokPublishDetailMapper.class);
    private final TkTiktokPublishPostMapper posts = mock(TkTiktokPublishPostMapper.class);
    private final TkTiktokPublishService publishing = mock(TkTiktokPublishService.class);
    private final TkApiKeyConfigService config = mock(TkApiKeyConfigService.class);
    private final TkOpenTiktokPublishDetailDO detail = TkOpenTiktokPublishDetailDO.builder()
            .id(10L).detailId("d-1").taskId("t-1").clientId("customer-1")
            .connectionId("connection-1").publishId("publish-1").status("PROCESSING").retryCount(0).build();
    private final TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
            .clientId("customer-1").connectionId("connection-1").openId("open-1").build();
    private TkTiktokWebhookEventDO event;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "eventMapper", events);
        ReflectionTestUtils.setField(service, "openDetailMapper", external);
        ReflectionTestUtils.setField(service, "connectionMapper", connections);
        ReflectionTestUtils.setField(service, "terminalService", terminal);
        ReflectionTestUtils.setField(service, "detailMapper", internal);
        ReflectionTestUtils.setField(service, "publishPostMapper", posts);
        ReflectionTestUtils.setField(service, "publishService", publishing);
        ReflectionTestUtils.setField(service, "configService", config);
        when(config.getValue(TkTiktokApiClient.PROVIDER, "client-key")).thenReturn("app-key");
        when(config.getValue(TkTiktokApiClient.PROVIDER, "client-secret")).thenReturn("secret");
        when(connections.selectByClientAndConnectionId("customer-1", "connection-1")).thenReturn(connection);
        when(events.claimForProcessing(eq(1L), any(), any())).thenReturn(1);
        when(events.finishAttempt(eq(1L), any(), anyString(), nullable(String.class), any()))
                .thenAnswer(call -> {
                    event.setStatus(call.getArgument(2));
                    event.setFailReason(call.getArgument(3));
                    event.setProcessedTime(call.getArgument(4));
                    return 1;
                });
        when(external.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(detail));
        when(terminal.confirm(any(), anyString(), nullable(String.class), nullable(String.class), eq("WEBHOOK")))
                .thenReturn(true);
    }

    @AfterEach
    void shutdown() { service.destroy(); }

    @Test
    void earlyArrivalRemainsRetryableAndResolvesAfterPublishIdPersistence() {
        event("post.publish.complete");
        when(external.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        process();
        assertEquals("RETRYING", event.getStatus());
        verifyNoInteractions(terminal);
        when(external.selectList(any(Wrapper.class))).thenReturn(Collections.singletonList(detail));
        process();
        assertEquals("PROCESSED", event.getStatus());
        verify(terminal).confirm(detail, "PUBLISH_COMPLETE", "post-1", null, "WEBHOOK");
    }

    @Test
    void duplicateReplayDoesNotCallTerminalTwice() {
        event("post.publish.complete");
        process();
        process();
        verify(terminal, times(1)).confirm(detail, "PUBLISH_COMPLETE", "post-1", null, "WEBHOOK");
    }

    @Test
    void publicAvailabilityAfterCompleteCanEnrichExistingSuccess() {
        event("post.publish.publicly_available");
        detail.setStatus("SUCCESS");
        process();
        assertEquals("PROCESSED", event.getStatus());
        verify(terminal).confirm(detail, "PUBLISH_COMPLETE", "post-1", null, "WEBHOOK");
    }

    @Test
    void failedEventUsesAuthoritativeFailedStatusAndReason() {
        event("post.publish.failed");
        event.setPayloadJson(event.getPayloadJson().replace("\"post_id\":\"post-1\"",
                "\"post_id\":\"post-1\",\"reason\":\"policy rejected\""));
        process();
        verify(terminal).confirm(detail, "FAILED", "post-1", "policy rejected", "WEBHOOK");
        assertEquals("PROCESSED", event.getStatus());
    }

    @Test
    void conflictingOutOfOrderTerminalIsRetainedForInvestigation() {
        event("post.publish.failed");
        detail.setStatus("SUCCESS");
        when(terminal.confirm(any(), eq("FAILED"), any(), any(), eq("WEBHOOK"))).thenReturn(false);
        process();
        assertEquals("CONFLICT", event.getStatus());
        assertNotNull(event.getFailReason());
        assertEquals("SUCCESS", detail.getStatus());
        verifyNoInteractions(internal, publishing);
    }

    @Test
    void wrongAppNeverFallsBackToInternalHandler() {
        event("post.publish.complete");
        when(config.getValue(TkTiktokApiClient.PROVIDER, "client-key")).thenReturn("other-app");
        process();
        assertEquals("REJECTED", event.getStatus());
        verifyNoInteractions(terminal, internal, publishing, posts);
    }

    @Test
    void missingConfiguredAppKeyCannotAuthenticateExternalEvent() {
        event("post.publish.complete");
        when(config.getValue(TkTiktokApiClient.PROVIDER, "client-key")).thenReturn(null);
        process();
        assertEquals("REJECTED", event.getStatus());
        verifyNoInteractions(terminal, internal);
    }

    @Test
    void wrongOpenIdNeverFallsBackToInternalHandler() {
        event("post.publish.complete");
        connection.setOpenId("other-account");
        process();
        assertEquals("REJECTED", event.getStatus());
        verify(connections).selectByClientAndConnectionId("customer-1", "connection-1");
        verifyNoInteractions(terminal, internal, publishing, posts);
    }

    @Test
    void missingConnectionIsRetainedAndCannotUseInternalRecord() {
        event("post.publish.complete");
        when(connections.selectByClientAndConnectionId("customer-1", "connection-1")).thenReturn(null);
        process();
        assertEquals("REJECTED", event.getStatus());
        verifyNoInteractions(terminal, internal);
    }

    @Test
    void ambiguousExternalPublishIdNeverPicksFirstMatch() {
        event("post.publish.complete");
        when(external.selectList(any(Wrapper.class))).thenReturn(Arrays.asList(detail, detail));
        process();
        assertEquals("CONFLICT", event.getStatus());
        verifyNoInteractions(terminal, internal);
    }

    @Test
    void noLongerPublicDoesNotRevertExternalSuccess() {
        event("post.publish.no_longer_publicaly_available");
        detail.setStatus("SUCCESS");
        process();
        assertEquals("IGNORED", event.getStatus());
        assertEquals("SUCCESS", detail.getStatus());
        verifyNoInteractions(terminal, internal);
    }

    @Test
    void unknownEventIsIgnoredWithoutPublishLookup() {
        event("user.authorization.removed");
        process();
        assertEquals("IGNORED", event.getStatus());
        verifyNoInteractions(terminal, external, internal);
    }

    @Test
    void missingPublishIdNeverGuessesUsingPostId() {
        event("post.publish.complete");
        event.setPayloadJson(event.getPayloadJson().replace("\"publish_id\":\"publish-1\",", ""));
        process();
        assertEquals("RETRYING", event.getStatus());
        verifyNoInteractions(terminal, external, internal, posts);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void externalLookupUsesOnlyExactPublishIdAndDetectsAmbiguity() {
        event("post.publish.complete");
        process();
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(external).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("publish_id ="));
        assertTrue(sql.contains("BINARY publish_id"), "MySQL default collation is case insensitive");
        assertTrue(sql.endsWith("LIMIT 2"));
        assertFalse(sql.contains("post_id"));
        assertTrue(((QueryWrapper<?>) query.getValue()).getParamNameValuePairs().values().stream()
                .allMatch("publish-1"::equals));
    }

    @Test
    void internalLookupWorksWithoutRequestTenantAndMutationRestoresOwnerTenant() {
        event("post.publish.complete");
        TkTiktokPublishDetailDO target = internalDetail("PROCESSING");
        when(internal.selectByPublishId("publish-1")).thenAnswer(call -> {
            assertTrue(TenantContextHolder.isIgnore());
            return target;
        });
        when(internal.updateById(target)).thenAnswer(call -> {
            assertFalse(TenantContextHolder.isIgnore());
            assertEquals(1L, TenantContextHolder.getTenantId());
            return 1;
        });
        process();
        assertEquals("PROCESSED", event.getStatus());
    }

    @Test
    void terminalExceptionIsDurableFailedAndReplayable() {
        event("post.publish.complete");
        when(terminal.confirm(any(), any(), any(), any(), eq("WEBHOOK")))
                .thenThrow(new IllegalStateException("transaction unavailable")).thenReturn(true);
        process();
        assertEquals("FAILED", event.getStatus());
        assertTrue(event.getFailReason().contains("transaction unavailable"));
        process();
        assertEquals("PROCESSED", event.getStatus());
        assertNull(event.getFailReason());
    }

    @Test
    void lostClaimDoesNotApplyEvent() {
        event("post.publish.complete");
        when(events.claimForProcessing(eq(1L), any(), any())).thenReturn(0);
        process();
        verifyNoInteractions(terminal, external, internal);
    }

    @Test
    void internalCompleteStillSynchronizesLinks() {
        event("post.publish.complete");
        TkTiktokPublishDetailDO target = internalDetail("PROCESSING");
        process();
        assertEquals("PUBLISH_COMPLETE", target.getTiktokStatus());
        verify(publishing).syncPublishLinks(20L);
        assertEquals("PROCESSED", event.getStatus());
        verifyNoInteractions(terminal);
    }

    @Test
    void internalFailureStillUpdatesDetail() {
        event("post.publish.failed");
        TkTiktokPublishDetailDO target = internalDetail("PROCESSING");
        process();
        assertEquals("FAILED", target.getStatus());
        verify(internal).updateById(target);
    }

    @Test
    void internalSuccessIsNotRevertedByLateFailure() {
        event("post.publish.failed");
        TkTiktokPublishDetailDO target = internalDetail("SUCCESS");
        process();
        assertEquals("SUCCESS", target.getStatus());
        assertEquals("CONFLICT", event.getStatus());
        verify(internal, never()).updateById(any(TkTiktokPublishDetailDO.class));
    }

    @Test
    void internalNoLongerPublicOnlyChangesVisibility() {
        event("post.publish.no_longer_publicaly_available");
        TkTiktokPublishDetailDO target = internalDetail("SUCCESS");
        process();
        assertEquals("SUCCESS", target.getStatus());
        assertEquals("NO_LONGER_PUBLIC", target.getLinkCaptureStatus());
    }

    @Test
    void rejectedSchedulingKeepsStoredEventReplayable() {
        event("post.publish.complete");
        when(events.selectByEventId(anyString())).thenReturn(null);
        doAnswer(call -> { ((TkTiktokWebhookEventDO) call.getArgument(0)).setId(1L); return 1; })
                .when(events).insert(any(TkTiktokWebhookEventDO.class));
        service.destroy();
        String body = event.getPayloadJson();
        long now = Instant.now().getEpochSecond();
        assertDoesNotThrow(() -> service.receive(body,
                "t=" + now + ",s=" + TkTiktokWebhookServiceImpl.sign("secret", now, body)));
        assertEquals("FAILED", event.getStatus());
        assertTrue(event.getFailReason().contains("scheduling"));
    }

    @Test
    void restartScannerReplaysStoredReceivedFailedAndRetryingEvents() {
        ExecutorService executor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(service, "executor", executor);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(executor).execute(any());
        for (String state : Arrays.asList("RECEIVED", "FAILED", "RETRYING")) {
            event("post.publish.complete");
            event.setStatus(state);
            when(events.selectRetryBatch(any(), eq(100))).thenReturn(Collections.singletonList(event));
            service.retryPending(1000);
            assertEquals("PROCESSED", event.getStatus(), state);
        }
        verify(events, times(3)).selectRetryBatch(any(), eq(100));
    }

    private TkTiktokPublishDetailDO internalDetail(String status) {
        when(external.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        TkTiktokPublishDetailDO target = new TkTiktokPublishDetailDO();
        target.setId(20L);
        target.setTenantId(1L);
        target.setPublishId("publish-1");
        target.setStatus(status);
        when(internal.selectByPublishId("publish-1")).thenReturn(target);
        return target;
    }

    private void event(String type) {
        String body = "{\"event_id\":\"event-1\",\"client_key\":\"app-key\",\"user_openid\":\"open-1\","
                + "\"event\":\"" + type + "\",\"content\":{\"publish_id\":\"publish-1\",\"post_id\":\"post-1\"}}";
        event = TkTiktokWebhookEventDO.builder().id(1L).eventId("event-1")
                .payloadJson(body).status("RECEIVED").receivedTime(LocalDateTime.now()).build();
        when(events.selectById(1L)).thenReturn(event);
    }

    private void process() { ReflectionTestUtils.invokeMethod(service, "process", 1L); }
}
