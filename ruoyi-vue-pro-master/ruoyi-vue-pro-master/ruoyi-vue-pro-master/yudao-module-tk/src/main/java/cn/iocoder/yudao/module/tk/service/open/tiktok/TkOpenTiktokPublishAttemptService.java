package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Durable ownership of one execution, independent of status-query timestamps. */
@Service @TenantIgnore
public class TkOpenTiktokPublishAttemptService {
    public enum ExpiredUploadDecision {
        REQUEUED,
        RETRY_EXHAUSTED,
        STALE
    }

    private final TkOpenTiktokPublishAttemptMapper attempts;
    private final TkOpenTiktokPublishDetailMapper details;
    private final TkOpenTiktokPublishTaskMapper tasks;

    public TkOpenTiktokPublishAttemptService(TkOpenTiktokPublishAttemptMapper attempts,
            TkOpenTiktokPublishDetailMapper details, TkOpenTiktokPublishTaskMapper tasks) {
        this.attempts = attempts; this.details = details; this.tasks = tasks;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishAttemptDO claim(TkOpenTiktokPublishDetailDO expected) {
        TkOpenTiktokPublishDetailDO current = lock(expected);
        if (current == null || !sameAttempt(current, expected)) return null;
        if (!"PENDING".equals(current.getStatus())) return null;
        // A queued row with an official publish ID is recoverable work.  It must
        // never enter the initialization lane and create a second TikTok post.
        if (StrUtil.isNotBlank(current.getPublishId())) {
            requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                    .eq(TkOpenTiktokPublishDetailDO::getId, current.getId())
                    .eq(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                    .set(TkOpenTiktokPublishDetailDO::getFailReason,
                            "Official TikTok publish ID exists; initialization is blocked")
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now())));
            tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getClientId, current.getClientId())
                    .eq(TkOpenTiktokPublishTaskDO::getTaskId, current.getTaskId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "PENDING")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING"));
            return null;
        }
        TkOpenTiktokPublishAttemptDO attempt = current(current);
        if (attempt != null && !canRestart(attempt.getPhase())) return null;
        LocalDateTime now = LocalDateTime.now();
        if (attempt == null) {
            attempt = TkOpenTiktokPublishAttemptDO.builder().clientId(current.getClientId())
                    .taskId(current.getTaskId()).detailId(current.getDetailId()).attemptNo(number(current.getRetryCount()))
                    .startedTime(now).reconcileCount(0).build();
        }
        attempt.setOwnerToken(UUID.randomUUID().toString()).setPhase("MATERIAL_PREPARATION").setHeartbeatTime(now);
        if (attempt.getId() == null) attempts.insert(attempt); else attempts.updateById(attempt);
        requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, current.getId())
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "LOCAL_PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, now)));
        // lock(expected) holds the task row; publish the running state atomically with the claim.
        tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                .eq(TkOpenTiktokPublishTaskDO::getClientId, current.getClientId())
                .eq(TkOpenTiktokPublishTaskDO::getTaskId, current.getTaskId())
                .eq(TkOpenTiktokPublishTaskDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING"));
        return attempt;
    }

    public TkOpenTiktokPublishAttemptDO current(TkOpenTiktokPublishDetailDO detail) {
        return attempts.selectCurrent(detail.getDetailId(), number(detail.getRetryCount()));
    }

    public void heartbeat(TkOpenTiktokPublishAttemptDO attempt) {
        requireWrite(attempts.update(null, owned(attempt)
                .ne(TkOpenTiktokPublishAttemptDO::getPhase, "CONFIRMED_TERMINAL")
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now())));
    }

    /**
     * Persist a recovery marker from a separate transaction after a worker loses
     * its lease.  The old owner is fenced before the detail becomes eligible for
     * the recovery/status lanes.
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean recoverExpired(TkOpenTiktokPublishDetailDO expected, LocalDateTime deadline, String reason) {
        TkOpenTiktokPublishTaskDO task = tasks.selectByClientAndTaskIdForUpdate(expected.getClientId(), expected.getTaskId());
        if (task == null) return false;
        TkOpenTiktokPublishDetailDO detail = details.selectByClientAndDetailIdForUpdate(
                expected.getClientId(), expected.getTaskId(), expected.getDetailId());
        if (detail == null || !sameAttempt(detail, expected)) return false;
        boolean publishedPending = "PENDING".equals(detail.getStatus()) && StrUtil.isNotBlank(detail.getPublishId());
        if (!publishedPending && !"PROCESSING".equals(detail.getStatus())) return false;
        TkOpenTiktokPublishAttemptDO attempt = current(detail);
        if (!publishedPending && attempt != null && active(attempt, deadline)) return false;
        // No attempt row means no remote side effect was recorded.  Return the
        // detail to the durable pending queue immediately so it cannot occupy a
        // processing slot forever.
        if (attempt == null && StrUtil.isBlank(detail.getPublishId())) {
            requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                    .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                    .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RESTART_PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getFailReason, StrUtil.maxLength(reason, 1000))
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now())));
            tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getClientId, detail.getClientId())
                    .eq(TkOpenTiktokPublishTaskDO::getTaskId, detail.getTaskId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PENDING"));
            return true;
        }
        if (attempt != null) {
            String fencedOwner = UUID.randomUUID().toString();
            LambdaUpdateWrapper<TkOpenTiktokPublishAttemptDO> fence = owned(attempt)
                    .set(TkOpenTiktokPublishAttemptDO::getOwnerToken, fencedOwner)
                    .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, deadline)
                    .set(TkOpenTiktokPublishAttemptDO::getLastError, StrUtil.maxLength(reason, 1000));
            if (StrUtil.isNotBlank(detail.getPublishId())) {
                fence.set(TkOpenTiktokPublishAttemptDO::getPhase,
                        "UPLOAD_URL_EXPIRED".equals(attempt.getPhase()) ? "UPLOAD_URL_EXPIRED" : "RECOVERY_REQUIRED");
            }
            if (attempts.update(null, fence) != 1) return false;
            attempt.setOwnerToken(fencedOwner).setHeartbeatTime(deadline);
            if (StrUtil.isNotBlank(detail.getPublishId()) && !"UPLOAD_URL_EXPIRED".equals(attempt.getPhase()))
                attempt.setPhase("RECOVERY_REQUIRED");
        }
        requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, detail.getStatus())
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, StrUtil.maxLength(reason, 1000))
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now())));
        if ("PENDING".equals(detail.getStatus())) {
            tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getClientId, detail.getClientId())
                    .eq(TkOpenTiktokPublishTaskDO::getTaskId, detail.getTaskId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "PENDING")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING"));
        }
        return true;
    }

    /** Called by the heartbeat lane when a renewal write failed. */
    @Transactional(rollbackFor = Exception.class)
    public boolean recoverHeartbeatFailure(TkOpenTiktokPublishAttemptDO attempt, String reason) {
        if (attempt == null) return false;
        TkOpenTiktokPublishDetailDO detail = details.selectByClientAndDetailId(
                attempt.getClientId(), attempt.getDetailId());
        return detail != null && recoverExpired(detail,
                LocalDateTime.now().minusMinutes(3), reason);
    }

    public void stage(TkOpenTiktokPublishAttemptDO attempt, String phase) {
        requireWrite(attempts.update(null, owned(attempt)
                .eq(TkOpenTiktokPublishAttemptDO::getPhase, attempt.getPhase())
                .set(TkOpenTiktokPublishAttemptDO::getPhase, phase)
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now())));
        attempt.setPhase(phase);
    }

    /** Atomically fences the lease immediately before the first remote publish side effect. */
    public void beginRemoteInit(TkOpenTiktokPublishAttemptDO attempt, LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();
        requireWrite(attempts.update(null, owned(attempt)
                .eq(TkOpenTiktokPublishAttemptDO::getPhase, "READY_TO_INIT")
                .gt(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, deadline)
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "INIT_SENT")
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, now)));
        attempt.setPhase("INIT_SENT").setHeartbeatTime(now);
    }

    /** Called before init: a persisted fenced phase is required before any remote side effect. */
    public void ready(TkOpenTiktokPublishAttemptDO attempt, String source, Long size, String sha256) {
        requireWrite(attempts.update(null, owned(attempt)
                .eq(TkOpenTiktokPublishAttemptDO::getPhase, "MATERIAL_PREPARATION")
                .set(TkOpenTiktokPublishAttemptDO::getUploadSource, source)
                .set(TkOpenTiktokPublishAttemptDO::getFileSize, size)
                .set(TkOpenTiktokPublishAttemptDO::getFileSha256, sha256)
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "READY_TO_INIT")
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now())));
        attempt.setUploadSource(source).setFileSize(size).setFileSha256(sha256).setPhase("READY_TO_INIT");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveResponse(TkOpenTiktokPublishDetailDO expected, TkOpenTiktokPublishAttemptDO attempt,
                             String publishId, String uploadUrlCipher) {
        if (StrUtil.isBlank(publishId)) throw new IllegalArgumentException("Official publish ID is missing");
        TkOpenTiktokPublishDetailDO current = lock(expected);
        if (current == null || !sameAttempt(current, expected)) throw new IllegalStateException("Execution changed");
        if (StrUtil.isNotBlank(current.getPublishId()) && !publishId.equals(current.getPublishId()))
            throw new IllegalStateException("Conflicting official publish ID");
        TkOpenTiktokPublishAttemptDO stored = current(current);
        if (stored == null || !Objects.equals(stored.getOwnerToken(), attempt.getOwnerToken()))
            throw new IllegalStateException("Execution ownership changed");
        // A webhook can already have confirmed this exact ID. Never regress its phase.
        boolean terminal = "SUCCESS".equals(current.getStatus()) || "FAILED".equals(current.getStatus());
        requireWrite(attempts.update(null, owned(attempt)
                .set(TkOpenTiktokPublishAttemptDO::getPublishId, publishId)
                .set(TkOpenTiktokPublishAttemptDO::getUploadUrlCipher, uploadUrlCipher)
                .set(!terminal, TkOpenTiktokPublishAttemptDO::getPhase, "PUBLISH_ID_SAVED")
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now())));
        if (!terminal) requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, current.getId())
                .set(TkOpenTiktokPublishDetailDO::getPublishId, publishId)
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "UPLOAD_PENDING")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now())));
        attempt.setPublishId(publishId).setUploadUrlCipher(uploadUrlCipher)
                .setPhase(terminal ? "CONFIRMED_TERMINAL" : "PUBLISH_ID_SAVED");
    }

    /** Return a prepared but unsent attempt to the database when the upload lane is full. */
    @Transactional(rollbackFor = Exception.class)
    public void deferUnsent(TkOpenTiktokPublishDetailDO expected, TkOpenTiktokPublishAttemptDO attempt) {
        TkOpenTiktokPublishDetailDO detail = lock(expected);
        if (detail == null || !sameAttempt(detail, expected) || !"PROCESSING".equals(detail.getStatus())
                || StrUtil.isNotBlank(detail.getPublishId())) return;
        // Only an owned, definitely unsent attempt may give its slot back. Fence its old worker.
        requireWrite(attempts.update(null, owned(attempt)
                .in(TkOpenTiktokPublishAttemptDO::getPhase, "MATERIAL_PREPARATION", "READY_TO_INIT")
                .set(TkOpenTiktokPublishAttemptDO::getOwnerToken, UUID.randomUUID().toString())
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now())));
        requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "LOCAL_PENDING")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)));
        if (details.selectListByClientAndTaskId(detail.getClientId(), detail.getTaskId()).stream()
                .noneMatch(item -> "PROCESSING".equals(item.getStatus()))) {
            tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getClientId, detail.getClientId())
                    .eq(TkOpenTiktokPublishTaskDO::getTaskId, detail.getTaskId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PENDING"));
        }
    }

    /** Fences a dead worker before making definitely unsent work runnable again. */
    @Transactional(rollbackFor = Exception.class)
    public boolean recoverUnsent(TkOpenTiktokPublishDetailDO expected, LocalDateTime deadline) {
        TkOpenTiktokPublishDetailDO detail = lock(expected);
        if (detail == null || !sameAttempt(detail, expected) || !"PROCESSING".equals(detail.getStatus())
                || StrUtil.isNotBlank(detail.getPublishId())) return false;
        TkOpenTiktokPublishAttemptDO attempt = current(detail);
        if (attempt == null || !canRestart(attempt.getPhase()) || active(attempt, deadline)) return false;
        int fenced = attempts.update(null, owned(attempt)
                .le(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, deadline)
                .in(TkOpenTiktokPublishAttemptDO::getPhase, "MATERIAL_PREPARATION", "READY_TO_INIT")
                .set(TkOpenTiktokPublishAttemptDO::getOwnerToken, UUID.randomUUID().toString()));
        if (fenced == 0) return false;
        requireWrite(details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RESTART_PENDING")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)));
        return true;
    }

    /** Claim only the existing upload session; this never initializes a new publish. */
    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishAttemptDO claimUpload(TkOpenTiktokPublishDetailDO expected, LocalDateTime deadline) {
        TkOpenTiktokPublishDetailDO detail = lock(expected);
        if (detail == null || !sameAttempt(detail, expected) || !"PROCESSING".equals(detail.getStatus())) return null;
        TkOpenTiktokPublishAttemptDO attempt = current(detail);
        if (attempt == null || active(attempt, deadline) || StrUtil.isBlank(attempt.getUploadUrlCipher())
                || !Objects.equals(detail.getPublishId(), attempt.getPublishId())) return null;
        String owner = UUID.randomUUID().toString();
        int changed = attempts.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishAttemptDO.class)
                .eq(TkOpenTiktokPublishAttemptDO::getId, attempt.getId())
                .le(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, deadline)
                .in(TkOpenTiktokPublishAttemptDO::getPhase, "PUBLISH_ID_SAVED", "UPLOADING", "RECOVERY_REQUIRED")
                .set(TkOpenTiktokPublishAttemptDO::getOwnerToken, owner)
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "UPLOADING")
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now()));
        return changed == 0 ? null : attempt.setOwnerToken(owner).setPhase("UPLOADING");
    }

    /** Keep expiry durable even if the following authoritative status query is unavailable. */
    public boolean markUploadExpired(TkOpenTiktokPublishAttemptDO attempt, String reason) {
        if (attempt == null) return false;
        int changed = attempts.update(null, owned(attempt)
                .in(TkOpenTiktokPublishAttemptDO::getPhase, "UPLOADING", "UPLOAD_URL_EXPIRED")
                .eq(TkOpenTiktokPublishAttemptDO::getUploadSource, "FILE_UPLOAD")
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "UPLOAD_URL_EXPIRED")
                .set(TkOpenTiktokPublishAttemptDO::getLastError, StrUtil.maxLength(reason, 1000))
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, LocalDateTime.now().plusSeconds(30)));
        if (changed == 1) attempt.setPhase("UPLOAD_URL_EXPIRED").setLastError(StrUtil.maxLength(reason, 1000));
        return changed == 1;
    }

    /**
     * Fence an expired upload session and return the detail to the initialization queue.
     * The old publish ID is retained only on the historical attempt row; the next
     * retry generation must initialize a fresh TikTok upload session.
     */
    @Transactional(rollbackFor = Exception.class)
    public ExpiredUploadDecision requeueExpiredUpload(TkOpenTiktokPublishDetailDO expected,
                                                       TkOpenTiktokPublishAttemptDO expectedAttempt,
                                                       long uploadedBytes, String reason, int maxRetries) {
        if (expected == null || expectedAttempt == null || uploadedBytes < 0 || maxRetries < 0) {
            return ExpiredUploadDecision.STALE;
        }
        TkOpenTiktokPublishTaskDO task = tasks.selectByClientAndTaskIdForUpdate(
                expected.getClientId(), expected.getTaskId());
        if (task == null) return ExpiredUploadDecision.STALE;
        TkOpenTiktokPublishDetailDO detail = details.selectByClientAndDetailIdForUpdate(
                expected.getClientId(), expected.getTaskId(), expected.getDetailId());
        if (detail == null || !sameAttempt(detail, expected) || !"PROCESSING".equals(detail.getStatus())
                || StrUtil.isBlank(detail.getPublishId())
                || !Objects.equals(detail.getPublishId(), expectedAttempt.getPublishId())) {
            return ExpiredUploadDecision.STALE;
        }
        TkOpenTiktokPublishAttemptDO current = attempts.selectCurrentForUpdate(
                detail.getDetailId(), number(detail.getRetryCount()));
        if (current == null || !Objects.equals(current.getId(), expectedAttempt.getId())
                || !Objects.equals(current.getOwnerToken(), expectedAttempt.getOwnerToken())
                || !Objects.equals(current.getPhase(), expectedAttempt.getPhase())
                || !Objects.equals(current.getPublishId(), detail.getPublishId())
                || !"UPLOAD_URL_EXPIRED".equals(current.getPhase())
                || !"FILE_UPLOAD".equals(current.getUploadSource())
                || !StrUtil.containsIgnoreCase(current.getLastError(), "HTTP 403") || current.getFileSize() == null
                || uploadedBytes >= current.getFileSize()) {
            return ExpiredUploadDecision.STALE;
        }
        int retryCount = number(detail.getRetryCount());
        if (retryCount >= maxRetries) return ExpiredUploadDecision.RETRY_EXHAUSTED;

        String fencedOwner = UUID.randomUUID().toString();
        String failure = StrUtil.maxLength(StrUtil.blankToDefault(reason,
                "TikTok upload URL expired before all bytes were uploaded"), 1000);
        int fenced = attempts.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishAttemptDO.class)
                .eq(TkOpenTiktokPublishAttemptDO::getId, current.getId())
                .eq(TkOpenTiktokPublishAttemptDO::getOwnerToken, current.getOwnerToken())
                .eq(TkOpenTiktokPublishAttemptDO::getAttemptNo, retryCount)
                .set(TkOpenTiktokPublishAttemptDO::getOwnerToken, fencedOwner)
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "URL_EXPIRED_RETRY")
                .set(TkOpenTiktokPublishAttemptDO::getLastError, failure)
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, null)
                .set(TkOpenTiktokPublishAttemptDO::getHeartbeatTime, LocalDateTime.now()));
        if (fenced != 1) return ExpiredUploadDecision.STALE;

        int changed = details.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .eq(TkOpenTiktokPublishDetailDO::getPublishId, detail.getPublishId())
                .apply("COALESCE(retry_count,0) = {0}", retryCount)
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RETRY_PENDING")
                .set(TkOpenTiktokPublishDetailDO::getPublishId, null)
                .set(TkOpenTiktokPublishDetailDO::getPublicPostId, null)
                .set(TkOpenTiktokPublishDetailDO::getPublishUrl, null)
                .set(TkOpenTiktokPublishDetailDO::getMetricsStatus, "WAITING_PUBLISH")
                .set(TkOpenTiktokPublishDetailDO::getViewCount, null)
                .set(TkOpenTiktokPublishDetailDO::getLikeCount, null)
                .set(TkOpenTiktokPublishDetailDO::getCommentCount, null)
                .set(TkOpenTiktokPublishDetailDO::getShareCount, null)
                .set(TkOpenTiktokPublishDetailDO::getFailReason, failure)
                .set(TkOpenTiktokPublishDetailDO::getRetryCount, retryCount + 1)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        requireWrite(changed);

        current.setOwnerToken(fencedOwner).setPhase("URL_EXPIRED_RETRY")
                .setLastError(failure).setHeartbeatTime(LocalDateTime.now());
        detail.setStatus("PENDING").setTiktokStatus("RETRY_PENDING")
                .setPublishId(null).setPublicPostId(null).setPublishUrl(null)
                .setMetricsStatus("WAITING_PUBLISH").setViewCount(null).setLikeCount(null)
                .setCommentCount(null).setShareCount(null).setFailReason(failure)
                .setRetryCount(retryCount + 1).setLastSyncTime(LocalDateTime.now());
        if (details.selectListByClientAndTaskId(detail.getClientId(), detail.getTaskId()).stream()
                .noneMatch(item -> "PROCESSING".equals(item.getStatus()))) {
            tasks.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getClientId, detail.getClientId())
                    .eq(TkOpenTiktokPublishTaskDO::getTaskId, detail.getTaskId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PENDING"));
        }
        return ExpiredUploadDecision.REQUEUED;
    }

    public void error(TkOpenTiktokPublishAttemptDO attempt, String reason) {
        if (attempt == null) return;
        int reconcileCount = number(attempt.getReconcileCount()) + 1;
        long[] delays = {30L, 60L, 120L, 300L, 600L};
        long delay = delays[Math.min(reconcileCount - 1, delays.length - 1)];
        LocalDateTime next = LocalDateTime.now().plusSeconds(delay);
        int changed = attempts.update(null, owned(attempt)
                .notIn(TkOpenTiktokPublishAttemptDO::getPhase, "CONFIRMED_TERMINAL", "URL_EXPIRED_RETRY")
                .set(!"UPLOAD_URL_EXPIRED".equals(attempt.getPhase()),
                        TkOpenTiktokPublishAttemptDO::getLastError, StrUtil.maxLength(reason, 1000))
                .set(TkOpenTiktokPublishAttemptDO::getReconcileCount, reconcileCount)
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, next));
        if (changed == 1) attempt.setReconcileCount(reconcileCount).setNextReconcileTime(next)
                .setLastError("UPLOAD_URL_EXPIRED".equals(attempt.getPhase())
                        ? attempt.getLastError() : StrUtil.maxLength(reason, 1000));
    }

    public void clearReconcileBackoff(TkOpenTiktokPublishAttemptDO attempt) {
        if (attempt == null || attempt.getId() == null) return;
        int changed = attempts.update(null, owned(attempt)
                .notIn(TkOpenTiktokPublishAttemptDO::getPhase,
                        "CONFIRMED_TERMINAL", "URL_EXPIRED_RETRY", "UPLOAD_URL_EXPIRED")
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, null)
                .set(TkOpenTiktokPublishAttemptDO::getReconcileCount, 0)
                .set(TkOpenTiktokPublishAttemptDO::getLastError, null));
        if (changed == 1) attempt.setNextReconcileTime(null).setReconcileCount(0).setLastError(null);
    }

    static boolean canRestart(String phase) {
        return "MATERIAL_PREPARATION".equals(phase) || "READY_TO_INIT".equals(phase);
    }

    static boolean active(TkOpenTiktokPublishAttemptDO attempt, LocalDateTime deadline) {
        return attempt != null && attempt.getHeartbeatTime() != null && attempt.getHeartbeatTime().isAfter(deadline);
    }
    private TkOpenTiktokPublishDetailDO lock(TkOpenTiktokPublishDetailDO expected) {
        if (tasks.selectByClientAndTaskIdForUpdate(expected.getClientId(), expected.getTaskId()) == null) return null;
        return details.selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getId, expected.getId()).last("FOR UPDATE"));
    }
    private boolean sameAttempt(TkOpenTiktokPublishDetailDO a, TkOpenTiktokPublishDetailDO b) {
        return number(a.getRetryCount()) == number(b.getRetryCount());
    }
    private LambdaUpdateWrapper<TkOpenTiktokPublishAttemptDO> owned(TkOpenTiktokPublishAttemptDO attempt) {
        return Wrappers.lambdaUpdate(TkOpenTiktokPublishAttemptDO.class).eq(TkOpenTiktokPublishAttemptDO::getId, attempt.getId())
                .eq(TkOpenTiktokPublishAttemptDO::getOwnerToken, attempt.getOwnerToken());
    }
    private int number(Integer value) { return value == null ? 0 : value; }
    private void requireWrite(int rows) { if (rows != 1) throw new IllegalStateException("Publish execution lease or record changed"); }
}
