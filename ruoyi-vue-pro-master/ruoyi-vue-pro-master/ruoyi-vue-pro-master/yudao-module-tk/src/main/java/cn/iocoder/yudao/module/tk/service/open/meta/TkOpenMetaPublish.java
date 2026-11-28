package cn.iocoder.yudao.module.tk.service.open.meta;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static cn.iocoder.yudao.module.tk.service.open.meta.TkOpenMetaAuth.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TkOpenMetaPublish {
    private final JdbcTemplate db;
    private final TkOpenMetaAccess access;
    private final TkOpenMetaAuth auth;
    private final TkOpenMetaMedia media;
    private final TkSocialPlatformClient provider;
    private final TkSocialTokenCipher cipher;
    private final TkOpenApiCallbackService callbacks;
    private final ObjectMapper json;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService lane = Executors.newSingleThreadExecutor(action -> {
        Thread worker = new Thread(action, "tk-open-meta-publish");
        worker.setDaemon(true);
        return worker;
    });

    public Map<String, Object> create(String platform, String connectionId, String mediaId, String type,
                                      String text, String key, String headerKey) {
        TkOpenMetaAccess.Scope s = access.require(platform, "publish");
        String body = text == null ? "" : text;
        validate(platform, mediaId, type, body, key, headerKey);
        String hash = requestHash(s, connectionId, mediaId, type, body);
        Map<String, Object> previous = byKey(s, key);
        if (previous != null) return replay(previous, hash);
        auth.token(s, auth.connection(s, connectionId));
        if (mediaId != null) {
            access.baseUrl();
            String expected = "IMAGE".equals(type) ? "image/jpeg" : "video/mp4";
            if (!expected.equals(media.ready(s, mediaId).get("content_type")))
                throw TkOpenApiException.badRequest("META_MEDIA_INVALID", "mediaType does not match the upload");
        }
        String id = TkOpenApiIds.next("metatask");
        try {
            db.update("INSERT INTO tk_open_meta_task (id,client_id,workspace_id,platform,connection_id,media_id,media_type,body_text,"
                            + "idempotency_key,request_hash,status,next_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING',"
                            + "CURRENT_TIMESTAMP(3),DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 30 MINUTE))",
                    id, s.clientId, s.workspaceId, s.platform, connectionId, mediaId, type, body, key, hash);
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> winner = byKey(s, key);
            if (winner == null) throw duplicate;
            return replay(winner, hash);
        }
        return view(find(s, id));
    }

    public Map<String, Object> get(String platform, String id) { return view(find(access.require(platform, "publish"), id)); }

    static void validate(String platform, String mediaId, String type, String text, String key, String headerKey) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}") || (headerKey != null && !headerKey.equals(key)))
            throw TkOpenApiException.badRequest("META_IDEMPOTENCY_INVALID", "Provide a signed idempotencyKey matching the optional header");
        if (text.length() > 2200 || !("TEXT".equals(type)
                ? TkOpenMetaAccess.FACEBOOK.equals(platform) && mediaId == null && !text.trim().isEmpty()
                : ("IMAGE".equals(type) || "VIDEO".equals(type)) && mediaId != null && mediaId.matches("metamedia_[0-9a-f]{32}")))
            throw TkOpenApiException.badRequest("META_PUBLISH_INVALID", "Unsupported platform, mediaType, text or mediaId combination");
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void tick() {
        if ((!access.enabled("facebook") && !access.enabled("instagram")) || !running.compareAndSet(false, true)) return;
        try {
            lane.execute(() -> {
                try { TenantUtils.executeIgnore(this::run); }
                catch (RuntimeException ex) { log.warn("Meta publish scan failed: type={}", ex.getClass().getSimpleName()); }
                finally { running.set(false); }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) { running.set(false); }
    }

    @PreDestroy
    public void stop() {
        lane.shutdown();
        try { if (!lane.awaitTermination(25, TimeUnit.SECONDS)) lane.shutdownNow(); }
        catch (InterruptedException ex) { lane.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    private void run() {
        for (String platform : Arrays.asList("facebook", "instagram")) {
            if (!access.enabled(platform)) continue;
            db.update("UPDATE tk_open_meta_task SET status='UNKNOWN',error_code='META_LEASE_EXPIRED',lease_id=NULL,lease_until=NULL "
                    + "WHERE platform=? AND status='PROCESSING' AND lease_until<=CURRENT_TIMESTAMP(3)", platform);
            db.update("UPDATE tk_open_meta_task SET status=IF(platform_status='FB_FINISH_SUBMITTED','UNKNOWN','FAILED'),"
                    + "error_code='META_TASK_EXPIRED' WHERE platform=? AND status='PENDING' AND expires_at<=CURRENT_TIMESTAMP(3)", platform);
            for (String id : db.queryForList("SELECT id FROM tk_open_meta_task WHERE platform=? AND status='PENDING' "
                    + "AND next_at<=CURRENT_TIMESTAMP(3) ORDER BY next_at LIMIT 3", String.class, platform)) {
                try { advance(platform, id); }
                catch (RuntimeException ex) { log.warn("Meta worker failed: task={}, type={}", id, ex.getClass().getSimpleName()); }
            }
            for (Map<String, Object> row : db.queryForList("SELECT * FROM tk_open_meta_task WHERE platform=? AND notified=0 "
                    + "AND status IN ('SUCCESS','FAILED','UNKNOWN','REAUTH_REQUIRED') LIMIT 30", platform)) {
                String id = str(row, "id");
                try {
                    callbacks.enqueueOnce(str(row, "client_id"), "publish.completed", "META_PUBLISH", id, view(row));
                    db.update("UPDATE tk_open_meta_task SET notified=1 WHERE id=?", id);
                } catch (RuntimeException ex) { log.warn("Meta callback enqueue failed: task={}, type={}", id, ex.getClass().getSimpleName()); }
            }
        }
    }

    private void advance(String platform, String id) {
        String lease = TkOpenApiIds.next("metalease");
        if (db.update("UPDATE tk_open_meta_task SET status='PROCESSING',lease_id=?,lease_until=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 3 MINUTE) "
                + "WHERE id=? AND platform=? AND status='PENDING' AND next_at<=CURRENT_TIMESTAMP(3) AND expires_at>CURRENT_TIMESTAMP(3)", lease, id, platform) != 1) return;
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_task WHERE id=? AND lease_id=?", id, lease));
        if (row == null) return;
        TkOpenMetaAccess.Scope s = new TkOpenMetaAccess.Scope(str(row, "client_id"), str(row, "workspace_id"), platform);
        String status, error = null;
        TkSocialPlatformClient.PublishResult r = null;
        boolean submitted = "FB_FINISH_SUBMITTED".equals(row.get("platform_status"));
        boolean invoked = false;
        try {
            if (!access.allowed(s.clientId, platform, "publish")) throw TkOpenApiException.forbidden("OPEN_API_PERMISSION_DENIED", "Permission revoked");
            Map<String, Object> conn = auth.connection(s, str(row, "connection_id"));
            String token = auth.token(s, conn);
            String mediaId = str(row, "media_id");
            String url = mediaId == null ? null : mediaUrl(platform, id, mediaId);
            invoked = true;
            r = provider.advance(TkOpenMetaAccess.provider(platform), str(conn, "external_id"), token, str(row, "media_type"),
                    str(row, "body_text"), url, str(row, "container_id"), str(row, "platform_status"));
            status = "SUCCESS".equals(r.getStatus()) ? "SUCCESS" : "PENDING";
        } catch (TkSocialPlatformException ex) {
            status = failureStatus(ex, submitted);
            error = ex.getCode();
        } catch (TkOpenApiException ex) {
            status = submitted ? "UNKNOWN" : "META_REAUTH_REQUIRED".equals(ex.getCode()) ? "REAUTH_REQUIRED" : "FAILED";
            error = ex.getCode();
        } catch (RuntimeException ex) {
            status = invoked || submitted ? "UNKNOWN" : "FAILED";
            error = "META_INTERNAL_ERROR";
        }
        db.update("UPDATE tk_open_meta_task SET status=?,error_code=?,next_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 10 SECOND),"
                        + "container_id=?,platform_status=?,provider_media_id=?,provider_post_id=?,publish_url=?,lease_id=NULL,lease_until=NULL "
                        + "WHERE id=? AND status='PROCESSING' AND lease_id=? AND lease_until>CURRENT_TIMESTAMP(3)", status, error,
                r == null || r.getContainerId() == null ? row.get("container_id") : r.getContainerId(),
                r == null ? row.get("platform_status") : r.getPlatformStatus(), r == null ? null : r.getMediaId(),
                r == null ? null : r.getPostId(), r == null ? null : r.getPublishUrl(), id, lease);
    }

    static String failureStatus(TkSocialPlatformException ex, boolean submitted) {
        if (ex.isUncertain()) return "UNKNOWN";
        if (ex.isRetryable()) return "PENDING";
        if (submitted && !Arrays.asList("FB_VIDEO_ERROR", "FB_VIDEO_PROCESSING_FAILED").contains(ex.getCode())) return "UNKNOWN";
        return ex.isReauthRequired() ? "REAUTH_REQUIRED" : "FAILED";
    }

    public ProviderMedia providerMedia(String platform, String task, String ticket) {
        String mediaId;
        try {
            if (!access.enabled(platform) || !task.matches("metatask_[0-9a-f]{32}") || ticket == null
                    || !ticket.matches("[A-Za-z0-9_-]{1,256}")) throw new IllegalArgumentException();
            String encoded = new String(Base64.getUrlDecoder().decode(ticket), StandardCharsets.US_ASCII);
            mediaId = verifyTicket(cipher.decrypt(encoded, ticketContext(platform, task)), Instant.now().getEpochSecond());
        } catch (RuntimeException ex) { return null; }
        Map<String, Object> row = one(db.queryForList("SELECT t.client_id,m.content_type FROM tk_open_meta_task t "
                + "JOIN tk_open_meta_media m ON m.id=t.media_id AND m.client_id=t.client_id AND m.workspace_id=t.workspace_id AND m.platform=t.platform "
                + "JOIN tk_open_meta_connection c ON c.id=t.connection_id AND c.client_id=t.client_id AND c.workspace_id=t.workspace_id AND c.platform=t.platform "
                + "WHERE t.id=? AND t.platform=? AND m.id=? AND m.status='READY' AND c.status='AUTHORIZED' "
                + "AND (c.token_expires_at IS NULL OR c.token_expires_at>CURRENT_TIMESTAMP(3)) "
                + "AND t.status IN ('PENDING','PROCESSING') AND t.expires_at>CURRENT_TIMESTAMP(3)", task, platform, mediaId));
        if (row == null || !access.allowed(str(row, "client_id"), platform, "publish")) return null;
        Path file = media.file(mediaId);
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                ? new ProviderMedia(new FileSystemResource(file), str(row, "content_type")) : null;
    }

    @lombok.Value
    public static class ProviderMedia {
        FileSystemResource resource;
        String contentType;
    }

    static String verifyTicket(String plaintext, long now) {
        String[] parts = plaintext.split(":", -1);
        if (parts.length != 2 || !parts[0].matches("metamedia_[0-9a-f]{32}") || Long.parseLong(parts[1]) <= now)
            throw new IllegalArgumentException();
        return parts[0];
    }

    private String mediaUrl(String platform, String task, String mediaId) {
        String encoded = cipher.encrypt(mediaId + ":" + (Instant.now().getEpochSecond() + 2700), ticketContext(platform, task));
        return access.baseUrl() + TkOpenMetaAccess.ROOT + platform + "/provider-media/" + task + "?ticket="
                + Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.getBytes(StandardCharsets.US_ASCII));
    }

    private static String ticketContext(String platform, String task) { return "meta-open:provider-media:" + platform + ":" + task; }

    private String requestHash(TkOpenMetaAccess.Scope s, String connection, String mediaId, String type, String text) {
        try { return TkSocialAuthService.hash(json.writeValueAsString(Arrays.asList(s.workspaceId, connection, mediaId, type, text))); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }

    private Map<String, Object> byKey(TkOpenMetaAccess.Scope s, String key) {
        return one(db.queryForList("SELECT * FROM tk_open_meta_task WHERE client_id=? AND workspace_id=? AND platform=? AND idempotency_key=?",
                s.clientId, s.workspaceId, s.platform, key));
    }

    private Map<String, Object> find(TkOpenMetaAccess.Scope s, String id) {
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_task" + SCOPED, s.args(id)));
        if (row == null) throw TkOpenApiException.notFound("META_TASK_NOT_FOUND", "Publish task not found");
        return row;
    }

    static Map<String, Object> replay(Map<String, Object> row, String hash) {
        if (!Objects.equals(row.get("request_hash"), hash))
            throw TkOpenApiException.conflict("META_IDEMPOTENCY_CONFLICT", "idempotencyKey already used for a different request");
        return view(row);
    }

    private static Map<String, Object> view(Map<String, Object> row) {
        return result("taskId", row.get("id"), "platform", row.get("platform"), "workspaceId", row.get("workspace_id"),
                "connectionId", row.get("connection_id"), "mediaType", row.get("media_type"), "status", row.get("status"),
                "platformStatus", row.get("platform_status"), "providerContainerId", row.get("container_id"), "providerMediaId", row.get("provider_media_id"),
                "providerPostId", row.get("provider_post_id"), "publishUrl", row.get("publish_url"), "errorCode", row.get("error_code"));
    }
}
