package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishAttemptMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Authoritative detail outcome, attempt evidence, task aggregate and callback outbox transaction. */
@Service
public class TkOpenTiktokPublishTerminalService {
    private static final String LEGACY_HEURISTIC_FAILURE = "TikTok publish was not found after reconciliation";
    private final TkOpenTiktokPublishDetailMapper detailMapper;
    private final TkOpenTiktokPublishTaskMapper taskMapper;
    private final TkOpenApiCallbackService callbackService;
    private final TkOpenTiktokPublishAttemptMapper attemptMapper;
    @Resource
    private TkOpenTiktokMediaService mediaService;

    public TkOpenTiktokPublishTerminalService(TkOpenTiktokPublishDetailMapper detailMapper,
                                              TkOpenTiktokPublishTaskMapper taskMapper,
                                              TkOpenApiCallbackService callbackService,
                                              TkOpenTiktokPublishAttemptMapper attemptMapper) {
        this.detailMapper = detailMapper;
        this.taskMapper = taskMapper;
        this.callbackService = callbackService;
        this.attemptMapper = attemptMapper;
    }

    /**
     * Invoke through the injected Spring bean. True includes an idempotent confirmation of the same outcome;
     * false means missing/stale state, unsupported evidence, or a contradictory terminal outcome.
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean confirm(TkOpenTiktokPublishDetailDO expected, String platformStatus, String postId,
                           String reason, String evidenceSource) {
        if (!"STATUS_API".equals(evidenceSource) && !"WEBHOOK".equals(evidenceSource)) return false;
        return confirmInternal(expected, platformStatus, postId, reason, evidenceSource, null);
    }

    /** The caller must classify INIT_REJECTED from a known structured rejection code, never exception text. */
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmLocal(TkOpenTiktokPublishDetailDO expected, String ownerToken,
                                String reason, String evidenceSource) {
        if (StrUtil.isBlank(ownerToken) || !isLocalSource(evidenceSource)) return false;
        return confirmInternal(expected, "FAILED", null, reason, evidenceSource, ownerToken);
    }

    private boolean confirmInternal(TkOpenTiktokPublishDetailDO expected, String platformStatus, String postId,
                                    String reason, String evidenceSource, String ownerToken) {
        String outcome = "PUBLISH_COMPLETE".equals(platformStatus) ? "SUCCESS"
                : "FAILED".equals(platformStatus) ? "FAILED" : null;
        if (outcome == null || !validSnapshot(expected) || !validEvidence(expected, outcome, evidenceSource)) {
            return false;
        }
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskIdForUpdate(
                expected.getClientId(), expected.getTaskId());
        if (task == null) return false;
        TkOpenTiktokPublishDetailDO detail = lockDetail(expected);
        if (!sameAttempt(expected, detail)) return false;
        boolean wasTerminal = isTerminal(detail.getStatus());
        if (wasTerminal && !outcome.equals(detail.getStatus())) return false;
        if (!wasTerminal && !"PENDING".equals(detail.getStatus()) && !"PROCESSING".equals(detail.getStatus())) {
            return false;
        }
        TkOpenTiktokPublishAttemptDO attempt = lockAttempt(detail);
        if (attempt != null && !compatibleAttempt(detail, attempt)) return false;
        if (isLocalSource(evidenceSource) && !validLocalAttempt(attempt, ownerToken, evidenceSource)) return false;

        LocalDateTime now = LocalDateTime.now();
        String failReason = wasTerminal ? detail.getFailReason() : "FAILED".equals(outcome)
                ? StrUtil.maxLength(StrUtil.blankToDefault(reason,
                    StrUtil.blankToDefault(detail.getFailReason(), "TikTok rejected the publish")), 1000) : null;
        LambdaUpdateWrapper<TkOpenTiktokPublishDetailDO> update = detailUpdateGuard(detail)
                .set(TkOpenTiktokPublishDetailDO::getStatus, outcome)
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus,
                        wasTerminal ? detail.getTiktokStatus() : platformStatus)
                .set(TkOpenTiktokPublishDetailDO::getFailReason, failReason)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, now);
        if ("SUCCESS".equals(outcome)) {
            boolean changedPostId = StrUtil.isNotBlank(postId) && !Objects.equals(postId, detail.getPublicPostId());
            if (changedPostId) {
                detail.setPublicPostId(postId);
                update.set(TkOpenTiktokPublishDetailDO::getPublicPostId, postId);
            }
            // Preserve already collected metrics when duplicate evidence arrives.
            if (!wasTerminal || changedPostId) {
                detail.setMetricsStatus(StrUtil.isBlank(detail.getPublicPostId()) ? "WAITING_PUBLIC" : "SYNCING");
                update.set(TkOpenTiktokPublishDetailDO::getMetricsStatus, detail.getMetricsStatus())
                        .set(TkOpenTiktokPublishDetailDO::getMetricsFailReason, null);
            }
        }
        if (detailMapper.update(null, update) != 1) return false;
        detail.setStatus(outcome).setTiktokStatus(wasTerminal ? detail.getTiktokStatus() : platformStatus)
                .setFailReason(failReason).setLastSyncTime(now);
        recordEvidence(detail, attempt, evidenceSource, now);
        aggregateAndEnqueue(detail, task);
        return true;
    }

    /** Repair only verified legacy terminals; never infer authority or rewrite existing outbox history. */
    @Transactional(rollbackFor = Exception.class)
    public boolean repairCallback(TkOpenTiktokPublishDetailDO expected) {
        if (!validSnapshot(expected)) return false;
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskIdForUpdate(
                expected.getClientId(), expected.getTaskId());
        if (task == null) return false;
        TkOpenTiktokPublishDetailDO detail = lockDetail(expected);
        if (!sameAttempt(expected, detail) || !isTerminal(detail.getStatus())
                || !Objects.equals(expected.getStatus(), detail.getStatus())
                || LEGACY_HEURISTIC_FAILURE.equals(detail.getFailReason())
                || ("SUCCESS".equals(detail.getStatus()) && StrUtil.isBlank(detail.getPublishId()))) {
            return false;
        }
        TkOpenTiktokPublishAttemptDO attempt = lockAttempt(detail);
        if (!hasRepairEvidence(detail, attempt)) return false;
        aggregateAndEnqueue(detail, task);
        return true;
    }

    private TkOpenTiktokPublishDetailDO lockDetail(TkOpenTiktokPublishDetailDO expected) {
        return detailMapper.selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getClientId, expected.getClientId())
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, expected.getTaskId())
                .eq(TkOpenTiktokPublishDetailDO::getDetailId, expected.getDetailId())
                .last("FOR UPDATE"));
    }

    private TkOpenTiktokPublishAttemptDO lockAttempt(TkOpenTiktokPublishDetailDO detail) {
        // Stage/heartbeat/upload ownership updates can lock only the attempt, independently of the detail.
        return attemptMapper.selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishAttemptDO>()
                .eq(TkOpenTiktokPublishAttemptDO::getClientId, detail.getClientId())
                .eq(TkOpenTiktokPublishAttemptDO::getTaskId, detail.getTaskId())
                .eq(TkOpenTiktokPublishAttemptDO::getDetailId, detail.getDetailId())
                .eq(TkOpenTiktokPublishAttemptDO::getAttemptNo, attemptNo(detail))
                .last("FOR UPDATE"));
    }

    private boolean validLocalAttempt(TkOpenTiktokPublishAttemptDO attempt, String ownerToken, String source) {
        if (attempt == null || !Objects.equals(ownerToken, attempt.getOwnerToken())
                || StrUtil.isNotBlank(attempt.getPublishId())) return false;
        // An already terminal phase is deliberately not accepted as evidence of a pre-init failure.
        return "PRE_INIT_FAILURE".equals(source)
                ? "MATERIAL_PREPARATION".equals(attempt.getPhase()) || "READY_TO_INIT".equals(attempt.getPhase())
                : "INIT_REJECTED".equals(source) && "INIT_SENT".equals(attempt.getPhase());
    }

    private boolean hasRepairEvidence(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishAttemptDO attempt) {
        if (attempt == null) {
            // Legacy failure writers collapsed local exceptions and heuristic guesses into FAILED.
            return "SUCCESS".equals(detail.getStatus()) && "PUBLISH_COMPLETE".equals(detail.getTiktokStatus())
                    && StrUtil.isNotBlank(detail.getPublishId());
        }
        // Phase alone cannot prove an outcome: require the committed source/time and matching detail status.
        return compatibleAttempt(detail, attempt) && "CONFIRMED_TERMINAL".equals(attempt.getPhase())
                && attempt.getTerminalTime() != null
                && validEvidence(detail, detail.getStatus(), attempt.getTerminalSource())
                && ("SUCCESS".equals(detail.getStatus()) ? "PUBLISH_COMPLETE" : "FAILED")
                    .equals(detail.getTiktokStatus());
    }

    private LambdaUpdateWrapper<TkOpenTiktokPublishDetailDO> detailUpdateGuard(TkOpenTiktokPublishDetailDO detail) {
        return Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getClientId, detail.getClientId())
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, detail.getTaskId())
                .eq(TkOpenTiktokPublishDetailDO::getDetailId, detail.getDetailId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, detail.getStatus())
                .isNull(detail.getRetryCount() == null, TkOpenTiktokPublishDetailDO::getRetryCount)
                .eq(detail.getRetryCount() != null, TkOpenTiktokPublishDetailDO::getRetryCount, detail.getRetryCount())
                .isNull(detail.getPublishId() == null, TkOpenTiktokPublishDetailDO::getPublishId)
                .eq(detail.getPublishId() != null, TkOpenTiktokPublishDetailDO::getPublishId, detail.getPublishId());
    }

    private void recordEvidence(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishAttemptDO attempt,
                                String source, LocalDateTime now) {
        // Legacy rows have no attempt. Keep the first authoritative evidence on duplicate confirmation.
        if (attempt == null || ("CONFIRMED_TERMINAL".equals(attempt.getPhase())
                && StrUtil.isNotBlank(attempt.getTerminalSource()) && attempt.getTerminalTime() != null)) return;
        int changed = attemptMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishAttemptDO.class)
                .eq(TkOpenTiktokPublishAttemptDO::getId, attempt.getId())
                .eq(TkOpenTiktokPublishAttemptDO::getClientId, detail.getClientId())
                .eq(TkOpenTiktokPublishAttemptDO::getTaskId, detail.getTaskId())
                .eq(TkOpenTiktokPublishAttemptDO::getDetailId, detail.getDetailId())
                .eq(TkOpenTiktokPublishAttemptDO::getAttemptNo, attemptNo(detail))
                .and(ids -> ids.isNull(TkOpenTiktokPublishAttemptDO::getPublishId)
                        .or().eq(TkOpenTiktokPublishAttemptDO::getPublishId, "")
                        .or(StrUtil.isNotBlank(detail.getPublishId()))
                        .eq(StrUtil.isNotBlank(detail.getPublishId()),
                                TkOpenTiktokPublishAttemptDO::getPublishId, detail.getPublishId()))
                .set(TkOpenTiktokPublishAttemptDO::getPhase, "CONFIRMED_TERMINAL")
                .set(TkOpenTiktokPublishAttemptDO::getTerminalSource, source)
                .set(TkOpenTiktokPublishAttemptDO::getTerminalTime, now)
                .set(TkOpenTiktokPublishAttemptDO::getLastError, detail.getFailReason())
                .set(TkOpenTiktokPublishAttemptDO::getNextReconcileTime, null));
        if (changed != 1) throw new IllegalStateException("Publish attempt changed during terminal confirmation");
    }

    private void aggregateAndEnqueue(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishTaskDO task) {
        // A locking read sees current sibling outcomes even with a REPEATABLE READ snapshot.
        // The task row already serializes sibling transitions, so sorting is unnecessary here.
        // Keep ORDER BY out: the deployed SQL parser renders it after FOR UPDATE (invalid MySQL).
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectList(
                new LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                        .eq(TkOpenTiktokPublishDetailDO::getClientId, detail.getClientId())
                        .eq(TkOpenTiktokPublishDetailDO::getTaskId, detail.getTaskId())
                        .last("FOR UPDATE"));
        int success = (int) details.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        int failed = (int) details.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int pending = details.size() - success - failed;
        String status = pending > 0 ? "PROCESSING" : failed == 0 ? "SUCCESS"
                : success > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        String failReason = details.stream().filter(item -> "FAILED".equals(item.getStatus()))
                .map(TkOpenTiktokPublishDetailDO::getFailReason).filter(StrUtil::isNotBlank).findFirst().orElse(null);
        int changed = taskMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                .eq(TkOpenTiktokPublishTaskDO::getId, task.getId())
                .eq(TkOpenTiktokPublishTaskDO::getClientId, detail.getClientId())
                .eq(TkOpenTiktokPublishTaskDO::getTaskId, detail.getTaskId())
                .set(TkOpenTiktokPublishTaskDO::getSuccessCount, success)
                .set(TkOpenTiktokPublishTaskDO::getFailedCount, failed)
                .set(TkOpenTiktokPublishTaskDO::getPendingCount, pending)
                .set(TkOpenTiktokPublishTaskDO::getStatus, status)
                .set(TkOpenTiktokPublishTaskDO::getScheduleStatus,
                        task.getScheduledAt() == null ? task.getScheduleStatus() : "COMPLETED")
                .set(TkOpenTiktokPublishTaskDO::getFailReason, failReason));
        if (changed != 1) throw new IllegalStateException("Publish task disappeared during terminal confirmation");
        callbackService.enqueueOnce(detail.getClientId(), "SUCCESS".equals(detail.getStatus())
                        ? "publish.success" : "publish.failed", "PUBLISH_DETAIL", detail.getDetailId(),
                buildPublishEventPayload(detail, task), attemptNo(detail));
        cleanupScheduledMediaAfterCommit(detail, task);
    }

    private void cleanupScheduledMediaAfterCommit(TkOpenTiktokPublishDetailDO detail,
                                                  TkOpenTiktokPublishTaskDO task) {
        if (mediaService == null || task == null || task.getScheduledAt() == null) return;
        Runnable cleanup = () -> {
            try {
                mediaService.cleanupScheduledPublishMedia(detail.getClientId(), task.getMediaId());
            } catch (Exception ex) {
                // Terminal persistence is authoritative; media service records cleanup failure separately.
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.run(); }
            });
        } else {
            cleanup.run();
        }
    }

    // Exact existing TkOpenTiktokPublishService callback shape. Keep the public contract unchanged.
    private Map<String, Object> buildPublishEventPayload(TkOpenTiktokPublishDetailDO detail,
                                                        TkOpenTiktokPublishTaskDO task) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId()); payload.put("detailId", detail.getDetailId());
        payload.put("connectionId", detail.getConnectionId()); payload.put("externalRequestId", task.getExternalRequestId());
        payload.put("status", detail.getStatus()); payload.put("publishId", detail.getPublishId());
        payload.put("publicPostId", detail.getPublicPostId());
        payload.put("publishUrl", detail.getPublishUrl()); payload.put("failReason", detail.getFailReason());
        return payload;
    }

    private boolean validSnapshot(TkOpenTiktokPublishDetailDO expected) {
        return expected != null && StrUtil.isNotBlank(expected.getClientId())
                && StrUtil.isNotBlank(expected.getTaskId()) && StrUtil.isNotBlank(expected.getDetailId());
    }

    private boolean validEvidence(TkOpenTiktokPublishDetailDO expected, String outcome, String source) {
        if ("STATUS_API".equals(source) || "WEBHOOK".equals(source)) return StrUtil.isNotBlank(expected.getPublishId());
        return isLocalSource(source)
                && "FAILED".equals(outcome) && StrUtil.isBlank(expected.getPublishId());
    }

    private boolean isLocalSource(String source) {
        return "PRE_INIT_FAILURE".equals(source) || "INIT_REJECTED".equals(source);
    }

    private boolean sameAttempt(TkOpenTiktokPublishDetailDO expected, TkOpenTiktokPublishDetailDO current) {
        return current != null && Objects.equals(expected.getClientId(), current.getClientId())
                && Objects.equals(expected.getTaskId(), current.getTaskId())
                && Objects.equals(expected.getDetailId(), current.getDetailId())
                && attemptNo(expected) == attemptNo(current)
                && Objects.equals(expected.getPublishId(), current.getPublishId());
    }

    private boolean compatibleAttempt(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishAttemptDO attempt) {
        return Objects.equals(attempt.getClientId(), detail.getClientId())
                && Objects.equals(attempt.getTaskId(), detail.getTaskId())
                && Objects.equals(attempt.getDetailId(), detail.getDetailId())
                && Objects.equals(attempt.getAttemptNo(), attemptNo(detail))
                && (attempt.getPublishId() == null || attempt.getPublishId().isEmpty()
                    || Objects.equals(attempt.getPublishId(), detail.getPublishId()));
    }

    private int attemptNo(TkOpenTiktokPublishDetailDO detail) {
        return detail.getRetryCount() == null ? 0 : detail.getRetryCount();
    }

    private boolean isTerminal(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status);
    }
}
