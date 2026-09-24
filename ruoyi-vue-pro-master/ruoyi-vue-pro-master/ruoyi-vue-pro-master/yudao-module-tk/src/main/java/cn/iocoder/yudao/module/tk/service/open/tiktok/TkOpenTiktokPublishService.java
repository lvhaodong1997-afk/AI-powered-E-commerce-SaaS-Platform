package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.*;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokApiClient;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Resource;
import java.security.MessageDigest;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TkOpenTiktokPublishService {

    private static final int PENDING_RECOVERY_DELAY_MINUTES = 1;
    private static final int INITIALIZATION_LEASE_MINUTES = 15;
    private static final int STATUS_STALE_MINUTES = 2;
    private static final int WORKER_LEASE_MINUTES = 3;

    private final TkOpenTiktokPublishTaskMapper taskMapper;
    private final TkOpenTiktokPublishDetailMapper detailMapper;
    private final TkOpenTiktokMediaMapper mediaMapper;
    private final TkOpenTiktokConnectionMapper connectionMapper;
    private final TkOpenApiIdempotencyMapper idempotencyMapper;
    private final TkOpenPublishPlatformRegistry platformRegistry;
    private final TkOpenApiCallbackService callbackService;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkLocalUploadStorageService localStorageService;
    private final TkOpenTiktokMediaService mediaService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService heartbeats = Executors.newScheduledThreadPool(1);
    @Resource private TkOpenTiktokPublishAttemptService attemptService;
    @Resource private TkOpenTiktokPublishTerminalService terminalService;
    @Resource private TkOpenTiktokPublishResponseJournal responseJournal;
    private volatile boolean stopping;

    public TkOpenTiktokPublishService(TkOpenTiktokPublishTaskMapper taskMapper,
                                      TkOpenTiktokPublishDetailMapper detailMapper,
                                      TkOpenTiktokMediaMapper mediaMapper,
                                      TkOpenTiktokConnectionMapper connectionMapper,
                                      TkOpenApiIdempotencyMapper idempotencyMapper,
                                      TkOpenPublishPlatformRegistry platformRegistry,
                                      TkOpenApiCallbackService callbackService,
                                      TkOpenApiSecretCipher secretCipher,
                                      TkLocalUploadStorageService localStorageService) {
        this(taskMapper, detailMapper, mediaMapper, connectionMapper, idempotencyMapper, platformRegistry,
                callbackService, secretCipher, localStorageService, null);
    }

    @Autowired
    public TkOpenTiktokPublishService(TkOpenTiktokPublishTaskMapper taskMapper,
                                      TkOpenTiktokPublishDetailMapper detailMapper,
                                      TkOpenTiktokMediaMapper mediaMapper,
                                      TkOpenTiktokConnectionMapper connectionMapper,
                                      TkOpenApiIdempotencyMapper idempotencyMapper,
                                      TkOpenPublishPlatformRegistry platformRegistry,
                                      TkOpenApiCallbackService callbackService,
                                      TkOpenApiSecretCipher secretCipher,
                                      TkLocalUploadStorageService localStorageService,
                                      TkOpenTiktokMediaService mediaService) {
        this.taskMapper = taskMapper;
        this.detailMapper = detailMapper;
        this.mediaMapper = mediaMapper;
        this.connectionMapper = connectionMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.platformRegistry = platformRegistry;
        this.callbackService = callbackService;
        this.secretCipher = secretCipher;
        this.localStorageService = localStorageService;
        this.mediaService = mediaService;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishVO.TaskResp create(TkOpenTiktokPublishVO.TaskCreateReq request, String idempotencyKey) {
        return createInternal(request, idempotencyKey, requestHash(request));
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishVO.TaskResp createQuick(TkOpenTiktokPublishVO.QuickTaskCreateReq request,
                                                      String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        String clientId = currentClient();
        String hash = requestHash(request);
        TkOpenApiIdempotencyDO existing = idempotencyMapper.selectByClientAndKey(clientId, idempotencyKey);
        if (existing != null && (existing.getExpireTime() == null || existing.getExpireTime().isAfter(LocalDateTime.now()))) {
            return resolveIdempotentResult(clientId, hash, existing, false);
        }
        if (existing != null) {
            idempotencyMapper.deleteExpired(clientId, idempotencyKey, LocalDateTime.now());
        }
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndExternalAccountId(
                clientId, request.getExternalAccountId());
        if (connection == null) {
            throw TkOpenApiException.notFound("CONNECTION_NOT_FOUND", "connection does not exist");
        }
        if (!"AUTHORIZED".equals(connection.getAuthStatus())) {
            throw TkOpenApiException.badRequest("CONNECTION_NOT_AUTHORIZED", "connection is not authorized");
        }
        if (mediaService == null) {
            throw TkOpenApiException.unavailable("MEDIA_SERVICE_UNAVAILABLE", "remote media service is unavailable");
        }
        TkOpenTiktokMediaDO media = mediaService.createRemote(request.getVideoUrl(), request.getFileName(),
                request.getContentType(), request.getCoverTimestampMs());
        TkOpenTiktokPublishVO.TaskCreateReq taskRequest = new TkOpenTiktokPublishVO.TaskCreateReq();
        taskRequest.setConnectionIds(Collections.singletonList(connection.getConnectionId()));
        taskRequest.setMediaId(media.getMediaId());
        taskRequest.setTitle(request.getTitle());
        taskRequest.setCaption(request.getCaption());
        taskRequest.setPostMode(StrUtil.blankToDefault(request.getPostMode(), "DIRECT_POST"));
        taskRequest.setPrivacyLevel(StrUtil.blankToDefault(request.getPrivacyLevel(), "PUBLIC_TO_EVERYONE"));
        taskRequest.setAllowComment(request.getAllowComment());
        taskRequest.setAllowDuet(request.getAllowDuet());
        taskRequest.setAllowStitch(request.getAllowStitch());
        taskRequest.setCommercialContent(request.getCommercialContent());
        taskRequest.setBrandContent(request.getBrandContent());
        taskRequest.setAigcContent(request.getAigcContent());
        taskRequest.setExternalRequestId(request.getExternalRequestId());
        taskRequest.setScheduledAt(request.getScheduledAt());
        return createInternal(taskRequest, idempotencyKey, hash);
    }

    private TkOpenTiktokPublishVO.TaskResp createInternal(TkOpenTiktokPublishVO.TaskCreateReq request,
                                                           String idempotencyKey, String hash) {
        validateIdempotencyKey(idempotencyKey);
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        LocalDateTime now = LocalDateTime.now();
        TkOpenApiIdempotencyDO existing = idempotencyMapper.selectByClientAndKey(clientId, idempotencyKey);
        if (existing != null) {
            if (existing.getExpireTime() == null || existing.getExpireTime().isAfter(now)) {
                return resolveIdempotentResult(clientId, hash, existing, false);
            }
            idempotencyMapper.deleteExpired(clientId, idempotencyKey, now);
        }
        LocalDateTime scheduledAt = parseScheduledAt(request.getScheduledAt());
        int requestedAccountCount = request.getConnectionIds() == null ? 0
                : new LinkedHashSet<>(request.getConnectionIds()).size();
        validateSchedule(request, scheduledAt, requestedAccountCount);
        TkOpenTiktokMediaDO media = mediaMapper.selectByClientAndMediaId(clientId, request.getMediaId());
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "media does not exist");
        if (!"READY".equals(media.getStatus())) throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "media is not ready");
        LinkedHashSet<String> requestedIds = new LinkedHashSet<>(request.getConnectionIds());
        List<TkOpenTiktokConnectionDO> connections = connectionMapper.selectListByClientAndIds(clientId, requestedIds);
        if (connections.size() != requestedIds.size()) {
            throw TkOpenApiException.notFound("CONNECTION_NOT_FOUND", "one or more connections do not exist");
        }
        for (TkOpenTiktokConnectionDO connection : connections) {
            if (!"AUTHORIZED".equals(connection.getAuthStatus()))
                throw TkOpenApiException.badRequest("CONNECTION_NOT_AUTHORIZED", "connection is not authorized");
        }
        boolean scheduled = scheduledAt != null;
        if (scheduled) {
            if (mediaService == null) {
                throw TkOpenApiException.unavailable("MEDIA_SERVICE_UNAVAILABLE", "scheduled media service is unavailable");
            }
            mediaService.prepareForScheduledPublish(media);
        }
        String taskId = TkOpenApiIds.next("task");
        TkOpenApiIdempotencyDO record = TkOpenApiIdempotencyDO.builder()
                .clientId(clientId).idempotencyKey(idempotencyKey).requestHash(hash)
                .resourceType("PUBLISH_TASK").resourceId(taskId).status("PROCESSING")
                .expireTime(now.plusDays(7)).build();
        try {
            idempotencyMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            TkOpenApiIdempotencyDO winner = idempotencyMapper.selectByClientAndKeyForUpdate(clientId, idempotencyKey);
            if (winner != null) return resolveIdempotentResult(clientId, hash, winner, true);
            throw TkOpenApiException.unavailable("IDEMPOTENCY_RESULT_UNAVAILABLE",
                    "idempotent result is unavailable");
        }
        TkOpenTiktokPublishTaskDO task = TkOpenTiktokPublishTaskDO.builder()
                .taskId(taskId)
                .clientId(clientId)
                .mediaId(media.getMediaId())
                .externalRequestId(request.getExternalRequestId())
                .title(request.getTitle())
                .caption(request.getCaption())
                .postMode(request.getPostMode())
                .privacyLevel(request.getPrivacyLevel())
                .allowComment(defaultBool(request.getAllowComment(), true))
                .allowDuet(defaultBool(request.getAllowDuet(), false))
                .allowStitch(defaultBool(request.getAllowStitch(), false))
                .commercialContent(defaultBool(request.getCommercialContent(), false))
                .brandContent(defaultBool(request.getBrandContent(), false))
                .aigcContent(defaultBool(request.getAigcContent(), true))
                .accountCount(connections.size()).successCount(0).failedCount(0).pendingCount(connections.size())
                .status(scheduled ? "SCHEDULED" : "PENDING")
                .scheduledAt(scheduledAt)
                .scheduleStatus(scheduled ? "SCHEDULED" : null)
                .scheduleVersion(0)
                .build();
        taskMapper.insert(task);
        for (TkOpenTiktokConnectionDO connection : connections) {
            detailMapper.insert(TkOpenTiktokPublishDetailDO.builder()
                    .detailId(TkOpenApiIds.next("detail"))
                    .taskId(task.getTaskId()).clientId(clientId).connectionId(connection.getConnectionId())
                    .accountName(StrUtil.blankToDefault(connection.getDisplayName(), connection.getUsername()))
                    .status(scheduled ? "SCHEDULED" : "PENDING")
                    .tiktokStatus(scheduled ? "SCHEDULED" : "LOCAL_PENDING").metricsStatus("WAITING_PUBLISH")
                    .retryCount(0).build());
        }
        record.setStatus("COMPLETED");
        idempotencyMapper.updateById(record);
        if (!scheduled) submitAfterCommit(clientId, task.getTaskId());
        return toTaskResp(task);
    }

    public TkOpenTiktokPublishVO.TaskResp getTask(String taskId) {
        return toTaskResp(requireTask(currentClient(), taskId));
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishVO.TaskResp reschedule(String taskId, String scheduledAt, String idempotencyKey) {
        validateOptionalIdempotencyKey(idempotencyKey);
        String clientId = currentClient();
        LocalDateTime nextTime = parseScheduledAt(scheduledAt);
        if (nextTime == null || !nextTime.isAfter(LocalDateTime.now())) {
            throw TkOpenApiException.badRequest("SCHEDULE_TIME_INVALID", "scheduledAt is required");
        }
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskIdForUpdate(clientId, taskId);
        if (task == null) throw TkOpenApiException.notFound("PUBLISH_TASK_NOT_FOUND", "publish task does not exist");
        if (!"SCHEDULED".equals(task.getStatus()) || task.getScheduledAt() == null) {
            throw TkOpenApiException.badRequest("SCHEDULE_NOT_MUTABLE", "only scheduled tasks can be rescheduled before execution");
        }
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskIdForUpdate(clientId, taskId);
        if (details.isEmpty() || details.stream().anyMatch(detail -> !"SCHEDULED".equals(detail.getStatus()))) {
            throw TkOpenApiException.conflict("SCHEDULE_ALREADY_RUNNING", "scheduled task execution has already started");
        }
        int version = task.getScheduleVersion() == null ? 0 : task.getScheduleVersion();
        int changed = taskMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                .eq(TkOpenTiktokPublishTaskDO::getId, task.getId())
                .eq(TkOpenTiktokPublishTaskDO::getStatus, "SCHEDULED")
                .set(TkOpenTiktokPublishTaskDO::getScheduledAt, nextTime)
                .set(TkOpenTiktokPublishTaskDO::getScheduleStatus, "SCHEDULED")
                .set(TkOpenTiktokPublishTaskDO::getScheduleVersion, version + 1));
        if (changed != 1) throw TkOpenApiException.conflict("SCHEDULE_ALREADY_RUNNING", "scheduled task execution has already started");
        task.setScheduledAt(nextTime).setScheduleStatus("SCHEDULED").setScheduleVersion(version + 1);
        return toTaskResp(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokPublishVO.TaskResp cancel(String taskId) {
        String clientId = currentClient();
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskIdForUpdate(clientId, taskId);
        if (task == null) throw TkOpenApiException.notFound("PUBLISH_TASK_NOT_FOUND", "publish task does not exist");
        if (!"SCHEDULED".equals(task.getStatus()) || task.getScheduledAt() == null) {
            throw TkOpenApiException.badRequest("SCHEDULE_NOT_MUTABLE", "only scheduled tasks can be cancelled before execution");
        }
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskIdForUpdate(clientId, taskId);
        if (details.isEmpty() || details.stream().anyMatch(detail -> !"SCHEDULED".equals(detail.getStatus()))) {
            throw TkOpenApiException.conflict("SCHEDULE_ALREADY_RUNNING", "scheduled task execution has already started");
        }
        LocalDateTime now = LocalDateTime.now();
        int changedDetails = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, taskId)
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "SCHEDULED")
                .set(TkOpenTiktokPublishDetailDO::getStatus, "CANCELLED")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "CANCELLED")
                .set(TkOpenTiktokPublishDetailDO::getMetricsStatus, "UNAVAILABLE")
                .set(TkOpenTiktokPublishDetailDO::getMetricsFailReason, "publish task was cancelled before execution")
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, now));
        if (changedDetails != details.size()) {
            throw TkOpenApiException.conflict("SCHEDULE_ALREADY_RUNNING", "scheduled task execution has already started");
        }
        int version = task.getScheduleVersion() == null ? 0 : task.getScheduleVersion();
        int changedTask = taskMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                .eq(TkOpenTiktokPublishTaskDO::getId, task.getId())
                .eq(TkOpenTiktokPublishTaskDO::getStatus, "SCHEDULED")
                .set(TkOpenTiktokPublishTaskDO::getStatus, "CANCELLED")
                .set(TkOpenTiktokPublishTaskDO::getScheduleStatus, "CANCELLED")
                .set(TkOpenTiktokPublishTaskDO::getScheduleVersion, version + 1)
                .set(TkOpenTiktokPublishTaskDO::getPendingCount, 0)
                .set(TkOpenTiktokPublishTaskDO::getFailReason, (String) null));
        if (changedTask != 1) throw TkOpenApiException.conflict("SCHEDULE_ALREADY_RUNNING", "scheduled task execution has already started");
        task.setStatus("CANCELLED").setScheduleStatus("CANCELLED").setScheduleVersion(version + 1).setPendingCount(0);
        for (TkOpenTiktokPublishDetailDO detail : details) {
            detail.setStatus("CANCELLED").setTiktokStatus("CANCELLED").setMetricsStatus("UNAVAILABLE")
                    .setMetricsFailReason("publish task was cancelled before execution").setLastSyncTime(now);
            if (callbackService != null) publishEvent(detail, task, "publish.cancelled");
        }
        cleanupScheduledMediaAfterCommit(clientId, task.getMediaId());
        return toTaskResp(task);
    }

    public List<TkOpenTiktokPublishVO.DetailResp> getDetails(String taskId) {
        String clientId = currentClient();
        requireTask(clientId, taskId);
        return detailMapper.selectListByClientAndTaskId(clientId, taskId).stream()
                .map(this::toDetailResp).collect(Collectors.toList());
    }

    public TkOpenTiktokPublishVO.MetricsResp getMetrics(String taskId) {
        String clientId = currentClient();
        requireTask(clientId, taskId);
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskId(clientId, taskId);
        if (details.isEmpty()) {
            throw TkOpenApiException.notFound("PUBLISH_DETAIL_NOT_FOUND", "publish detail does not exist");
        }
        if (details.size() > 1) {
            throw TkOpenApiException.badRequest("PUBLISH_METRICS_MULTI_DETAIL_UNSUPPORTED",
                    "metrics query supports one published TikTok account per task");
        }
        TkOpenTiktokPublishDetailDO detail = details.get(0);
        if (StrUtil.isBlank(detail.getPublicPostId()) && StrUtil.isNotBlank(detail.getPublishId())) {
            syncDetail(detail);
            detail = detailMapper.selectByClientAndDetailId(clientId, detail.getDetailId());
        }
        if (detail == null) {
            throw TkOpenApiException.notFound("PUBLISH_DETAIL_NOT_FOUND", "publish detail does not exist");
        }
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(
                clientId, detail.getConnectionId());
        if (connection == null) {
            return toMetricsResp(detail, "FAILED", "TikTok connection does not exist");
        }
        if (!"AUTHORIZED".equals(connection.getAuthStatus())) {
            return toMetricsResp(detail, "REAUTH_REQUIRED",
                    StrUtil.blankToDefault(connection.getFailReason(), "TikTok authorization is required"));
        }
        if (StrUtil.isBlank(detail.getPublicPostId())) {
            return toMetricsResp(detail, metricsStateBeforePublicPost(detail), detail.getMetricsFailReason());
        }

        try {
            TkOpenPublishPlatformAdapter adapter = platform();
            String token = validAccessToken(connection, adapter, false);
            TkOpenPublishPlatformAdapter.VideoMetricsResult result = adapter.queryVideoMetrics(
                    token, detail.getPublicPostId());
            if (result.isAccessTokenInvalid()) {
                token = validAccessToken(connection, adapter, true);
                result = adapter.queryVideoMetrics(token, detail.getPublicPostId());
            }
            if (result.isAccessTokenInvalid()) {
                return persistMetricsFailure(detail, "REAUTH_REQUIRED", "TikTok access token is invalid");
            }
            if (!result.isSuccess()) {
                return persistMetricsFailure(detail, "UNAVAILABLE",
                        StrUtil.blankToDefault(result.getFailReason(), "TikTok video metrics are unavailable"));
            }
            return persistMetrics(detail, result);
        } catch (Exception ex) {
            String reason = StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "TikTok video metrics query failed"), 1000);
            String status = "AUTHORIZED".equals(connection.getAuthStatus()) ? "FAILED" : "REAUTH_REQUIRED";
            return persistMetricsFailure(detail, status, reason);
        }
    }

    public TkOpenTiktokPublishVO.MetricsBatchResp getMetricsBatch(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty() || taskIds.size() > 50) {
            throw TkOpenApiException.badRequest("PUBLISH_METRICS_BATCH_INVALID",
                    "taskIds must contain between 1 and 50 items");
        }
        String clientId = currentClient();
        LinkedHashSet<String> requestedTaskIds = taskIds.stream()
                .filter(StrUtil::isNotBlank).collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedTaskIds.isEmpty()) {
            throw TkOpenApiException.badRequest("PUBLISH_METRICS_BATCH_INVALID",
                    "taskIds must contain at least one non-blank item");
        }

        Map<String, List<TkOpenTiktokPublishDetailDO>> detailsByTask = new LinkedHashMap<>();
        Map<String, List<TkOpenTiktokPublishVO.MetricsResp>> immediateByTask = new LinkedHashMap<>();
        Map<String, TkOpenTiktokPublishVO.MetricsResp> responseByDetail = new HashMap<>();
        Map<String, List<TkOpenTiktokPublishDetailDO>> detailsByConnection = new LinkedHashMap<>();

        for (String taskId : requestedTaskIds) {
            TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(clientId, taskId);
            if (task == null) {
                immediateByTask.put(taskId, Collections.singletonList(batchFailure(taskId, "NOT_FOUND",
                        "publish task does not exist")));
                continue;
            }
            List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskId(clientId, taskId);
            if (details.isEmpty()) {
                immediateByTask.put(taskId, Collections.singletonList(batchFailure(taskId, "NOT_FOUND",
                        "publish detail does not exist")));
                continue;
            }
            List<TkOpenTiktokPublishDetailDO> resolvedDetails = new ArrayList<>();
            for (TkOpenTiktokPublishDetailDO detail : details) {
                if (StrUtil.isBlank(detail.getPublicPostId()) && StrUtil.isNotBlank(detail.getPublishId())) {
                    syncDetail(detail);
                    detail = detailMapper.selectByClientAndDetailId(clientId, detail.getDetailId());
                }
                if (detail == null) {
                    continue;
                }
                resolvedDetails.add(detail);
                if (StrUtil.isBlank(detail.getPublicPostId())) {
                    responseByDetail.put(detail.getDetailId(),
                            toMetricsResp(detail, metricsStateBeforePublicPost(detail), detail.getMetricsFailReason()));
                } else {
                    detailsByConnection.computeIfAbsent(detail.getConnectionId(), key -> new ArrayList<>()).add(detail);
                }
            }
            detailsByTask.put(taskId, resolvedDetails);
        }

        for (Map.Entry<String, List<TkOpenTiktokPublishDetailDO>> entry : detailsByConnection.entrySet()) {
            String connectionId = entry.getKey();
            List<TkOpenTiktokPublishDetailDO> details = entry.getValue();
            TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(clientId, connectionId);
            if (connection == null) {
                for (TkOpenTiktokPublishDetailDO detail : details) {
                    responseByDetail.put(detail.getDetailId(), toMetricsResp(detail, "FAILED",
                            "TikTok connection does not exist"));
                }
                continue;
            }
            if (!"AUTHORIZED".equals(connection.getAuthStatus())) {
                for (TkOpenTiktokPublishDetailDO detail : details) {
                    responseByDetail.put(detail.getDetailId(), toMetricsResp(detail, "REAUTH_REQUIRED",
                            StrUtil.blankToDefault(connection.getFailReason(), "TikTok authorization is required")));
                }
                continue;
            }
            try {
                TkOpenPublishPlatformAdapter adapter = platform();
                String token = validAccessToken(connection, adapter, false);
                for (int offset = 0; offset < details.size(); offset += 20) {
                    List<TkOpenTiktokPublishDetailDO> chunk = details.subList(offset,
                            Math.min(offset + 20, details.size()));
                    List<String> publicPostIds = chunk.stream().map(TkOpenTiktokPublishDetailDO::getPublicPostId)
                            .collect(Collectors.toList());
                    Map<String, TkOpenPublishPlatformAdapter.VideoMetricsResult> metrics =
                            adapter.queryVideoMetrics(token, publicPostIds);
                    if (containsInvalidAccessToken(metrics)) {
                        token = validAccessToken(connection, adapter, true);
                        metrics = adapter.queryVideoMetrics(token, publicPostIds);
                    }
                    for (TkOpenTiktokPublishDetailDO detail : chunk) {
                        TkOpenPublishPlatformAdapter.VideoMetricsResult result = metrics.get(detail.getPublicPostId());
                        if (result == null) {
                            responseByDetail.put(detail.getDetailId(), persistMetricsFailure(detail, "UNAVAILABLE",
                                    "TikTok video metrics are unavailable"));
                        } else if (result.isAccessTokenInvalid()) {
                            responseByDetail.put(detail.getDetailId(), persistMetricsFailure(detail,
                                    "REAUTH_REQUIRED", "TikTok access token is invalid"));
                        } else if (!result.isSuccess()) {
                            responseByDetail.put(detail.getDetailId(), persistMetricsFailure(detail, "UNAVAILABLE",
                                    StrUtil.blankToDefault(result.getFailReason(), "TikTok video metrics are unavailable")));
                        } else {
                            responseByDetail.put(detail.getDetailId(), persistMetrics(detail, result));
                        }
                    }
                }
            } catch (Exception ex) {
                String reason = StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(),
                        "TikTok video metrics query failed"), 1000);
                String status = "AUTHORIZED".equals(connection.getAuthStatus()) ? "FAILED" : "REAUTH_REQUIRED";
                for (TkOpenTiktokPublishDetailDO detail : details) {
                    responseByDetail.put(detail.getDetailId(), persistMetricsFailure(detail, status, reason));
                }
            }
        }

        List<TkOpenTiktokPublishVO.MetricsResp> items = new ArrayList<>();
        for (String taskId : requestedTaskIds) {
            List<TkOpenTiktokPublishVO.MetricsResp> immediate = immediateByTask.get(taskId);
            if (immediate != null) {
                items.addAll(immediate);
                continue;
            }
            for (TkOpenTiktokPublishDetailDO detail : detailsByTask.getOrDefault(taskId, Collections.emptyList())) {
                TkOpenTiktokPublishVO.MetricsResp response = responseByDetail.get(detail.getDetailId());
                if (response != null) {
                    items.add(response);
                }
            }
        }
        TkOpenTiktokPublishVO.MetricsBatchResp response = new TkOpenTiktokPublishVO.MetricsBatchResp();
        response.setRequestedCount(requestedTaskIds.size());
        response.setResultCount(items.size());
        response.setItems(items);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void retry(String detailId) {
        String clientId = currentClient();
        TkOpenTiktokPublishDetailDO detail = detailMapper.selectByClientAndDetailId(clientId, detailId);
        if (detail == null) throw TkOpenApiException.notFound("PUBLISH_DETAIL_NOT_FOUND", "publish detail does not exist");
        if (!"FAILED".equals(detail.getStatus()))
            throw TkOpenApiException.badRequest("PUBLISH_RETRY_STATUS_INVALID", "publish detail cannot be retried");
        taskMapper.selectByClientAndTaskIdForUpdate(clientId, detail.getTaskId());
        int changed = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "FAILED")
                .apply("COALESCE(retry_count,0) = {0}", defaultInt(detail.getRetryCount()))
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
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                .set(TkOpenTiktokPublishDetailDO::getRetryCount, defaultInt(detail.getRetryCount()) + 1));
        if (changed != 1) throw TkOpenApiException.conflict("PUBLISH_RETRY_STATUS_INVALID", "publish execution changed");
        refreshSummary(clientId, detail.getTaskId());
        submitAfterCommit(clientId, detail.getTaskId());
    }

    public int syncStale(int limit) {
        int count = 0;
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime initializationDeadline = now.minusMinutes(INITIALIZATION_LEASE_MINUTES);
        for (TkOpenTiktokPublishDetailDO detail
                : detailMapper.selectStaleInitializing(initializationDeadline, boundedLimit)) {
            if (recoverInterruptedInitialization(detail, initializationDeadline)) {
                count++;
            }
        }
        int remaining = boundedLimit - count;
        if (remaining <= 0) {
            return count;
        }
        for (TkOpenTiktokPublishDetailDO detail
                : detailMapper.selectStaleProcessing(now.minusMinutes(STATUS_STALE_MINUTES), remaining)) {
            syncDetail(detail);
            count++;
        }
        return count;
    }

    public int reconcileTerminalCallbacks(int limit) {
        int count = 0;
        for (TkOpenTiktokPublishDetailDO detail : detailMapper.selectTerminalForCallback(Math.max(1, Math.min(limit, 200)))) {
            try {
                if (terminalService.repairCallback(detail)) count++;
            } catch (Exception ex) {
                log.warn("[reconcileTerminalCallbacks][detailId({}) will retry]", detail.getDetailId(), ex);
            }
        }
        return count;
    }

    public int reconcileRecoveryRequired(int limit) {
        int count = 0;
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        for (TkOpenTiktokPublishDetailDO detail : detailMapper.selectRecoveryRequired(boundedLimit)) {
            if (reconcileRecoveryDetail(detail)) {
                count++;
            }
        }
        return count;
    }

    public int resumeStalePending(int limit) {
        List<TkOpenTiktokPublishDetailDO> pending = detailMapper.selectStalePending(
                LocalDateTime.now().minusMinutes(PENDING_RECOVERY_DELAY_MINUTES), limit);
        Set<String> submittedTasks = new HashSet<>();
        for (TkOpenTiktokPublishDetailDO detail : pending) {
            String key = detail.getClientId() + "\n" + detail.getTaskId();
            if (submittedTasks.add(key)) {
                submitTask(detail.getClientId(), detail.getTaskId());
            }
        }
        return submittedTasks.size();
    }

    @Transactional(rollbackFor = Exception.class)
    public int dispatchDueScheduled(int limit) {
        List<TkOpenTiktokPublishTaskDO> due = taskMapper.selectDueScheduled(LocalDateTime.now(),
                Math.max(1, Math.min(limit, 200)));
        List<String> submitted = new ArrayList<>();
        for (TkOpenTiktokPublishTaskDO candidate : due) {
            TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskIdForUpdate(
                    candidate.getClientId(), candidate.getTaskId());
            if (task == null || !"SCHEDULED".equals(task.getStatus()) || task.getScheduledAt() == null
                    || task.getScheduledAt().isAfter(LocalDateTime.now())) continue;
            List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskIdForUpdate(
                    task.getClientId(), task.getTaskId());
            if (details.isEmpty() || details.stream().anyMatch(detail -> !"SCHEDULED".equals(detail.getStatus()))) continue;
            int changedDetails = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                    .eq(TkOpenTiktokPublishDetailDO::getClientId, task.getClientId())
                    .eq(TkOpenTiktokPublishDetailDO::getTaskId, task.getTaskId())
                    .eq(TkOpenTiktokPublishDetailDO::getStatus, "SCHEDULED")
                    .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "LOCAL_PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getFailReason, null));
            if (changedDetails != details.size()) continue;
            int version = task.getScheduleVersion() == null ? 0 : task.getScheduleVersion();
            int changedTask = taskMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishTaskDO.class)
                    .eq(TkOpenTiktokPublishTaskDO::getId, task.getId())
                    .eq(TkOpenTiktokPublishTaskDO::getStatus, "SCHEDULED")
                    .set(TkOpenTiktokPublishTaskDO::getStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishTaskDO::getScheduleStatus, "RUNNING")
                    .set(TkOpenTiktokPublishTaskDO::getScheduleVersion, version + 1));
            if (changedTask != 1) {
                throw new IllegalStateException("scheduled task changed during activation");
            }
            submitted.add(task.getClientId() + "\n" + task.getTaskId());
        }
        Runnable submit = () -> submitted.forEach(value -> {
            String[] parts = value.split("\\n", 2);
            submitTask(parts[0], parts[1]);
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
        return submitted.size();
    }

    void processTask(String clientId, String taskId) {
        try {
            for (TkOpenTiktokPublishDetailDO detail : detailMapper.selectListByClientAndTaskId(clientId, taskId)) {
                if (!stopping && "PENDING".equals(detail.getStatus())) processDetail(detail);
            }
        } catch (Exception ex) {
            log.error("[processTask][taskId({}) worker failed; durable recovery will resume]", taskId, ex);
        }
    }

    private void processDetail(TkOpenTiktokPublishDetailDO detail) {
        TkOpenTiktokPublishAttemptDO attempt = null;
        UploadSource source = null;
        ScheduledFuture<?> heartbeat = null;
        boolean initSent = false;
        String failureSource = "PRE_INIT_FAILURE";
        try {
            attempt = attemptService.claim(detail);
            if (attempt == null) return;
            final TkOpenTiktokPublishAttemptDO executionAttempt = attempt;
            detail.setStatus("PROCESSING");
            heartbeat = startHeartbeat(attempt);
            TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(detail.getClientId(), detail.getTaskId());
            TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(detail.getClientId(), detail.getConnectionId());
            TkOpenTiktokMediaDO media = task == null ? null : mediaMapper.selectByClientAndMediaId(detail.getClientId(), task.getMediaId());
            if (task == null || connection == null || media == null) throw new IllegalStateException("publish resource no longer exists");
            TkOpenPublishPlatformAdapter adapter = platform();
            String token = validAccessToken(connection, adapter, false);
            TkOpenPublishPlatformAdapter.CreatorCapabilities creator = adapter.queryCreatorInfo(token);
            if (creator.isAccessTokenInvalid()) {
                token = validAccessToken(connection, adapter, true);
                creator = adapter.queryCreatorInfo(token);
            }
            if (!creator.isSuccess()) throw new IllegalStateException(creator.getFailReason());
            source = resolveSource(media, adapter.verifiedPullDomain());
            Map<String, Object> payload = buildPayload(task, media, creator, source);
            attemptService.ready(attempt, source.pullFromUrl ? "PULL_FROM_URL" : "FILE_UPLOAD",
                    source.size, source.pullFromUrl ? null : fileSha256(source.file));
            // This fenced durable transition must succeed before contacting TikTok.
            attemptService.beginRemoteInit(attempt, LocalDateTime.now().minusMinutes(WORKER_LEASE_MINUTES));
            initSent = true;
            TkOpenPublishPlatformAdapter.PublishInitResult initialized = adapter.initVideoPost(token, task.getPostMode(), payload);
            if (initialized.isAccessTokenInvalid()) {
                // An explicit invalid-token rejection has no accepted publish; one refreshed request is safe.
                token = validAccessToken(connection, adapter, true);
                attemptService.heartbeat(attempt);
                initialized = adapter.initVideoPost(token, task.getPostMode(), payload);
            }
            if (!initialized.isSuccess()) {
                if (isDefinitiveInitRejection(initialized.getErrorCode())) {
                    initSent = false;
                    failureSource = "INIT_REJECTED";
                }
                throw new IllegalStateException(StrUtil.blankToDefault(initialized.getFailReason(), "TikTok initialization response was inconclusive"));
            }
            if (StrUtil.isBlank(initialized.getPublishId())) {
                markRecoveryRequired(detail, "Awaiting authoritative TikTok result: initialization returned no publish ID");
                return;
            }
            detail.setPublishId(initialized.getPublishId());
            String encryptedUrl = StrUtil.isBlank(initialized.getUploadUrl()) ? null : secretCipher.encrypt(initialized.getUploadUrl());
            // Journal first, so a process restart during database recovery retains the official response.
            try { responseJournal.save(detail, attempt, initialized.getPublishId(), encryptedUrl); }
            catch (RuntimeException journalFailure) {
                log.warn("[processDetail][detailId({}) response journal unavailable; direct DB persistence is the fallback]",
                        detail.getDetailId(), journalFailure);
            }
            persistResponse(detail, attempt, initialized.getPublishId(), encryptedUrl);
            responseJournal.remove(detail.getDetailId(), defaultInt(detail.getRetryCount()));
            if ("CONFIRMED_TERMINAL".equals(attempt.getPhase())) return;
            if (!source.pullFromUrl) {
                if (StrUtil.isBlank(initialized.getUploadUrl())) throw new IllegalStateException("platform upload URL is missing");
                attemptService.stage(attempt, "UPLOADING");
                adapter.uploadVideo(initialized.getUploadUrl(), source.file, media.getContentType(),
                        () -> attemptService.heartbeat(executionAttempt));
            }
            attemptService.stage(attempt, "PLATFORM_PROCESSING");
            detail.setTiktokStatus("PROCESSING");
            int changed = detailMapper.update(null, processingUpdate(detail)
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "PROCESSING")
                    .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
            connectionMapper.updateById(new TkOpenTiktokConnectionDO().setId(connection.getId()).setLastPublishTime(LocalDateTime.now()));
            if (changed > 0) publishEvent(detail, task, "publish.processing");
        } catch (Exception ex) {
            log.warn("[processDetail][detailId({}) phase({}) publishId({}) requires attention]",
                    detail.getDetailId(), attempt == null ? "CLAIM" : attempt.getPhase(), detail.getPublishId(), ex);
            if (attempt != null) {
                try {
                    attemptService.error(attempt, safeError(ex));
                    if (isUploadUrlExpired(ex) && reconcileExpiredUpload(detail, attempt)) return;
                    if (initSent || StrUtil.isNotBlank(detail.getPublishId())) {
                        markRecoveryRequired(detail, "Awaiting authoritative TikTok result; execution interrupted");
                    } else if (ex instanceof org.springframework.dao.DataAccessException) {
                        // Local DB outages before init are restartable, not proof of a publishing failure.
                        markRecoveryRequired(detail, "Preparing publish; local storage is temporarily unavailable");
                    } else {
                        terminalService.confirmLocal(detail, attempt.getOwnerToken(), "TikTok publish failed: " + safeError(ex), failureSource);
                    }
                } catch (Exception persistenceFailure) {
                    log.error("[processDetail][detailId({}) cannot persist recovery; next scan will retry]", detail.getDetailId(), persistenceFailure);
                }
            }
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            if (source != null) source.cleanup();
        }
    }

    private ScheduledFuture<?> startHeartbeat(TkOpenTiktokPublishAttemptDO attempt) {
        return heartbeats.scheduleAtFixedRate(() -> {
            try { attemptService.heartbeat(attempt); }
            catch (Exception ex) { log.warn("[heartbeat][detailId({}) unable to renew execution]", attempt.getDetailId()); }
        }, 20, 20, TimeUnit.SECONDS);
    }

    private void persistResponse(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishAttemptDO attempt,
                                 String publishId, String uploadUrlCipher) {
        RuntimeException last = null;
        for (int retry = 0; retry < 3; retry++) {
            try { attemptService.saveResponse(detail, attempt, publishId, uploadUrlCipher); return; }
            catch (RuntimeException ex) {
                last = ex;
                if (retry < 2) {
                    try { Thread.sleep(200L * (retry + 1)); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw last;
    }

    private static boolean isDefinitiveInitRejection(String errorCode) {
        return StrUtil.equalsAny(errorCode, "access_token_invalid", "scope_not_authorized", "invalid_param",
                "spam_risk_too_many_posts", "spam_risk_user_banned_from_posting", "reached_active_user_cap",
                "unaudited_client_can_only_post_to_private_accounts", "url_ownership_unverified",
                "privacy_level_option_mismatch", "spam_risk_too_many_pending_share", "rate_limit_exceeded");
    }

    private String safeError(Exception ex) {
        // URLs can contain credentials. Keep the exception type and sanitized text in execution records.
        return StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName())
                .replaceAll("https?://\\S+", "[URL]"), 1000);
    }

    private String fileSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
        return hex.toString();
    }

    private void syncDetail(TkOpenTiktokPublishDetailDO detail) {
        try {
            TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(detail.getClientId(), detail.getConnectionId());
            if (connection == null || StrUtil.isBlank(detail.getPublishId())) return;
            TkOpenPublishPlatformAdapter adapter = platform();
            String token = validAccessToken(connection, adapter, false);
            TkOpenPublishPlatformAdapter.PublishStatusResult status = adapter.fetchPostStatus(token, detail.getPublishId());
            if (status.isAccessTokenInvalid()) status = adapter.fetchPostStatus(validAccessToken(connection, adapter, true), detail.getPublishId());
            if (!status.isSuccess()) throw new IllegalStateException(status.getFailReason());
            String postId = status.getPublicPostIds() == null || status.getPublicPostIds().isEmpty() ? null : status.getPublicPostIds().get(0);
            if (isSuccess(status.getStatus()) || isFailed(status.getStatus())) {
                terminalService.confirm(detail, status.getStatus(), postId, status.getFailReason(), "STATUS_API");
            } else {
                detailMapper.update(null, processingUpdate(detail)
                        .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, status.getStatus())
                        .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                        .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
                if ("PROCESSING_UPLOAD".equals(status.getStatus()) && status.getUploadedBytes() != null) {
                    resumeUpload(detail);
                }
            }
        } catch (Exception ex) {
            log.warn("[syncDetail][detailId({}) status query deferred: {}]", detail.getDetailId(), safeError(ex));
            detailMapper.update(null, processingUpdate(detail)
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        }
    }

    private void resumeUpload(TkOpenTiktokPublishDetailDO detail) {
        TkOpenTiktokPublishAttemptDO attempt = attemptService.claimUpload(detail, LocalDateTime.now().minusMinutes(WORKER_LEASE_MINUTES));
        if (attempt == null) return;
        try {
            executor.execute(() -> {
                UploadSource source = null;
                ScheduledFuture<?> heartbeat = startHeartbeat(attempt);
                try {
                    TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(detail.getClientId(), detail.getTaskId());
                    TkOpenTiktokMediaDO media = task == null ? null : mediaMapper.selectByClientAndMediaId(detail.getClientId(), task.getMediaId());
                    if (media == null || !"FILE_UPLOAD".equals(attempt.getUploadSource())) {
                        throw new IllegalStateException("Original upload material is unavailable for same-session recovery");
                    }
                    source = resolveSource(media, null);
                    if (!Objects.equals(source.size, attempt.getFileSize()) || !Objects.equals(fileSha256(source.file), attempt.getFileSha256()))
                        throw new IllegalStateException("Recovery media differs from the original upload");
                    TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(detail.getClientId(), detail.getConnectionId());
                    TkOpenPublishPlatformAdapter adapter = platform();
                    TkOpenPublishPlatformAdapter.PublishStatusResult current = adapter.fetchPostStatus(validAccessToken(connection, adapter, false), detail.getPublishId());
                    if (current.isSuccess() && "PROCESSING_UPLOAD".equals(current.getStatus()) && current.getUploadedBytes() != null) {
                        attemptService.heartbeat(attempt);
                        adapter.resumeUploadVideo(secretCipher.decrypt(attempt.getUploadUrlCipher()), source.file,
                                media.getContentType(), current.getUploadedBytes(), () -> attemptService.heartbeat(attempt));
                        attemptService.stage(attempt, "PLATFORM_PROCESSING");
                    } else if (current.isSuccess() && (isSuccess(current.getStatus()) || isFailed(current.getStatus()))) {
                        terminalService.confirm(detail, current.getStatus(),
                                current.getPublicPostIds() == null || current.getPublicPostIds().isEmpty() ? null : current.getPublicPostIds().get(0),
                                current.getFailReason(), "STATUS_API");
                    }
                } catch (Exception ex) {
                    try {
                        attemptService.error(attempt, safeError(ex));
                        if (isUploadUrlExpired(ex) && reconcileExpiredUpload(detail, attempt)) return;
                    }
                    catch (Exception ignored) { log.error("[resumeUpload][detailId({}) recovery record unavailable]", detail.getDetailId()); }
                    log.warn("[resumeUpload][detailId({}) same-session upload deferred: {}]", detail.getDetailId(), safeError(ex));
                } finally {
                    heartbeat.cancel(false);
                    if (source != null) source.cleanup();
                }
            });
        } catch (RejectedExecutionException ex) {
            log.info("[resumeUpload][detailId({}) queued for next recovery scan]", detail.getDetailId());
        }
    }

    private boolean reconcileExpiredUpload(TkOpenTiktokPublishDetailDO detail,
                                           TkOpenTiktokPublishAttemptDO attempt) {
        try {
            TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(
                    detail.getClientId(), detail.getConnectionId());
            if (connection == null || StrUtil.isBlank(detail.getPublishId())) return false;
            TkOpenPublishPlatformAdapter adapter = platform();
            TkOpenPublishPlatformAdapter.PublishStatusResult status = adapter.fetchPostStatus(
                    validAccessToken(connection, adapter, false), detail.getPublishId());
            if (status.isAccessTokenInvalid()) {
                status = adapter.fetchPostStatus(validAccessToken(connection, adapter, true), detail.getPublishId());
            }
            if (!status.isSuccess()) return false;
            String postId = status.getPublicPostIds() == null || status.getPublicPostIds().isEmpty()
                    ? null : status.getPublicPostIds().get(0);
            if (isSuccess(status.getStatus()) || isFailed(status.getStatus())) {
                return terminalService.confirm(detail, status.getStatus(), postId,
                        status.getFailReason(), "STATUS_API");
            }
            if ("PROCESSING_UPLOAD".equals(status.getStatus()) && status.getUploadedBytes() != null) {
                String reason = StrUtil.format(
                        "TikTok publish failed: upload URL expired after {} of {} bytes",
                        status.getUploadedBytes(), attempt.getFileSize());
                return terminalService.confirmUploadExpired(
                        detail, attempt.getOwnerToken(), status.getUploadedBytes(), reason);
            }
            return false;
        } catch (Exception statusError) {
            log.warn("[reconcileExpiredUpload][detailId({}) status query deferred: {}]",
                    detail.getDetailId(), safeError(statusError));
            return false;
        }
    }

    private boolean isUploadUrlExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TkTiktokApiClient.UploadException) {
                return ((TkTiktokApiClient.UploadException) current).isUploadUrlExpired();
            }
            current = current.getCause();
        }
        return false;
    }

    private LambdaUpdateWrapper<TkOpenTiktokPublishDetailDO> processingUpdate(TkOpenTiktokPublishDetailDO detail) {
        LambdaUpdateWrapper<TkOpenTiktokPublishDetailDO> update = Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .apply("COALESCE(retry_count,0) = {0}", defaultInt(detail.getRetryCount()));
        if (StrUtil.isNotBlank(detail.getPublishId())) {
            update.eq(TkOpenTiktokPublishDetailDO::getPublishId, detail.getPublishId());
        } else update.isNull(TkOpenTiktokPublishDetailDO::getPublishId);
        return update;
    }

    private boolean recoverInterruptedInitialization(TkOpenTiktokPublishDetailDO detail, LocalDateTime deadline) {
        if (attemptService.recoverUnsent(detail, deadline)) return true;
        TkOpenTiktokPublishAttemptDO attempt = attemptService.current(detail);
        if (TkOpenTiktokPublishAttemptService.active(attempt, LocalDateTime.now().minusMinutes(WORKER_LEASE_MINUTES))) return false;
        String reason = "Awaiting authoritative TikTok result: publish initialization was interrupted";
        int updated = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNull(TkOpenTiktokPublishDetailDO::getPublishId)
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                        .or().le(TkOpenTiktokPublishDetailDO::getLastSyncTime, deadline))
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, reason)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        if (updated <= 0) {
            return false;
        }
        detail.setStatus("PROCESSING");
        detail.setTiktokStatus("RECOVERY_REQUIRED");
        detail.setFailReason(reason);
        detail.setLastSyncTime(LocalDateTime.now());
        log.warn("[recoverInterruptedInitialization][detailId({}) requires verification; automatic republish blocked]",
                detail.getDetailId());
        return true;
    }

    private boolean reconcileRecoveryDetail(TkOpenTiktokPublishDetailDO detail) {
        try {
            if (StrUtil.isNotBlank(detail.getPublishId())) {
                syncDetail(detail);
                return true;
            }
            if (attemptService.recoverUnsent(detail, LocalDateTime.now().minusMinutes(WORKER_LEASE_MINUTES))) return true;
            // Absence from video.list cannot distinguish private, moderated, delayed or failed posts.
            detailMapper.update(null, processingUpdate(detail)
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        } catch (Exception ex) {
            log.warn("[reconcileRecoveryDetail][detailId({}) will retry]", detail.getDetailId(), ex);
        }
        return false;
    }

    private void markRecoveryRequired(TkOpenTiktokPublishDetailDO detail, String reason) {
        detail.setStatus("PROCESSING").setTiktokStatus("RECOVERY_REQUIRED").setFailReason(reason).setLastSyncTime(LocalDateTime.now());
        detailMapper.update(null, processingUpdate(detail)
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, StrUtil.maxLength(reason, 1000))
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
    }

    private String validAccessToken(TkOpenTiktokConnectionDO connection, TkOpenPublishPlatformAdapter adapter, boolean force) {
        if (!"AUTHORIZED".equals(connection.getAuthStatus())) throw new IllegalStateException("connection is not authorized");
        if (!force && connection.getAccessTokenExpireTime() != null
                && connection.getAccessTokenExpireTime().isAfter(LocalDateTime.now().plusMinutes(1)))
            return secretCipher.decrypt(connection.getAccessTokenCipher());
        String refresh = secretCipher.decrypt(connection.getRefreshTokenCipher());
        TkOpenPublishPlatformAdapter.OAuthTokenResult result = adapter.refreshAccessToken(refresh);
        if (!result.isSuccess()) {
            String reason = StrUtil.blankToDefault(result.getFailReason(), "token refresh failed");
            connection.setTokenStatus("INVALID").setAuthStatus("REAUTH_REQUIRED").setFailReason(reason);
            connectionMapper.updateById(new TkOpenTiktokConnectionDO().setId(connection.getId())
                    .setTokenStatus("INVALID").setAuthStatus("REAUTH_REQUIRED").setFailReason(reason));
            throw new IllegalStateException(reason);
        }
        connection.setAccessTokenCipher(secretCipher.encrypt(result.getAccessToken()));
        if (StrUtil.isNotBlank(result.getRefreshToken())) connection.setRefreshTokenCipher(secretCipher.encrypt(result.getRefreshToken()));
        connection.setAccessTokenExpireTime(LocalDateTime.now().plusSeconds(defaultLong(result.getAccessTokenExpiresIn(), 86400L)));
        connection.setRefreshTokenExpireTime(LocalDateTime.now().plusSeconds(defaultLong(result.getRefreshTokenExpiresIn(), 31536000L)));
        connection.setTokenStatus("NORMAL");
        connection.setFailReason(null);
        connectionMapper.updateById(connection);
        return result.getAccessToken();
    }

    private Map<String, Object> buildPayload(TkOpenTiktokPublishTaskDO task, TkOpenTiktokMediaDO media,
                                             TkOpenPublishPlatformAdapter.CreatorCapabilities creator, UploadSource source) {
        Map<String, Object> post = new LinkedHashMap<>();
        String text = StrUtil.blankToDefault(task.getTitle(), "TikTok video");
        if (StrUtil.isNotBlank(task.getCaption()) && !task.getCaption().equals(task.getTitle())) text += "\n" + task.getCaption();
        post.put("title", text);
        post.put("privacy_level", resolvePrivacy(task.getPrivacyLevel(), creator.getPrivacyLevelOptions()));
        post.put("disable_comment", creator.isCommentDisabled() || !Boolean.TRUE.equals(task.getAllowComment()));
        post.put("disable_duet", creator.isDuetDisabled() || !Boolean.TRUE.equals(task.getAllowDuet()));
        post.put("disable_stitch", creator.isStitchDisabled() || !Boolean.TRUE.equals(task.getAllowStitch()));
        post.put("video_cover_timestamp_ms", media.getCoverTimestampMs() == null ? 1000 : media.getCoverTimestampMs());
        post.put("brand_content_toggle", Boolean.TRUE.equals(task.getBrandContent()));
        post.put("brand_organic_toggle", Boolean.TRUE.equals(task.getCommercialContent()));
        post.put("is_aigc", Boolean.TRUE.equals(task.getAigcContent()));
        Map<String, Object> sourceInfo = new LinkedHashMap<>();
        sourceInfo.put("source", source.pullFromUrl ? "PULL_FROM_URL" : "FILE_UPLOAD");
        if (source.pullFromUrl) sourceInfo.put("video_url", media.getFileUrl());
        else {
            sourceInfo.put("video_size", source.size);
            sourceInfo.put("chunk_size", source.chunkSize);
            sourceInfo.put("total_chunk_count", source.totalChunks);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("post_info", post); payload.put("source_info", sourceInfo);
        return payload;
    }

    private UploadSource resolveSource(TkOpenTiktokMediaDO media, String verifiedDomain) throws Exception {
        if (StrUtil.isNotBlank(media.getScheduledLocalPath())) {
            Path scheduled = java.nio.file.Paths.get(media.getScheduledLocalPath()).toAbsolutePath().normalize();
            if (Files.isRegularFile(scheduled)) return UploadSource.file(scheduled, false);
        }
        if ("READY".equals(media.getScheduledDownloadStatus())) {
            throw new IllegalStateException("scheduled media local copy is unavailable");
        }
        if (isVerifiedPullUrl(media.getFileUrl(), verifiedDomain)) return UploadSource.pull();
        Optional<Path> local = localStorageService == null ? Optional.empty()
                : localStorageService.resolveLocalPath(media.getFileUrl());
        if (local.isPresent()) return UploadSource.file(local.get(), false);
        URI remoteUrl = TkOpenTiktokMediaService.validateRemoteVideoUrl(media.getFileUrl());
        Path temporary = Files.createTempFile("tk-open-publish-", "." + TkOpenTiktokMediaService.normalizeExtension(media.getFileName()));
        try (HttpResponse response = HttpRequest.get(remoteUrl.toString()).timeout(600000).execute()) {
            if (!response.isOk()) throw new IllegalStateException("cannot download media, HTTP " + response.getStatus());
            try (InputStream input = response.bodyStream()) { Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception ex) {
            Files.deleteIfExists(temporary); throw ex;
        }
        return UploadSource.file(temporary, true);
    }

    private void refreshSummary(String clientId, String taskId) {
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(clientId, taskId);
        if (task == null) return;
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<TkOpenTiktokPublishDetailDO>()
                .eq(TkOpenTiktokPublishDetailDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishDetailDO::getTaskId, taskId).last("FOR UPDATE"));
        int success = (int) details.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        int failed = (int) details.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int pending = details.size() - success - failed;
        String status = pending > 0 ? "PROCESSING" : failed == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        task.setSuccessCount(success); task.setFailedCount(failed); task.setPendingCount(pending); task.setStatus(status);
        taskMapper.updateById(task);
    }

    private void publishEvent(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishTaskDO task, String type) {
        if (callbackService == null) return;
        if ("publish.processing".equals(type)) {
            callbackService.enqueue(detail.getClientId(), type, "PUBLISH_DETAIL", detail.getDetailId(),
                    buildPublishEventPayload(detail, task));
        } else {
            callbackService.enqueueOnce(detail.getClientId(), type, "PUBLISH_DETAIL", detail.getDetailId(),
                    buildPublishEventPayload(detail, task), defaultInt(detail.getRetryCount()));
        }
    }

    private void cleanupScheduledMediaAfterCommit(String clientId, String mediaId) {
        if (mediaService == null) return;
        Runnable cleanup = () -> {
            try {
                mediaService.cleanupScheduledPublishMedia(clientId, mediaId);
            } catch (Exception ex) {
                log.warn("[cleanupScheduledMedia][clientId({}) mediaId({}) deferred]", clientId, mediaId, ex);
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

    private TkOpenTiktokPublishVO.TaskResp resolveIdempotentResult(String clientId, String hash,
                                                                   TkOpenApiIdempotencyDO existing,
                                                                   boolean lockingRead) {
        if (!hash.equals(existing.getRequestHash()))
            throw TkOpenApiException.conflict("IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key was used with a different request");
        TkOpenTiktokPublishTaskDO task = lockingRead
                ? taskMapper.selectByClientAndTaskIdForUpdate(clientId, existing.getResourceId())
                : taskMapper.selectByClientAndTaskId(clientId, existing.getResourceId());
        if (task == null) throw TkOpenApiException.unavailable("IDEMPOTENCY_RESULT_UNAVAILABLE", "idempotent result is unavailable");
        return toTaskResp(task);
    }

    static String requestHash(TkOpenTiktokPublishVO.TaskCreateReq request) {
        return TkOpenApiSigner.sha256Hex(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8));
    }

    static String requestHash(TkOpenTiktokPublishVO.QuickTaskCreateReq request) {
        return TkOpenApiSigner.sha256Hex(JsonUtils.toJsonString(request).getBytes(StandardCharsets.UTF_8));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (StrUtil.isBlank(idempotencyKey)) {
            throw TkOpenApiException.badRequest("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }
        if (idempotencyKey.length() > 128) {
            throw TkOpenApiException.badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is too long");
        }
    }

    private void submitAfterCommit(String clientId, String taskId) {
        Runnable submit = () -> submitTask(clientId, taskId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
    }

    private void submitTask(String clientId, String taskId) {
        if (stopping) return;
        try { executor.execute(() -> processTask(clientId, taskId)); }
        catch (RejectedExecutionException ex) { log.info("[submitTask][taskId({}) retained for recovery]", taskId); }
    }

    public int recoverJournal(int limit) {
        return responseJournal.replay(limit, detailMapper, attemptService);
    }

    private TkOpenTiktokPublishTaskDO requireTask(String clientId, String taskId) {
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(clientId, taskId);
        if (task == null) throw TkOpenApiException.notFound("PUBLISH_TASK_NOT_FOUND", "publish task does not exist");
        return task;
    }

    private String currentClient() { return TkOpenApiContext.getRequiredPrincipal().getClientId(); }
    private TkOpenPublishPlatformAdapter platform() { return platformRegistry.getRequired("TIKTOK"); }
    private boolean defaultBool(Boolean value, boolean fallback) { return value == null ? fallback : value; }
    private int defaultInt(Integer value) { return value == null ? 0 : value; }
    private long defaultLong(Long value, long fallback) { return value == null || value <= 0 ? fallback : value; }
    private boolean isSuccess(String status) { return "PUBLISH_COMPLETE".equals(status); }
    private boolean isFailed(String status) { return "FAILED".equals(status); }

    private String resolvePrivacy(String requested, List<String> options) {
        if (options == null || options.isEmpty()) return requested;
        for (String option : options) if (option.equalsIgnoreCase(requested)) return requested;
        for (String option : options) if ("SELF_ONLY".equalsIgnoreCase(option)) return option;
        return options.get(0);
    }

    private boolean isVerifiedPullUrl(String url, String domain) {
        if (!StrUtil.startWithIgnoreCase(url, "https://") || StrUtil.isBlank(domain)) return false;
        try {
            String host = URI.create(url).getHost().toLowerCase(Locale.ROOT);
            String expected = domain.replaceFirst("(?i)^https?://", "");
            int slash = expected.indexOf('/'); if (slash >= 0) expected = expected.substring(0, slash);
            expected = expected.toLowerCase(Locale.ROOT);
            return host.equals(expected) || host.endsWith("." + expected);
        } catch (Exception ex) { return false; }
    }

    private TkOpenTiktokPublishVO.TaskResp toTaskResp(TkOpenTiktokPublishTaskDO task) {
        TkOpenTiktokPublishVO.TaskResp response = new TkOpenTiktokPublishVO.TaskResp();
        response.setTaskId(task.getTaskId()); response.setMediaId(task.getMediaId()); response.setExternalRequestId(task.getExternalRequestId());
        response.setStatus(task.getStatus()); response.setAccountCount(task.getAccountCount()); response.setSuccessCount(task.getSuccessCount());
        response.setFailedCount(task.getFailedCount()); response.setPendingCount(task.getPendingCount()); response.setFailReason(task.getFailReason());
        response.setScheduledAt(task.getScheduledAt());
        response.setScheduleStatus(task.getScheduleStatus());
        boolean mutableSchedule = "SCHEDULED".equals(task.getStatus()) && task.getScheduledAt() != null;
        response.setCanReschedule(mutableSchedule);
        response.setCanCancel(mutableSchedule);
        response.setCreateTime(task.getCreateTime()); response.setUpdateTime(task.getUpdateTime()); return response;
    }

    static LocalDateTime parseScheduledAt(String raw) {
        if (StrUtil.isBlank(raw)) return null;
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return ZonedDateTime.parse(value).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignoredAgain) {
                try {
                    return LocalDateTime.parse(value);
                } catch (DateTimeParseException invalid) {
                    throw TkOpenApiException.badRequest("SCHEDULE_TIME_INVALID",
                            "scheduledAt must be an ISO-8601 date-time");
                }
            }
        }
    }

    private void validateSchedule(TkOpenTiktokPublishVO.TaskCreateReq request,
                                  LocalDateTime scheduledAt, int accountCount) {
        if (scheduledAt == null) return;
        if (!scheduledAt.isAfter(LocalDateTime.now())) {
            throw TkOpenApiException.badRequest("SCHEDULE_TIME_INVALID", "scheduledAt must be in the future");
        }
        if (accountCount != 1) {
            throw TkOpenApiException.badRequest("SCHEDULE_ACCOUNT_COUNT_INVALID",
                    "scheduled publish supports exactly one TikTok account");
        }
        if (!"DIRECT_POST".equals(request.getPostMode())) {
            throw TkOpenApiException.badRequest("SCHEDULE_MODE_UNSUPPORTED",
                    "scheduled publish supports DIRECT_POST only");
        }
    }

    private void validateOptionalIdempotencyKey(String idempotencyKey) {
        if (StrUtil.isNotBlank(idempotencyKey) && idempotencyKey.length() > 128) {
            throw TkOpenApiException.badRequest("IDEMPOTENCY_KEY_INVALID", "Idempotency-Key is too long");
        }
    }

    private TkOpenTiktokPublishVO.DetailResp toDetailResp(TkOpenTiktokPublishDetailDO detail) {
        TkOpenTiktokPublishVO.DetailResp response = new TkOpenTiktokPublishVO.DetailResp();
        response.setDetailId(detail.getDetailId()); response.setTaskId(detail.getTaskId()); response.setConnectionId(detail.getConnectionId());
        response.setAccountName(detail.getAccountName()); response.setStatus(detail.getStatus()); response.setTiktokStatus(detail.getTiktokStatus());
        response.setPublishId(detail.getPublishId()); response.setPublishUrl(detail.getPublishUrl()); response.setFailReason(detail.getFailReason());
        response.setRetryCount(detail.getRetryCount()); response.setUpdateTime(detail.getUpdateTime()); return response;
    }

    private String metricsStateBeforePublicPost(TkOpenTiktokPublishDetailDO detail) {
        if ("FAILED".equals(detail.getStatus())) return "FAILED";
        if ("SEND_TO_USER_INBOX".equalsIgnoreCase(detail.getTiktokStatus())) return "UNAVAILABLE";
        if ("PENDING".equals(detail.getStatus())) return "WAITING_PUBLISH";
        return StrUtil.blankToDefault(detail.getMetricsStatus(), "WAITING_PUBLIC");
    }

    private TkOpenTiktokPublishVO.MetricsResp batchFailure(String taskId, String status, String reason) {
        TkOpenTiktokPublishVO.MetricsResp response = new TkOpenTiktokPublishVO.MetricsResp();
        response.setTaskId(taskId);
        response.setMetricsStatus(status);
        response.setMetricsFailReason(reason);
        return response;
    }

    private boolean containsInvalidAccessToken(
            Map<String, TkOpenPublishPlatformAdapter.VideoMetricsResult> metrics) {
        return metrics != null && metrics.values().stream().anyMatch(Objects::nonNull)
                && metrics.values().stream().anyMatch(TkOpenPublishPlatformAdapter.VideoMetricsResult::isAccessTokenInvalid);
    }

    private TkOpenTiktokPublishVO.MetricsResp persistMetrics(TkOpenTiktokPublishDetailDO detail,
                                                              TkOpenPublishPlatformAdapter.VideoMetricsResult result) {
        LocalDateTime now = LocalDateTime.now();
        detail.setPublicPostId(StrUtil.blankToDefault(result.getPublicPostId(), detail.getPublicPostId()));
        detail.setPublishUrl(StrUtil.blankToDefault(result.getShareUrl(), detail.getPublishUrl()));
        detail.setViewCount(result.getViewCount());
        detail.setLikeCount(result.getLikeCount());
        detail.setCommentCount(result.getCommentCount());
        detail.setShareCount(result.getShareCount());
        detail.setMetricsStatus("AVAILABLE");
        detail.setMetricsFailReason(null);
        detail.setMetricsLastSyncTime(now);
        detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .set(TkOpenTiktokPublishDetailDO::getPublicPostId, detail.getPublicPostId())
                .set(TkOpenTiktokPublishDetailDO::getPublishUrl, detail.getPublishUrl())
                .set(TkOpenTiktokPublishDetailDO::getViewCount, detail.getViewCount())
                .set(TkOpenTiktokPublishDetailDO::getLikeCount, detail.getLikeCount())
                .set(TkOpenTiktokPublishDetailDO::getCommentCount, detail.getCommentCount())
                .set(TkOpenTiktokPublishDetailDO::getShareCount, detail.getShareCount())
                .set(TkOpenTiktokPublishDetailDO::getMetricsStatus, detail.getMetricsStatus())
                .set(TkOpenTiktokPublishDetailDO::getMetricsFailReason, (String) null)
                .set(TkOpenTiktokPublishDetailDO::getMetricsLastSyncTime, now));
        return toMetricsResp(detail, "AVAILABLE", null);
    }

    private TkOpenTiktokPublishVO.MetricsResp persistMetricsFailure(TkOpenTiktokPublishDetailDO detail,
                                                                      String status, String reason) {
        String safeReason = StrUtil.maxLength(StrUtil.blankToDefault(reason, "TikTok video metrics query failed"), 1000);
        detail.setMetricsStatus(status);
        detail.setMetricsFailReason(safeReason);
        detail.setMetricsLastSyncTime(LocalDateTime.now());
        detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .set(TkOpenTiktokPublishDetailDO::getMetricsStatus, status)
                .set(TkOpenTiktokPublishDetailDO::getMetricsFailReason, safeReason)
                .set(TkOpenTiktokPublishDetailDO::getMetricsLastSyncTime, detail.getMetricsLastSyncTime()));
        return toMetricsResp(detail, status, safeReason);
    }

    private TkOpenTiktokPublishVO.MetricsResp toMetricsResp(TkOpenTiktokPublishDetailDO detail,
                                                              String status, String reason) {
        TkOpenTiktokPublishVO.MetricsResp response = new TkOpenTiktokPublishVO.MetricsResp();
        response.setTaskId(detail.getTaskId());
        response.setDetailId(detail.getDetailId());
        response.setConnectionId(detail.getConnectionId());
        response.setPublishId(detail.getPublishId());
        response.setPublicPostId(detail.getPublicPostId());
        response.setPublishUrl(detail.getPublishUrl());
        response.setMetricsStatus(status);
        response.setViewCount(detail.getViewCount());
        response.setLikeCount(detail.getLikeCount());
        response.setCommentCount(detail.getCommentCount());
        response.setShareCount(detail.getShareCount());
        response.setMetricsLastSyncTime(detail.getMetricsLastSyncTime());
        response.setMetricsFailReason(reason);
        return response;
    }

    @PreDestroy public void destroy() {
        stopping = true;
        executor.shutdown();
        try { executor.awaitTermination(45, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        heartbeats.shutdown();
    }

    private static class UploadSource {
        private static final long MAX_SINGLE_CHUNK_SIZE = 64_000_000L;
        private static final long DEFAULT_CHUNK_SIZE = 32_000_000L;
        private static final long MAX_VIDEO_SIZE = 4_000_000_000L;

        private final boolean pullFromUrl; private final Path file; private final boolean temporary;
        private final long size; private final long chunkSize; private final int totalChunks;
        private UploadSource(boolean pull, Path file, boolean temporary, long size, long chunkSize, int totalChunks) {
            this.pullFromUrl = pull; this.file = file; this.temporary = temporary; this.size = size; this.chunkSize = chunkSize; this.totalChunks = totalChunks;
        }
        static UploadSource pull() { return new UploadSource(true, null, false, 0, 0, 0); }
        static UploadSource file(Path path, boolean temporary) throws Exception {
            long size = Files.size(path);
            if (size <= 0 || size > MAX_VIDEO_SIZE) {
                throw new IllegalArgumentException("TikTok upload size must be between 1 byte and 4GB");
            }
            long chunkSize = size <= MAX_SINGLE_CHUNK_SIZE ? size : DEFAULT_CHUNK_SIZE;
            int totalChunks = size <= MAX_SINGLE_CHUNK_SIZE ? 1 : Math.toIntExact(size / DEFAULT_CHUNK_SIZE);
            return new UploadSource(false, path, temporary, size, chunkSize, Math.max(1, totalChunks));
        }
        void cleanup() { if (temporary && file != null) try { Files.deleteIfExists(file); } catch (Exception ignored) {} }
    }
}
