package cn.iocoder.yudao.module.tk.service.open.api;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiCallbackUrlValidator;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSigner;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class TkOpenApiCallbackService implements TkOpenApiCallbackOperations {

    private static final int MAX_ATTEMPTS = 8;
    private static final int DELIVERY_LEASE_MINUTES = 5;
    private final TkOpenApiEventMapper eventMapper;
    private final TkOpenApiClientMapper clientMapper;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkOpenApiCallbackHttpClient callbackHttpClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public TkOpenApiCallbackService(TkOpenApiEventMapper eventMapper, TkOpenApiClientMapper clientMapper,
                                    TkOpenApiSecretCipher secretCipher,
                                    TkOpenApiCallbackHttpClient callbackHttpClient) {
        this.eventMapper = eventMapper;
        this.clientMapper = clientMapper;
        this.secretCipher = secretCipher;
        this.callbackHttpClient = callbackHttpClient;
    }

    public String enqueue(String clientId, String eventType, String resourceType, String resourceId,
                          Map<String, Object> payload) {
        TkOpenApiClientDO client = clientMapper.selectByClientId(clientId);
        String callbackUrl = eventType.startsWith("authorization.")
                ? client == null ? null : client.getAuthCallbackUrl()
                : client == null ? null : client.getPublishCallbackUrl();
        String eventId = TkOpenApiIds.next("evt");
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("eventId", eventId);
        eventPayload.put("eventType", eventType);
        if (payload != null) eventPayload.putAll(payload);
        eventPayload.put("occurredAt", java.time.OffsetDateTime.now().toString());
        TkOpenApiEventDO event = TkOpenApiEventDO.builder()
                .eventId(eventId)
                .clientId(clientId)
                .eventType(eventType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .callbackUrl(callbackUrl)
                .payloadJson(JsonUtils.toJsonString(eventPayload))
                .status(StrUtil.isBlank(callbackUrl) ? "SKIPPED" : "PENDING")
                .attemptCount(0)
                .nextRetryTime(StrUtil.isBlank(callbackUrl) ? null : LocalDateTime.now())
                .build();
        eventMapper.insert(event);
        if (StrUtil.isNotBlank(callbackUrl)) {
            submitAfterCommit(eventId);
        }
        return eventId;
    }

    public int deliverPending(int limit) {
        LocalDateTime now = LocalDateTime.now();
        eventMapper.recoverStaleDelivering(now);
        int submitted = 0;
        for (TkOpenApiEventDO event : eventMapper.selectRetryable(now, limit)) {
            executor.submit(() -> deliver(event.getEventId()));
            submitted++;
        }
        return submitted;
    }

    public void replay(String eventId) {
        TkOpenApiEventDO event = eventMapper.selectByEventId(eventId);
        if (event == null) {
            throw new IllegalArgumentException("Callback event does not exist");
        }
        eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .set(TkOpenApiEventDO::getStatus, "PENDING")
                .set(TkOpenApiEventDO::getAttemptCount, 0)
                .set(TkOpenApiEventDO::getNextRetryTime, LocalDateTime.now())
                .set(TkOpenApiEventDO::getLastError, null));
        executor.submit(() -> deliver(eventId));
    }

    public void deliver(String eventId) {
        TkOpenApiEventDO event = eventMapper.selectByEventId(eventId);
        if (event == null || StrUtil.isBlank(event.getCallbackUrl())
                || !("PENDING".equals(event.getStatus()) || "RETRYING".equals(event.getStatus()))) {
            return;
        }
        int attempt = (event.getAttemptCount() == null ? 0 : event.getAttemptCount()) + 1;
        int claimed = eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .in(TkOpenApiEventDO::getStatus, java.util.Arrays.asList("PENDING", "RETRYING"))
                .set(TkOpenApiEventDO::getStatus, "DELIVERING")
                .set(TkOpenApiEventDO::getAttemptCount, attempt)
                .set(TkOpenApiEventDO::getNextRetryTime,
                        LocalDateTime.now().plusMinutes(DELIVERY_LEASE_MINUTES)));
        if (claimed <= 0) return;
        Integer httpStatus = null;
        String error = null;
        try {
            InetAddress[] validatedAddresses = TkOpenApiCallbackUrlValidator.resolveAndValidate(event.getCallbackUrl());
            TkOpenApiClientDO client = clientMapper.selectByClientId(event.getClientId());
            if (client == null || !Integer.valueOf(0).equals(client.getStatus())) {
                throw new IllegalStateException("Open API client is disabled");
            }
            String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond());
            String bodyHash = TkOpenApiSigner.sha256Hex(event.getPayloadJson().getBytes(StandardCharsets.UTF_8));
            String canonical = event.getEventId() + "\n" + timestamp + "\n" + bodyHash;
            String signature = TkOpenApiSigner.hmacBase64(secretCipher.decrypt(client.getCallbackSecretCipher()), canonical);
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("X-TK-Event-Id", event.getEventId());
            headers.put("X-TK-Timestamp", timestamp);
            headers.put("X-TK-Signature", signature);
            httpStatus = callbackHttpClient.post(URI.create(event.getCallbackUrl()), validatedAddresses,
                    headers, event.getPayloadJson());
            if (httpStatus >= 200 && httpStatus < 300) {
                markDelivered(event, attempt, httpStatus);
                return;
            }
            error = "callback returned HTTP " + httpStatus;
        } catch (Exception ex) {
            error = StrUtil.maxLength(ex.getMessage(), 1000);
            log.warn("[deliver][eventId({}) attempt({}) failed]", eventId, attempt, ex);
        }
        markRetry(event, attempt, httpStatus, error);
    }

    private void submitAfterCommit(String eventId) {
        Runnable action = () -> executor.submit(() -> deliver(eventId));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void markDelivered(TkOpenApiEventDO event, int attempt, int httpStatus) {
        eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .set(TkOpenApiEventDO::getStatus, "DELIVERED")
                .set(TkOpenApiEventDO::getAttemptCount, attempt)
                .set(TkOpenApiEventDO::getLastHttpStatus, httpStatus)
                .set(TkOpenApiEventDO::getLastError, null)
                .set(TkOpenApiEventDO::getNextRetryTime, null)
                .set(TkOpenApiEventDO::getDeliveredTime, LocalDateTime.now()));
    }

    private void markRetry(TkOpenApiEventDO event, int attempt, Integer httpStatus, String error) {
        boolean exhausted = attempt >= MAX_ATTEMPTS;
        eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .set(TkOpenApiEventDO::getStatus, exhausted ? "FAILED" : "RETRYING")
                .set(TkOpenApiEventDO::getAttemptCount, attempt)
                .set(TkOpenApiEventDO::getLastHttpStatus, httpStatus)
                .set(TkOpenApiEventDO::getLastError, StrUtil.maxLength(error, 1000))
                .set(TkOpenApiEventDO::getNextRetryTime,
                        exhausted ? null : LocalDateTime.now().plusMinutes(backoffMinutes(attempt))));
    }

    private long backoffMinutes(int attempt) {
        long[] delays = {1, 5, 15, 30, 60, 180, 360};
        return delays[Math.min(Math.max(0, attempt - 1), delays.length - 1)];
    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }
}
