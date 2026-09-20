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
        if (current == null || !"PENDING".equals(current.getStatus()) || !sameAttempt(current, expected)) return null;
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

    public void error(TkOpenTiktokPublishAttemptDO attempt, String reason) {
        if (attempt == null) return;
        attempts.update(null, owned(attempt).ne(TkOpenTiktokPublishAttemptDO::getPhase, "CONFIRMED_TERMINAL")
                .set(TkOpenTiktokPublishAttemptDO::getLastError, StrUtil.maxLength(reason, 1000))
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, LocalDateTime.now().plusMinutes(2)));
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
