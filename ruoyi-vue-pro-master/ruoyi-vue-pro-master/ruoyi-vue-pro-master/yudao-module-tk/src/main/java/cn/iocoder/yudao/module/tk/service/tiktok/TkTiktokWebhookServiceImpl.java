package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishPostDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishPostMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokWebhookEventMapper;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
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
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class TkTiktokWebhookServiceImpl implements TkTiktokWebhookService {

    private static final long MAX_TIMESTAMP_SKEW_SECONDS = 600L;
    private static final String EVENT_PUBLICLY_AVAILABLE = "post.publish.publicly_available";
    private static final String EVENT_NO_LONGER_PUBLIC = "post.publish.no_longer_publicaly_available";
    private static final String EVENT_COMPLETE = "post.publish.complete";
    private static final String EVENT_FAILED = "post.publish.failed";

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

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
        Long eventId = event.getId();
        executor.submit(() -> process(eventId));
    }

    private void process(Long eventId) {
        TkTiktokWebhookEventDO event = eventMapper.selectById(eventId);
        if (event == null || "PROCESSED".equals(event.getStatus())) {
            return;
        }
        try {
            WebhookPayload payload = parse(event.getPayloadJson());
            TkTiktokPublishDetailDO detail = StrUtil.isBlank(payload.getPublishId())
                    ? null : detailMapper.selectByPublishId(payload.getPublishId());
            if (detail == null && StrUtil.isNotBlank(payload.getPostId())) {
                TkTiktokPublishPostDO post = publishPostMapper.selectByPublicPostId(payload.getPostId());
                if (post != null) {
                    detail = detailMapper.selectById(post.getPublishDetailId());
                }
            }
            if (detail != null) {
                TkTiktokPublishDetailDO target = detail;
                TenantUtils.execute(detail.getTenantId(), () -> applyEvent(target, payload));
            }
            event.setStatus("PROCESSED");
            event.setProcessedTime(LocalDateTime.now());
            event.setFailReason(null);
            eventMapper.updateById(event);
        } catch (Exception ex) {
            event.setStatus("FAILED");
            event.setFailReason(StrUtil.maxLength(ex.getMessage(), 512));
            event.setProcessedTime(LocalDateTime.now());
            eventMapper.updateById(event);
            log.warn("[process][TikTok Webhook 处理失败，eventId({})]", event.getEventId(), ex);
        }
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
