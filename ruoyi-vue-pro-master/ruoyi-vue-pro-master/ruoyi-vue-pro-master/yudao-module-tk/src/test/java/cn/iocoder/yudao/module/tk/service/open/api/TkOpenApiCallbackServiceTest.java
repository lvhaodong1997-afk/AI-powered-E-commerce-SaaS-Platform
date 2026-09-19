package cn.iocoder.yudao.module.tk.service.open.api;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSigner;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenApiCallbackServiceTest {

    @org.junit.jupiter.api.BeforeEach
    void initMybatisLambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenApiEventDO.class);
    }

    @Test
    void shouldDeduplicateAcrossRestartsButAllowANewPublishAttempt() {
        TkOpenApiEventMapper mapper = mock(TkOpenApiEventMapper.class);
        Map<String, TkOpenApiEventDO> stored = new java.util.HashMap<>();
        when(mapper.selectByDedupeKey(anyString(), anyString())).thenAnswer(
                call -> stored.get(call.getArgument(1)));
        when(mapper.insert(any(TkOpenApiEventDO.class))).thenAnswer(call -> {
            TkOpenApiEventDO event = call.getArgument(0);
            stored.put(event.getDedupeKey(), event);
            return 1;
        });
        TkOpenApiCallbackService first = new TkOpenApiCallbackService(mapper,
                mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class), null);
        TkOpenApiCallbackService restarted = new TkOpenApiCallbackService(mapper,
                mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class), null);
        try {
            String eventId = first.enqueueOnce("c", "publish.failed", "PUBLISH_DETAIL", "d", null, 0);
            assertEquals(eventId, restarted.enqueueOnce("c", "publish.failed", "PUBLISH_DETAIL", "d", null, 0));
            org.junit.jupiter.api.Assertions.assertNotEquals(eventId,
                    restarted.enqueueOnce("c", "publish.failed", "PUBLISH_DETAIL", "d", null, 1));
            verify(mapper, times(2)).insert(any(TkOpenApiEventDO.class));
        } finally {
            first.destroy();
            restarted.destroy();
        }
    }

    @Test
    void shouldReturnConcurrentWinnerInsteadOfCreatingDuplicateCallback() {
        TkOpenApiEventMapper mapper = mock(TkOpenApiEventMapper.class);
        when(mapper.selectByDedupeKey(anyString(), anyString())).thenReturn(null,
                TkOpenApiEventDO.builder().eventId("winner").build());
        when(mapper.insert(any(TkOpenApiEventDO.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(mapper,
                mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class), null);
        try {
            assertEquals("winner", service.enqueueOnce("c", "publish.success", "PUBLISH_DETAIL", "d", null, 0));
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldRecoverExpiredDeliveringClaimsBeforeScanningPendingEvents() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        when(eventMapper.selectRetryable(any(LocalDateTime.class), eq(100))).thenReturn(Collections.emptyList());
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(eventMapper,
                mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class),
                mock(TkOpenApiCallbackHttpClient.class));

        try {
            service.deliverPending(100);

            verify(eventMapper).recoverStaleDelivering(any(LocalDateTime.class));
            verify(eventMapper).selectRetryable(any(LocalDateTime.class), eq(100));
        } finally {
            service.destroy();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSendSignedCallbackThroughPinnedHttpClient() throws Exception {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiClientMapper clientMapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, clientMapper, secretCipher, httpClient);
        TkOpenApiEventDO event = TkOpenApiEventDO.builder()
                .id(1L)
                .eventId("evt_1")
                .clientId("client_a")
                .callbackUrl("https://8.8.8.8/tk/events")
                .payloadJson("{}")
                .status("PENDING")
                .attemptCount(0)
                .build();
        TkOpenApiClientDO client = TkOpenApiClientDO.builder()
                .clientId("client_a")
                .callbackSecretCipher("callback-cipher")
                .status(0)
                .build();
        when(eventMapper.selectByEventId("evt_1")).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(clientMapper.selectByClientId("client_a")).thenReturn(client);
        when(secretCipher.decrypt("callback-cipher")).thenReturn("callback-secret");
        when(httpClient.post(any(), any(), anyMap(), eq("{}"))).thenReturn(204);

        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    TkOpenApiEventDO.class);
            service.deliver("evt_1");

            ArgumentCaptor<InetAddress[]> addresses = ArgumentCaptor.forClass(InetAddress[].class);
            ArgumentCaptor<Map> headers = ArgumentCaptor.forClass(Map.class);
            verify(httpClient).post(eq(URI.create(event.getCallbackUrl())), addresses.capture(),
                    headers.capture(), eq("{}"));
            assertEquals(InetAddress.getByName("8.8.8.8"), addresses.getValue()[0]);
            assertEquals("evt_1", headers.getValue().get("X-TK-Event-Id"));
            String timestamp = (String) headers.getValue().get("X-TK-Timestamp");
            String bodyHash = TkOpenApiSigner.sha256Hex("{}".getBytes(StandardCharsets.UTF_8));
            assertEquals(TkOpenApiSigner.hmacBase64("callback-secret",
                            "evt_1\n" + timestamp + "\n" + bodyHash),
                    headers.getValue().get("X-TK-Signature"));
            verify(eventMapper, times(2)).update(isNull(), any());
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldKeepTerminalPublishCallbackRetryableAfterTheFiniteAttemptLimit() throws Exception {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiClientMapper clientMapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);
        TkOpenApiEventDO event = publishEvent("publish.success", "success-1", "publish-1", 7);
        when(eventMapper.selectByEventId(event.getEventId())).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(clientMapper.selectByClientId("client_a")).thenReturn(TkOpenApiClientDO.builder()
                .clientId("client_a").callbackSecretCipher("cipher").status(0).build());
        when(secretCipher.decrypt("cipher")).thenReturn("secret");
        when(httpClient.post(any(), any(), anyMap(), anyString())).thenReturn(503);
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, clientMapper, secretCipher, httpClient);
        try {
            service.deliver(event.getEventId());

            Map<String, Object> values = lastUpdateValues(eventMapper);
            org.junit.jupiter.api.Assertions.assertTrue(values.containsValue("RETRYING"));
            org.junit.jupiter.api.Assertions.assertTrue(values.containsValue(8));
            LocalDateTime nextRetryTime = values.values().stream()
                    .filter(LocalDateTime.class::isInstance).map(LocalDateTime.class::cast)
                    .findFirst().orElse(LocalDateTime.MIN);
            org.junit.jupiter.api.Assertions.assertTrue(nextRetryTime.isAfter(LocalDateTime.now().plusHours(5)
                    .plusMinutes(59)));
            org.junit.jupiter.api.Assertions.assertTrue(nextRetryTime.isBefore(LocalDateTime.now().plusHours(6)
                    .plusMinutes(1)), "terminal callback backoff should be capped at six hours");
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldKeepFiniteRetryLimitForNonTerminalCallbackEvents() throws Exception {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiClientMapper clientMapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);
        TkOpenApiEventDO event = publishEvent("authorization.failed", "auth-1", null, 7);
        when(eventMapper.selectByEventId(event.getEventId())).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(clientMapper.selectByClientId("client_a")).thenReturn(TkOpenApiClientDO.builder()
                .clientId("client_a").callbackSecretCipher("cipher").status(0).build());
        when(secretCipher.decrypt("cipher")).thenReturn("secret");
        when(httpClient.post(any(), any(), anyMap(), anyString())).thenReturn(503);
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, clientMapper, secretCipher, httpClient);
        try {
            service.deliver(event.getEventId());

            org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).containsValue("FAILED"));
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldSkipProcessingCallbackWhenDetailIsAlreadyTerminal() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiEventDO event = publishEvent("publish.processing", "detail-1", "publish-1", 0);
        when(eventMapper.selectByEventId(event.getEventId())).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(detailMapper.selectByClientAndDetailId("client_a", "detail-1"))
                .thenReturn(TkOpenTiktokPublishDetailDO.builder()
                        .clientId("client_a").detailId("detail-1").status("SUCCESS")
                        .publishId("publish-1").build());
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class),
                httpClient, detailMapper);
        try {
            service.deliver(event.getEventId());

            verifyNoInteractions(httpClient);
            org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).containsValue("SKIPPED"));
            org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).values().stream()
                    .anyMatch(value -> String.valueOf(value).contains("already terminal")));
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldSkipProcessingCallbackWhenPublishIdBelongsToAnOlderAttempt() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiEventDO event = publishEvent("publish.processing", "detail-1", "publish-old", 0);
        when(eventMapper.selectByEventId(event.getEventId())).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(detailMapper.selectByClientAndDetailId("client_a", "detail-1"))
                .thenReturn(TkOpenTiktokPublishDetailDO.builder()
                        .clientId("client_a").detailId("detail-1").status("PROCESSING")
                        .publishId("publish-current").build());
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class),
                httpClient, detailMapper);
        try {
            service.deliver(event.getEventId());

            verifyNoInteractions(httpClient);
            org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).containsValue("SKIPPED"));
            org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).values().stream()
                    .anyMatch(value -> String.valueOf(value).contains("publishId no longer matches")));
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldSkipHistoricalHeuristicTerminalCallbacksWithoutHttp() {
        for (Map.Entry<String, ? extends Object> scenario : Arrays.asList(
                new java.util.AbstractMap.SimpleEntry<>("publish.failed", "TikTok publish was not found after reconciliation"),
                new java.util.AbstractMap.SimpleEntry<>("publish.success", null))) {
            TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
            TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
            TkOpenApiEventDO event = publishEvent(scenario.getKey(), "detail-1", null, 0);
            event.setPayloadJson(scenario.getValue() == null
                    ? "{\"status\":\"SUCCESS\",\"publishId\":null}"
                    : "{\"status\":\"FAILED\",\"failReason\":\"TikTok publish was not found after reconciliation\"}");
            when(eventMapper.selectByEventId(event.getEventId())).thenReturn(event);
            when(eventMapper.update(isNull(), any())).thenReturn(1);
            TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                    eventMapper, mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class),
                    httpClient);
            try {
                service.deliver(event.getEventId());
                verifyNoInteractions(httpClient);
                org.junit.jupiter.api.Assertions.assertTrue(lastUpdateValues(eventMapper).containsValue("SKIPPED"));
            } finally {
                service.destroy();
            }
        }
    }

    @Test
    void shouldLeaveDurablePendingEventWhenAfterCommitSchedulingIsRejected() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiClientMapper clientMapper = mock(TkOpenApiClientMapper.class);
        when(clientMapper.selectByClientId("client_a")).thenReturn(TkOpenApiClientDO.builder()
                .clientId("client_a").publishCallbackUrl("https://8.8.8.8/callback").status(0).build());
        when(eventMapper.insert(any(TkOpenApiEventDO.class))).thenReturn(1);
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, clientMapper, mock(TkOpenApiSecretCipher.class), null);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.destroy();
            service.enqueue("client_a", "publish.success", "PUBLISH_DETAIL", "detail-1", null);

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                    TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private TkOpenApiEventDO publishEvent(String eventType, String resourceId, String publishId,
                                          int attemptCount) {
        String payload = publishId == null
                ? "{\"status\":\"" + ("publish.failed".equals(eventType) ? "FAILED" : "SUCCESS") + "\",\"publishId\":null}"
                : "{\"status\":\"PROCESSING\",\"publishId\":\"" + publishId + "\"}";
        return TkOpenApiEventDO.builder().id(1L).eventId("evt-1").clientId("client_a")
                .eventType(eventType).resourceType("PUBLISH_DETAIL").resourceId(resourceId)
                .callbackUrl("https://8.8.8.8/callback").payloadJson(payload).status("PENDING")
                .attemptCount(attemptCount).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> lastUpdateValues(TkOpenApiEventMapper eventMapper) {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TkOpenApiEventDO>>
                captor = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(eventMapper, atLeastOnce()).update(isNull(), captor.capture());
        return new HashMap<>(captor.getAllValues().get(captor.getAllValues().size() - 1).getParamNameValuePairs());
    }
}
