package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenYouTubeMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.youtube.TkOpenYouTubeMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.tk.service.channel.TkOpenYouTubeAuthService.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TkOpenYouTubePublishService {
    private static final String TASKS = "tk_open_youtube_publish_task";
    private static final String PERMISSION = "youtube.publish";
    private final TkOpenYouTubeAccess access;
    private final TkOpenYouTubeAuthService auth;
    private final TkOpenYouTubeMediaService media;
    private final TkChannelVideoClient videos;
    private final TkSocialTokenCipher cipher;
    private final TkOpenApiCallbackService callbacks;
    private final JdbcTemplate db;
    private final PlatformTransactionManager transactions;

    public Map<String, Object> create(Create req, String key) {
        String client = access.requireClient(PERMISSION), workspace = access.workspaceId();
        if (key == null || !key.matches("[!-~]{1,128}"))
            throw TkOpenApiException.badRequest("YOUTUBE_IDEMPOTENCY_REQUIRED", "Idempotency-Key must contain 1 to 128 printable ASCII characters");
        String description = req.description == null ? "" : req.description;
        if (description.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 5000
                || containsMarkup(req.title) || containsMarkup(description))
            throw TkOpenApiException.badRequest("YOUTUBE_METADATA_INVALID", "metadata contains angle brackets or description exceeds 5000 UTF-8 bytes");
        String fingerprint = hash(TkChannelHttp.JSON.valueToTree(fields("connectionId", req.connectionId, "mediaId", req.mediaId,
                "title", req.title, "description", description, "privacyStatus", req.privacyStatus,
                "madeForKids", req.madeForKids, "containsSyntheticMedia", Boolean.TRUE.equals(req.containsSyntheticMedia))).toString());
        Task existing = lookupKey(client, key);
        if (existing != null) return replay(existing, workspace, fingerprint);
        auth.requireConnection(client, workspace, req.connectionId, false);
        TkOpenYouTubeMediaDO ready = media.requireReady(client, req.mediaId, workspace);
        String id = TkOpenApiIds.next("yttask");
        try {
            db.update("INSERT INTO " + TASKS + " (task_id,client_id,workspace_id,connection_id,media_id,file_size,idempotency_key,payload_hash,"
                            + "title,description,privacy_status,made_for_kids,contains_synthetic_media,status,next_retry_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP)", id, client, workspace, req.connectionId,
                    req.mediaId, ready.getFileSize(), key, fingerprint, req.title, description, req.privacyStatus, req.madeForKids, Boolean.TRUE.equals(req.containsSyntheticMedia));
        } catch (DuplicateKeyException ex) {
            existing = lookupKey(client, key);
            if (existing == null) throw ex;
            return replay(existing, workspace, fingerprint);
        }
        return get(id);
    }

    public Map<String, Object> get(String id) {
        return result(requireTask(access.requireClient(PERMISSION), access.workspaceId(), id, false));
    }

    @Transactional
    public Map<String, Object> retry(String id) {
        Task task = requireTask(access.requireClient(PERMISSION), access.workspaceId(), id, true);
        if ("PENDING".equals(task.status)) return result(task);
        if (!"FAILED".equals(task.status) || !task.retryable || task.attempt >= 3)
            throw TkOpenApiException.conflict("YOUTUBE_RETRY_NOT_ALLOWED", "task cannot be safely retried");
        auth.requireConnection(task.clientId, task.workspaceId, task.connectionId, false);
        if (task.youtubeVideoId == null && task.uploadCipher == null) media.requireReady(task.clientId, task.mediaId, task.workspaceId);
        db.update("UPDATE " + TASKS + " SET status='PENDING',fail_code=NULL,retryable=FALSE,attempt=attempt+1,"
                + "transient_errors=0,poll_count=0,next_retry_time=CURRENT_TIMESTAMP WHERE id=?", task.id);
        return get(id);
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 20000)
    public void tick() {
        if (!access.isEnabled()) return;
        List<Long> due = db.queryForList("SELECT id FROM " + TASKS + " WHERE status='PENDING' AND next_retry_time<=CURRENT_TIMESTAMP"
                + " ORDER BY next_retry_time,id LIMIT 8", Long.class);
        for (Long id : due) {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(state -> process(id));
            } catch (RuntimeException ex) {
                log.warn("YouTube OpenAPI checkpoint transaction failed: row={}, type={}", id, ex.getClass().getSimpleName());
            }
        }
    }

    void process(long id) {
        Task task = query("id=? FOR UPDATE", id);
        if (task == null || !"PENDING".equals(task.status) || task.nextRetryTime == null
                || task.nextRetryTime.isAfter(LocalDateTime.now())) return;
        if (!access.allowed(task.clientId, PERMISSION)) {
            finish(task, "FAILED", "YOUTUBE_PERMISSION_DENIED", true);
            return;
        }
        TkOpenYouTubeAuthService.Connection connection = null;
        TkChannelVideoClient.Checkpoint step;
        try {
            connection = auth.requireConnection(task.clientId, task.workspaceId, task.connectionId, true);
            String token = auth.accessToken(connection);
            if (task.youtubeVideoId != null) {
                step = videos.youtubeStatus(task.youtubeVideoId, TkChannelOAuthClient.bearer(token), task.uploadOffset);
            } else step = advance(task, token);
        } catch (TkOpenApiException ex) {
            finish(task, "FAILED", ex.getCode(), "YOUTUBE_CONNECTION_DISABLED".equals(ex.getCode()));
            return;
        } catch (TkSocialPlatformException ex) {
            if (ex.isReauthRequired() && connection != null) auth.expire(connection);
            platformFailure(task, ex);
            return;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            finish(task, "UNKNOWN", "YOUTUBE_CHECKPOINT_INVALID", false);
            return;
        }
        String encrypted = step.getUploadUrl() == null ? null : task.uploadCipher == null
                ? cipher.encrypt(step.getUploadUrl(), uploadContext(task)) : task.uploadCipher;
        task.youtubeVideoId = step.getPostId();
        task.platformStatus = step.getStage();
        task.uploadOffset = step.getOffset();
        int polls = task.pollCount + ("YT_PROCESSING".equals(step.getStage()) ? 1 : 0);
        db.update("UPDATE " + TASKS + " SET youtube_video_id=?,platform_status=?,upload_offset=?,upload_cipher=?,"
                        + "poll_count=?,transient_errors=0,next_retry_time=? WHERE id=?", task.youtubeVideoId, task.platformStatus,
                task.uploadOffset, encrypted, polls, LocalDateTime.now().plusSeconds(Math.max(2, step.getDelay())), task.id);
        if ("PUBLISHED".equals(step.getStage())) finish(task, "SUCCESS", null, false);
        else if (polls >= 120) finish(task, "FAILED", "YOUTUBE_PROCESSING_TIMEOUT", true);
    }

    private TkChannelVideoClient.Checkpoint advance(Task task, String token) {
        String session = task.uploadCipher == null ? null : cipher.decrypt(task.uploadCipher, uploadContext(task));
        TkOpenYouTubeMediaDO ready;
        boolean local;
        String source;
        java.nio.file.Path localSource;
        try {
            ready = media.requireReady(task.clientId, task.mediaId, task.workspaceId);
            local = "LOCAL".equals(ready.getUploadMode());
            source = local ? null : media.resolveReadUrl(ready);
            localSource = local ? media.resolveLocalSource(ready) : null;
            if (!local && ready.getObjectEtag() == null)
                throw TkOpenApiException.badRequest("MEDIA_SOURCE_CHANGED", "stored video identity is missing");
        } catch (TkOpenApiException ex) {
            if (session != null) {
                TkChannelVideoClient.Checkpoint resolved = videos.reconcileYouTube(session, token, task.fileSize);
                if (resolved != null) return resolved;
            }
            throw ex;
        }
        TkSocialPublishTaskDO video = new TkSocialPublishTaskDO();
        video.setTitle(task.title);
        video.setChannelText(task.description);
        video.setChannelPrivacy(task.privacyStatus);
        video.setChannelForKids(task.madeForKids);
        video.setChannelSynthetic(task.containsSyntheticMedia);
        TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();
        detail.setPlatform(TkChannelProperties.YOUTUBE);
        return videos.advanceYouTube(video, detail, token, source, localSource, ready.getObjectEtag(), task.fileSize, session);
    }

    void platformFailure(Task task, TkSocialPlatformException ex) {
        if (ex.isUncertain()) {
            finish(task, "UNKNOWN", ex.getCode(), false);
        } else if (ex.isRetryable() && task.transientErrors < 6) {
            db.update("UPDATE " + TASKS + " SET transient_errors=transient_errors+1,next_retry_time=? WHERE id=?",
                    LocalDateTime.now().plusSeconds(15L << task.transientErrors), task.id);
        } else {
            finish(task, "FAILED", ex.getCode(), ex.isRetryable() || ex.isReauthRequired());
        }
    }

    private void finish(Task task, String status, String code, boolean retryable) {
        task.status = status;
        task.failCode = code;
        task.retryable = retryable && task.attempt < 3;
        db.update("UPDATE " + TASKS + " SET status=?,fail_code=?,retryable=?,next_retry_time=NULL WHERE id=?",
                status, code, task.retryable, task.id);
        Map<String, Object> payload = result(task);
        payload.put("platform", "youtube");
        callbacks.enqueueOnce(task.clientId, "SUCCESS".equals(status) ? "publish.completed" : "publish.failed",
                "PUBLISH_TASK", task.taskId, payload, task.attempt);
    }

    private Task lookupKey(String client, String key) { return query("client_id=? AND idempotency_key=?", client, key); }
    private Task requireTask(String client, String workspace, String id, boolean lock) {
        Task task = query("client_id=? AND workspace_id=? AND task_id=?" + (lock ? " FOR UPDATE" : ""), client, workspace, id);
        if (task == null) throw TkOpenApiException.notFound("YOUTUBE_TASK_NOT_FOUND", "publish task does not exist");
        return task;
    }

    private Task query(String where, Object... args) {
        return first(db.query("SELECT * FROM " + TASKS + " WHERE " + where, BeanPropertyRowMapper.newInstance(Task.class), args));
    }

    private static Map<String, Object> replay(Task task, String workspace, String hash) {
        if (!workspace.equals(task.workspaceId) || !hash.equals(task.payloadHash))
            throw TkOpenApiException.conflict("IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key is already in use");
        return result(task);
    }

    private static Map<String, Object> result(Task t) {
        return fields("taskId", t.taskId, "workspaceId", t.workspaceId, "connectionId", t.connectionId, "mediaId", t.mediaId,
                "status", t.status, "platformStatus", t.platformStatus, "privacyStatus", t.privacyStatus,
                "youtubeVideoId", t.youtubeVideoId, "publishUrl", t.youtubeVideoId == null ? null : "https://www.youtube.com/watch?v=" + t.youtubeVideoId,
                "retryable", t.retryable, "retryCount", t.attempt, "errorCode", t.failCode, "uploadedBytes", t.uploadOffset);
    }

    private static boolean containsMarkup(String value) { return value.indexOf('<') >= 0 || value.indexOf('>') >= 0; }
    private static String uploadContext(Task task) { return "open-youtube:upload:" + task.clientId + ":" + task.workspaceId + ":" + task.taskId; }

    @Data public static class Create {
        @NotBlank @Pattern(regexp = "[!-~]{1,128}") private String idempotencyKey;
        @NotBlank @Size(max = 48) private String connectionId;
        @NotBlank @Size(max = 64) private String mediaId;
        @NotBlank @Size(max = 100) private String title;
        @Size(max = 5000) private String description;
        @NotBlank @Pattern(regexp = "private|unlisted|public") private String privacyStatus;
        @NotNull private Boolean madeForKids;
        private Boolean containsSyntheticMedia;
    }

    @Data public static class Task {
        private Long id;
        private String taskId, clientId, workspaceId, connectionId, mediaId, payloadHash, title, description,
                privacyStatus, uploadCipher, youtubeVideoId, platformStatus, status, failCode;
        private boolean madeForKids, containsSyntheticMedia, retryable;
        private int attempt, transientErrors, pollCount;
        private long uploadOffset, fileSize;
        private LocalDateTime nextRetryTime;
    }
}
