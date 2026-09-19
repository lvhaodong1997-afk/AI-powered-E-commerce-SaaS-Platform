package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishPostDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishPostMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokWebhookEventMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokPublishTerminalService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkTiktokWebhookServiceImpl implements TkTiktokWebhookService {

    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 600L;
    private static final String EVENT_PUBLICLY_AVAILABLE = "post.publish.publicly_available";
    private static final String EVENT_NO_LONGER_PUBLIC = "post.publish.no_longer_publicaly_available";
    private static final String EVENT_COMPLETE = "post.publish.complete";
    private static final String EVENT_FAILED = "post.publish.failed";

    // The existing DATETIME column doubles as last-attempt time and a recoverable processing lease.
    private static final long RETRY_DELAY_SECONDS = 300L;
    private final ExecutorService executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.AbortPolicy());
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    @Resource
    private TkApiKeyConfigService configService;
    @Resource
    private TkTiktokWebhookEventMapper eventMapper;
    @Resource
    private TkTiktokPublishDetailMapper detailMapper;
    @Resource
    private TkTiktokPublishPostMapper publishPostMapper;
    @Resource
    private TkTiktokPublishService publishService;
    @Resource
    private TkOpenTiktokPublishDetailMapper openDetailMapper;
    @Resource
    private TkOpenTiktokConnectionMapper connectionMapper;
    @Resource
    private TkOpenTiktokPublishTerminalService terminalService;

    @Override
    public void receive(String rawBody, String signature) {
        if (StrUtil.isBlank(rawBody) || !verifySignature(signature,
                configService.getValue(TkTiktokApiClient.PROVIDER, "client-secret"), rawBody,
                Instant.now().getEpochSecond())) {
            throw new IllegalArgumentException("TikTok Webhook 签名无效或已过期");
        }
        WebhookPayload payload = parse(rawBody);
        TkTiktokWebhookEventDO event = eventMapper.selectByEventId(payload.getEventId());
        if (event != null) {
            return;
        }
        event = TkTiktokWebhookEventDO.builder()
                .eventId(payload.getEventId())
                .clientKey(payload.getClientKey())
                .userOpenId(payload.getUserOpenId())
                .eventType(payload.getEventType())
                .publishId(payload.getPublishId())
                .postId(payload.getPostId())
                .payloadJson(rawBody)
                .status("RECEIVED")
                .receivedTime(LocalDateTime.now())
                .build();
        try {
            eventMapper.insert(event);
        } catch (Exception duplicate) {
            if (eventMapper.selectByEventId(payload.getEventId()) != null) {
                return;
            }
            throw duplicate;
        }
        schedule(event.getId());
    }

    public void retryPending(int limit) {
        List<TkTiktokWebhookEventDO> pending = eventMapper.selectRetryBatch(
                now().minusSeconds(RETRY_DELAY_SECONDS), Math.max(1, Math.min(limit, 100)));
        for (TkTiktokWebhookEventDO event : pending) {
            schedule(event.getId());
        }
    }

    private void schedule(Long eventId) {
        if (!inFlight.add(eventId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    TenantUtils.executeIgnore(() -> process(eventId));
                } catch (Exception ex) {
                    // Includes inbox read/write failures: stored RECEIVED or expired RETRYING is replayed.
                    log.warn("[schedule][TikTok Webhook worker failed, id({})]", eventId, ex);
                } finally {
                    inFlight.remove(eventId);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlight.remove(eventId);
            log.warn("[schedule][TikTok Webhook scheduling rejected, id({})]", eventId, ex);
            LocalDateTime claimedAt = now();
            try {
                if (eventMapper.claimForProcessing(eventId, claimedAt,
                        claimedAt.minusSeconds(RETRY_DELAY_SECONDS)) == 1) {
                    finish(eventId, claimedAt, "FAILED", "Webhook scheduling rejected; durable replay pending");
                }
            } catch (Exception persistenceFailure) {
                log.warn("[schedule][Failed to record scheduling rejection, id({}); durable inbox retained]",
                        eventId, persistenceFailure);
            }
        }
    }

    private void process(Long eventId) {
        TkTiktokWebhookEventDO event = eventMapper.selectById(eventId);
        if (event == null || !("RECEIVED".equals(event.getStatus())
                || "RETRYING".equals(event.getStatus()) || "FAILED".equals(event.getStatus()))) {
            return;
        }
        LocalDateTime claimedAt = now();
        if (eventMapper.claimForProcessing(eventId, claimedAt,
                claimedAt.minusSeconds(RETRY_DELAY_SECONDS)) != 1) {
            return;
        }
        try {
            WebhookPayload payload = parse(event.getPayloadJson());
            if (!isKnownEvent(payload.getEventType())) {
                finish(eventId, claimedAt, "IGNORED", "Unsupported webhook event type");
                return;
            }
            if (StrUtil.isBlank(payload.getPublishId())) {
                finish(eventId, claimedAt, "RETRYING", "Missing publish_id; cannot safely correlate event");
                return;
            }
            // Never infer an external publication from a title, timestamp, account or post ID.
            List<TkOpenTiktokPublishDetailDO> external = openDetailMapper.selectList(
                    new QueryWrapper<TkOpenTiktokPublishDetailDO>()
                            .eq("publish_id", payload.getPublishId())
                            .apply("BINARY publish_id = {0}", payload.getPublishId()).last("LIMIT 2"));
            if (!external.isEmpty()) {
                if (external.size() != 1) {
                    finish(eventId, claimedAt, "CONFLICT", "Multiple external details have the same publish_id");
                    return;
                }
                applyExternalEvent(eventId, claimedAt, external.get(0), payload);
                return; // An external rejection must NEVER fall through to the internal publisher.
            }
            TkTiktokPublishDetailDO detail = TenantUtils.executeIgnore(
                    () -> detailMapper.selectByPublishId(payload.getPublishId()));
            if (detail == null) {
                finish(eventId, claimedAt, "RETRYING", "publish_id not persisted or not matched yet");
                return;
            }
            if ((EVENT_FAILED.equals(payload.getEventType()) && "SUCCESS".equals(detail.getStatus()))
                    || ((EVENT_COMPLETE.equals(payload.getEventType())
                    || EVENT_PUBLICLY_AVAILABLE.equals(payload.getEventType())) && "FAILED".equals(detail.getStatus()))) {
                finish(eventId, claimedAt, "CONFLICT", "Webhook contradicts existing internal publishing terminal");
                return;
            }
            TenantUtils.execute(detail.getTenantId(), () -> applyEvent(detail, payload));
            finish(eventId, claimedAt, "PROCESSED", null);
        } catch (Exception ex) {
            log.warn("[process][TikTok Webhook 处理失败，eventId({})]", event.getEventId(), ex);
            finish(eventId, claimedAt, "FAILED", StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName()));
        }
    }

    private void applyExternalEvent(Long eventId, LocalDateTime claimedAt,
                                    TkOpenTiktokPublishDetailDO detail, WebhookPayload payload) {
        String clientKey = configService.getValue(TkTiktokApiClient.PROVIDER, "client-key");
        if (StrUtil.isBlank(clientKey) || !clientKey.equals(payload.getClientKey())) {
            finish(eventId, claimedAt, "REJECTED", "Platform client_key mismatch");
            return;
        }
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(
                detail.getClientId(), detail.getConnectionId());
        if (connection == null || StrUtil.isBlank(payload.getUserOpenId())
                || !Objects.equals(detail.getClientId(), connection.getClientId())
                || !Objects.equals(detail.getConnectionId(), connection.getConnectionId())
                || !payload.getUserOpenId().equals(connection.getOpenId())) {
            finish(eventId, claimedAt, "REJECTED", "External connection identity mismatch");
            return;
        }
        if (EVENT_NO_LONGER_PUBLIC.equals(payload.getEventType())) {
            finish(eventId, claimedAt, "IGNORED", "Visibility change does not alter publishing terminal");
            return;
        }
        String platformStatus = EVENT_FAILED.equals(payload.getEventType()) ? "FAILED" : "PUBLISH_COMPLETE";
        boolean confirmed = terminalService.confirm(detail, platformStatus, payload.getPostId(),
                payload.getFailReason(), "WEBHOOK");
        finish(eventId, claimedAt, confirmed ? "PROCESSED" : "CONFLICT",
                confirmed ? null : "Terminal confirmation rejected; retain evidence for investigation");
    }

    private static boolean isKnownEvent(String eventType) {
        return EVENT_COMPLETE.equals(eventType) || EVENT_PUBLICLY_AVAILABLE.equals(eventType)
                || EVENT_FAILED.equals(eventType) || EVENT_NO_LONGER_PUBLIC.equals(eventType);
    }

    private void finish(Long id, LocalDateTime claimedAt, String status, String reason) {
        if (eventMapper.finishAttempt(id, claimedAt, status, StrUtil.maxLength(reason, 512), now()) != 1) {
            log.warn("[finish][Webhook attempt superseded; preserving newer inbox state, id({})]", id);
        }
    }

    private static LocalDateTime now() {
        // Match the existing MySQL DATETIME precision for conditional claim completion.
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private void applyEvent(TkTiktokPublishDetailDO detail, WebhookPayload payload) {
        String eventType = payload.getEventType();
        if (EVENT_FAILED.equals(eventType)) {
            detail.setStatus("FAILED");
            detail.setTiktokStatus("FAILED");
            detail.setFailReason(StrUtil.blankToDefault(payload.getFailReason(), "TikTok 发布失败"));
            detail.setLastSyncTime(LocalDateTime.now());
            detailMapper.updateById(detail);
            return;
        }
        if (EVENT_NO_LONGER_PUBLIC.equals(eventType)) {
            detail.setLinkCaptureStatus("NO_LONGER_PUBLIC");
            detail.setLinkLastError("TikTok 视频已不再公开");
            detail.setLastSyncTime(LocalDateTime.now());
            detailMapper.updateById(detail);
            if (StrUtil.isNotBlank(payload.getPostId())) {
                TkTiktokPublishPostDO post = publishPostMapper.selectByPublicPostId(payload.getPostId());
                if (post != null) {
                    post.setStatus("NO_LONGER_PUBLIC");
                    post.setNoLongerPublicTime(LocalDateTime.now());
                    post.setLastSyncTime(LocalDateTime.now());
                    publishPostMapper.updateById(post);
                }
            }
            return;
        }
        if (EVENT_COMPLETE.equals(eventType) || EVENT_PUBLICLY_AVAILABLE.equals(eventType)) {
            detail.setTiktokStatus("PUBLISH_COMPLETE");
            detail.setLastSyncTime(LocalDateTime.now());
            detailMapper.updateById(detail);
            if (EVENT_PUBLICLY_AVAILABLE.equals(eventType) && StrUtil.isNotBlank(payload.getPostId())) {
                TkTiktokPublishPostDO post = publishPostMapper.selectByPublicPostId(payload.getPostId());
                if (post == null) {
                    post = new TkTiktokPublishPostDO();
                    post.setTenantId(detail.getTenantId());
                    post.setCompanyId(detail.getCompanyId());
                    post.setPublishDetailId(detail.getId());
                    post.setPublishTaskId(detail.getPublishTaskId());
                    post.setAccountId(detail.getAccountId());
                    post.setPublishId(detail.getPublishId());
                    post.setPublicPostId(payload.getPostId());
                    post.setFirstSeenTime(LocalDateTime.now());
                }
                post.setStatus("PUBLICLY_AVAILABLE");
                post.setLastSyncTime(LocalDateTime.now());
                if (post.getId() == null) {
                    publishPostMapper.insert(post);
                } else {
                    publishPostMapper.updateById(post);
                }
            }
            if (StrUtil.isNotBlank(detail.getPublishId())) {
                publishService.syncPublishLinks(detail.getId());
            }
        }
    }

    static WebhookPayload parse(String rawBody) {
        JsonNode root = JsonUtils.parseTree(rawBody);
        JsonNode content = root.path("content");
        if (content.isTextual()) {
            content = JsonUtils.parseTree(content.asText());
        }
        String eventType = root.path("event").asText(null);
        String publishId = firstText(content, root, "publish_id", "publishId");
        String postId = firstText(content, root, "post_id", "postId");
        String failReason = firstText(content, root, "reason", "fail_reason", "failReason");
        String eventId = firstText(root, root, "event_id", "eventId", "id");
        return new WebhookPayload(
                StrUtil.isBlank(eventId) ? eventId(rawBody) : eventId,
                root.path("client_key").asText(null),
                root.path("user_openid").asText(null), eventType, publishId, postId, failReason);
    }

    private static String firstText(JsonNode primary, JsonNode fallback, String... fields) {
        for (String field : fields) {
            String value = primary.path(field).asText(null);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
            value = fallback.path(field).asText(null);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    static String eventId(String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawBody.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("TikTok Webhook 事件编号生成失败", ex);
        }
    }

    static String sign(String secret, long timestamp, String rawBody) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("TikTok Webhook 签名计算失败", ex);
        }
    }

    static boolean verifySignature(String signature, String secret, String rawBody, long nowEpochSeconds) {
        if (StrUtil.isBlank(signature) || StrUtil.isBlank(secret) || StrUtil.isBlank(rawBody)) {
            return false;
        }
        String timestamp = null;
        String received = null;
        for (String item : signature.split(",")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length == 2 && "t".equals(pair[0])) timestamp = pair[1];
            if (pair.length == 2 && "s".equals(pair[0])) received = pair[1];
        }
        if (timestamp == null || received == null) return false;
        try {
            long signedAt = Long.parseLong(timestamp);
            if (Math.abs(nowEpochSeconds - signedAt) > MAX_TIMESTAMP_SKEW_SECONDS) return false;
            byte[] expected = sign(secret, signedAt, rawBody).getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(expected, received.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Data
    @AllArgsConstructor
    static class WebhookPayload {
        private String eventId;
        private String clientKey;
        private String userOpenId;
        private String eventType;
        private String publishId;
        private String postId;
        private String failReason;

    }

    @PreDestroy
    public void destroy() {
        executor.shutdown();
    }
}
