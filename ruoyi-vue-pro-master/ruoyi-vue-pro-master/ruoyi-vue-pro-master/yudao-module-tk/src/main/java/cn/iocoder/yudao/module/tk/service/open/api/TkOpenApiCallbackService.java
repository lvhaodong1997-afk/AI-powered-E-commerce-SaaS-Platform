package cn.iocoder.yudao.module.tk.service.open.api;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiCallbackUrlValidator;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSigner;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class TkOpenApiCallbackService implements TkOpenApiCallbackOperations {

    private static final int MAX_ATTEMPTS = 8;
    private static final int DELIVERY_LEASE_MINUTES = 5;
    private final TkOpenApiEventMapper eventMapper;
    private final TkOpenApiClientMapper clientMapper;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkOpenApiCallbackHttpClient callbackHttpClient;
    @Resource
    private TkOpenTiktokPublishDetailMapper publishDetailMapper;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    public TkOpenApiCallbackService(TkOpenApiEventMapper eventMapper, TkOpenApiClientMapper clientMapper,
                                    TkOpenApiSecretCipher secretCipher,
                                    TkOpenApiCallbackHttpClient callbackHttpClient) {
        this.eventMapper = eventMapper;
        this.clientMapper = clientMapper;
        this.secretCipher = secretCipher;
        this.callbackHttpClient = callbackHttpClient;
    }

    public TkOpenApiCallbackService(TkOpenApiEventMapper eventMapper, TkOpenApiClientMapper clientMapper,
                                    TkOpenApiSecretCipher secretCipher,
                                    TkOpenApiCallbackHttpClient callbackHttpClient,
                                    TkOpenTiktokPublishDetailMapper publishDetailMapper) {
        this(eventMapper, clientMapper, secretCipher, callbackHttpClient);
        this.publishDetailMapper = publishDetailMapper;
    }

    public String enqueue(String clientId, String eventType, String resourceType, String resourceId,
                          Map<String, Object> payload) {
        return enqueueInternal(clientId, eventType, resourceType, resourceId, payload,
                null);
    }

    /** Read-only diagnostics, scoped to the current attempt; one query for an entire queue page. */
    public Map<String, TkOpenApiEventDO> currentPublishCallbacks(java.util.List<TkOpenTiktokPublishDetailDO> details) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (TkOpenTiktokPublishDetailDO detail : details) {
            if (!java.util.Arrays.asList("SUCCESS", "FAILED", "CANCELLED").contains(detail.getStatus())) continue;
            String key = buildDedupeKey(detail.getClientId(), "publish." + detail.getStatus().toLowerCase(java.util.Locale.ROOT),
                    "PUBLISH_DETAIL", detail.getDetailId()) + "|" + (detail.getRetryCount() == null ? 0 : detail.getRetryCount());
            keys.put(key, detail.getClientId() + "|" + detail.getDetailId());
        }
        Map<String, TkOpenApiEventDO> result = new LinkedHashMap<>();
        if (keys.isEmpty()) return result;
        for (TkOpenApiEventDO event : eventMapper.selectList(Wrappers.<TkOpenApiEventDO>lambdaQuery()
                .in(TkOpenApiEventDO::getDedupeKey, keys.keySet()))) {
            String key = keys.get(event.getDedupeKey());
            if (key != null) result.put(key, event);
        }
        return result;
    }

    public String enqueueOnce(String clientId, String eventType, String resourceType, String resourceId,
                              Map<String, Object> payload) {
        return enqueueOnce(clientId, eventType, resourceType, resourceId, payload, 0);
    }

    public String enqueueOnce(String clientId, String eventType, String resourceType, String resourceId,
                              Map<String, Object> payload, int publishAttempt) {
        String dedupeKey = buildDedupeKey(clientId, eventType, resourceType, resourceId) + "|" + publishAttempt;
        TkOpenApiEventDO existing = eventMapper.selectByDedupeKey(clientId, dedupeKey);
        if (existing != null) {
            return existing.getEventId();
        }
        try {
            return enqueueInternal(clientId, eventType, resourceType, resourceId, payload, dedupeKey);
        } catch (DuplicateKeyException ex) {
            TkOpenApiEventDO winner = eventMapper.selectByDedupeKey(clientId, dedupeKey);
            if (winner != null) return winner.getEventId();
            throw ex;
        }
    }

    private String enqueueInternal(String clientId, String eventType, String resourceType, String resourceId,
                                   Map<String, Object> payload, String dedupeKey) {
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
                .dedupeKey(dedupeKey)
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

    private String buildDedupeKey(String clientId, String eventType, String resourceType, String resourceId) {
        return String.join("|", StrUtil.nullToEmpty(clientId), StrUtil.nullToEmpty(eventType),
                StrUtil.nullToEmpty(resourceType), StrUtil.nullToEmpty(resourceId));
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
        String historicalSkipReason = historicalTerminalSkipReason(event);
        if (StrUtil.isNotBlank(historicalSkipReason)) {
            markSkipped(event, historicalSkipReason);
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
            String staleProcessingReason = staleProcessingReason(event);
            if (StrUtil.isNotBlank(staleProcessingReason)) {
                markSkipped(event, staleProcessingReason);
                return;
            }
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
        Runnable action = () -> {
            try {
                executor.submit(() -> deliver(eventId));
            } catch (RejectedExecutionException ex) {
                // The durable PENDING row remains available to the retry job.
                log.warn("[submitAfterCommit][eventId({}) scheduling rejected; leaving durable callback pending]",
                        eventId);
            }
        };
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
        boolean exhausted = attempt >= MAX_ATTEMPTS && !isTerminalPublishCallback(event.getEventType());
        eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .set(TkOpenApiEventDO::getStatus, exhausted ? "FAILED" : "RETRYING")
                .set(TkOpenApiEventDO::getAttemptCount, attempt)
                .set(TkOpenApiEventDO::getLastHttpStatus, httpStatus)
                .set(TkOpenApiEventDO::getLastError, StrUtil.maxLength(error, 1000))
                .set(TkOpenApiEventDO::getNextRetryTime,
                        exhausted ? null : LocalDateTime.now().plusMinutes(backoffMinutes(attempt))));
    }

    private void markSkipped(TkOpenApiEventDO event, String reason) {
        eventMapper.update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getId, event.getId())
                .in(TkOpenApiEventDO::getStatus, java.util.Arrays.asList("PENDING", "RETRYING", "DELIVERING"))
                .set(TkOpenApiEventDO::getStatus, "SKIPPED")
                .set(TkOpenApiEventDO::getLastError, StrUtil.maxLength(reason, 1000))
                .set(TkOpenApiEventDO::getNextRetryTime, null));
    }

    private String historicalTerminalSkipReason(TkOpenApiEventDO event) {
        if ("publish.failed".equals(event.getEventType())
                && "TikTok publish was not found after reconciliation".equals(payloadValue(event, "failReason"))) {
            return "publish.failed callback skipped because reconciliation did not find the TikTok publish";
        }
        if ("publish.success".equals(event.getEventType())
                && StrUtil.isBlank(payloadValue(event, "publishId"))) {
            return "publish.success callback skipped because publishId is missing";
        }
        return null;
    }

    private String staleProcessingReason(TkOpenApiEventDO event) {
        if (!("publish.processing".equals(event.getEventType()) || isTerminalPublishCallback(event.getEventType()))
                || !"PUBLISH_DETAIL".equals(event.getResourceType()) || publishDetailMapper == null) return null;
        String prefix = event.getEventType() + " callback skipped because ";
        TkOpenTiktokPublishDetailDO current = publishDetailMapper.selectByClientAndDetailId(
                event.getClientId(), event.getResourceId());
        if (current == null) {
            return prefix + "publish detail no longer exists";
        }
        if ("publish.processing".equals(event.getEventType())
                && ("SUCCESS".equals(current.getStatus()) || "FAILED".equals(current.getStatus()) || "CANCELLED".equals(current.getStatus()))) {
            return prefix + "publish detail is already terminal";
        }
        if (!Objects.equals(normalize(payloadValue(event, "publishId")), normalize(current.getPublishId()))) {
            return prefix + "publishId no longer matches the current attempt";
        }
        String retry = payloadValue(event, "retryCount");
        if (retry != null && !Objects.equals(retry, String.valueOf(current.getRetryCount() == null ? 0 : current.getRetryCount())))
            return prefix + "publish attempt no longer matches";
        if (isTerminalPublishCallback(event.getEventType()) && StrUtil.isNotBlank(event.getDedupeKey())
                && !event.getDedupeKey().equals(buildDedupeKey(event.getClientId(), event.getEventType(),
                event.getResourceType(), event.getResourceId()) + "|" + (current.getRetryCount() == null ? 0 : current.getRetryCount())))
            return prefix + "publish attempt no longer matches";
        String expectedStatus = "publish.success".equals(event.getEventType()) ? "SUCCESS"
                : "publish.failed".equals(event.getEventType()) ? "FAILED"
                : "publish.cancelled".equals(event.getEventType()) ? "CANCELLED" : null;
        if (expectedStatus != null && !expectedStatus.equals(current.getStatus()))
            return prefix + "publish status no longer matches";
        return null;
    }

    private String payloadValue(TkOpenApiEventDO event, String field) {
        try {
            JsonNode root = JsonUtils.parseTree(event.getPayloadJson());
            JsonNode value = root == null ? null : root.get(field);
            return value == null || value.isNull() ? null : value.asText();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalize(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    private boolean isTerminalPublishCallback(String eventType) {
        return "publish.success".equals(eventType) || "publish.failed".equals(eventType)
                || "publish.cancelled".equals(eventType);
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
