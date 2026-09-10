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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TkOpenTiktokPublishService {

    private static final int PENDING_RECOVERY_DELAY_MINUTES = 1;
    private static final int INITIALIZATION_LEASE_MINUTES = 15;
    private static final int STATUS_STALE_MINUTES = 2;

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
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

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
                .status("PENDING").build();
        taskMapper.insert(task);
        for (TkOpenTiktokConnectionDO connection : connections) {
            detailMapper.insert(TkOpenTiktokPublishDetailDO.builder()
                    .detailId(TkOpenApiIds.next("detail"))
                    .taskId(task.getTaskId()).clientId(clientId).connectionId(connection.getConnectionId())
                    .accountName(StrUtil.blankToDefault(connection.getDisplayName(), connection.getUsername()))
                    .status("PENDING").tiktokStatus("LOCAL_PENDING").metricsStatus("WAITING_PUBLISH")
                    .retryCount(0).build());
        }
        record.setStatus("COMPLETED");
        idempotencyMapper.updateById(record);
        submitAfterCommit(clientId, task.getTaskId());
        return toTaskResp(task);
    }

    public TkOpenTiktokPublishVO.TaskResp getTask(String taskId) {
        return toTaskResp(requireTask(currentClient(), taskId));
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

    @Transactional(rollbackFor = Exception.class)
    public void retry(String detailId) {
        String clientId = currentClient();
        TkOpenTiktokPublishDetailDO detail = detailMapper.selectByClientAndDetailId(clientId, detailId);
        if (detail == null) throw TkOpenApiException.notFound("PUBLISH_DETAIL_NOT_FOUND", "publish detail does not exist");
        if (!"FAILED".equals(detail.getStatus()))
            throw TkOpenApiException.badRequest("PUBLISH_RETRY_STATUS_INVALID", "publish detail cannot be retried");
        detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RETRY_PENDING")
                .set(TkOpenTiktokPublishDetailDO::getPublishId, null)
                .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                .set(TkOpenTiktokPublishDetailDO::getRetryCount, defaultInt(detail.getRetryCount()) + 1));
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
            if (failInterruptedInitialization(detail, initializationDeadline)) {
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

    public int resumeStalePending(int limit) {
        List<TkOpenTiktokPublishDetailDO> pending = detailMapper.selectStalePending(
                LocalDateTime.now().minusMinutes(PENDING_RECOVERY_DELAY_MINUTES), limit);
        Set<String> submittedTasks = new HashSet<>();
        for (TkOpenTiktokPublishDetailDO detail : pending) {
            String key = detail.getClientId() + "\n" + detail.getTaskId();
            if (submittedTasks.add(key)) {
                executor.submit(() -> processTask(detail.getClientId(), detail.getTaskId()));
            }
        }
        return submittedTasks.size();
    }

    void processTask(String clientId, String taskId) {
        try {
            for (TkOpenTiktokPublishDetailDO detail : detailMapper.selectListByClientAndTaskId(clientId, taskId)) {
                if ("PENDING".equals(detail.getStatus())) processDetail(detail);
            }
        } finally {
            refreshSummary(clientId, taskId);
        }
    }

    private void processDetail(TkOpenTiktokPublishDetailDO detail) {
        int claimed = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PENDING")
                .set(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "LOCAL_PROCESSING")
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        if (claimed <= 0) return;
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(detail.getClientId(), detail.getTaskId());
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(detail.getClientId(), detail.getConnectionId());
        TkOpenTiktokMediaDO media = task == null ? null : mediaMapper.selectByClientAndMediaId(detail.getClientId(), task.getMediaId());
        if (task == null || connection == null || media == null) {
            failDetail(detail, task, "publish resource no longer exists");
            return;
        }
        UploadSource source = null;
        try {
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
            TkOpenPublishPlatformAdapter.PublishInitResult initialized = adapter.initVideoPost(token, task.getPostMode(), payload);
            if (initialized.isAccessTokenInvalid()) {
                token = validAccessToken(connection, adapter, true);
                initialized = adapter.initVideoPost(token, task.getPostMode(), payload);
            }
            if (!initialized.isSuccess()) throw new IllegalStateException(initialized.getFailReason());
            detail.setPublishId(initialized.getPublishId());
            detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                    .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                    .set(TkOpenTiktokPublishDetailDO::getPublishId, initialized.getPublishId())
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus,
                            StrUtil.isBlank(initialized.getPublishId()) ? "SEND_TO_USER_INBOX" : "UPLOAD_PENDING")
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
            if (!source.pullFromUrl) {
                if (StrUtil.isBlank(initialized.getUploadUrl())) throw new IllegalStateException("platform upload URL is missing");
                adapter.uploadVideo(initialized.getUploadUrl(), source.file, media.getContentType());
            }
            boolean sentToUserInbox = "UPLOAD_TO_INBOX".equalsIgnoreCase(task.getPostMode())
                    && StrUtil.isBlank(initialized.getPublishId());
            detail.setStatus(sentToUserInbox ? "SUCCESS" : "PROCESSING");
            detail.setTiktokStatus(sentToUserInbox ? "SEND_TO_USER_INBOX" : "PROCESSING");
            detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                    .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                    .set(TkOpenTiktokPublishDetailDO::getStatus, detail.getStatus())
                    .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, detail.getTiktokStatus())
                    .set(TkOpenTiktokPublishDetailDO::getFailReason, null)
                    .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
            connectionMapper.updateById(new TkOpenTiktokConnectionDO().setId(connection.getId()).setLastPublishTime(LocalDateTime.now()));
            publishEvent(detail, task, sentToUserInbox ? "publish.success" : "publish.processing");
        } catch (Exception ex) {
            failDetail(detail, task, "TikTok publish failed: " + ex.getMessage());
        } finally {
            if (source != null) source.cleanup();
        }
    }

    private void syncDetail(TkOpenTiktokPublishDetailDO detail) {
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(detail.getClientId(), detail.getTaskId());
        TkOpenTiktokConnectionDO connection = connectionMapper.selectByClientAndConnectionId(detail.getClientId(), detail.getConnectionId());
        if (task == null || connection == null) return;
        try {
            TkOpenPublishPlatformAdapter adapter = platform();
            String token = validAccessToken(connection, adapter, false);
            TkOpenPublishPlatformAdapter.PublishStatusResult status = adapter.fetchPostStatus(token, detail.getPublishId());
            if (status.isAccessTokenInvalid()) status = adapter.fetchPostStatus(validAccessToken(connection, adapter, true), detail.getPublishId());
            if (!status.isSuccess()) throw new IllegalStateException(status.getFailReason());
            detail.setTiktokStatus(status.getStatus());
            detail.setLastSyncTime(LocalDateTime.now());
            List<String> publicPostIds = status.getPublicPostIds() == null
                    ? Collections.emptyList() : status.getPublicPostIds();
            if (StrUtil.isNotBlank(publicPostIds.isEmpty() ? null : publicPostIds.get(0))) {
                detail.setPublicPostId(publicPostIds.get(0));
                detail.setMetricsStatus("SYNCING");
                detail.setMetricsFailReason(null);
            } else if (isSuccess(status.getStatus())) {
                detail.setMetricsStatus("WAITING_PUBLIC");
            }
            if (isSuccess(status.getStatus())) {
                detail.setStatus("SUCCESS");
                detail.setFailReason(null);
                detailMapper.updateById(detail);
                publishEvent(detail, task, "publish.success");
            } else if (isFailed(status.getStatus())) {
                failDetail(detail, task, StrUtil.blankToDefault(status.getFailReason(), "TikTok rejected the publish"));
            } else {
                detailMapper.updateById(detail);
            }
        } catch (Exception ex) {
            detailMapper.updateById(new TkOpenTiktokPublishDetailDO().setId(detail.getId())
                    .setLastSyncTime(LocalDateTime.now()).setFailReason(StrUtil.maxLength(ex.getMessage(), 1000)));
        } finally {
            refreshSummary(detail.getClientId(), detail.getTaskId());
        }
    }

    private boolean failInterruptedInitialization(TkOpenTiktokPublishDetailDO detail, LocalDateTime deadline) {
        String reason = "Publish execution was interrupted before the platform publish ID was persisted; "
                + "verify the TikTok account before retrying to avoid a duplicate post";
        int updated = detailMapper.update(null, Wrappers.lambdaUpdate(TkOpenTiktokPublishDetailDO.class)
                .eq(TkOpenTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkOpenTiktokPublishDetailDO::getStatus, "PROCESSING")
                .isNull(TkOpenTiktokPublishDetailDO::getPublishId)
                .and(wrapper -> wrapper.isNull(TkOpenTiktokPublishDetailDO::getLastSyncTime)
                        .or().le(TkOpenTiktokPublishDetailDO::getLastSyncTime, deadline))
                .set(TkOpenTiktokPublishDetailDO::getStatus, "FAILED")
                .set(TkOpenTiktokPublishDetailDO::getTiktokStatus, "RECOVERY_REQUIRED")
                .set(TkOpenTiktokPublishDetailDO::getFailReason, reason)
                .set(TkOpenTiktokPublishDetailDO::getLastSyncTime, LocalDateTime.now()));
        if (updated <= 0) {
            return false;
        }
        detail.setStatus("FAILED");
        detail.setTiktokStatus("RECOVERY_REQUIRED");
        detail.setFailReason(reason);
        detail.setLastSyncTime(LocalDateTime.now());
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(
                detail.getClientId(), detail.getTaskId());
        if (task != null) {
            publishEvent(detail, task, "publish.failed");
        }
        refreshSummary(detail.getClientId(), detail.getTaskId());
        return true;
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

    private void failDetail(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishTaskDO task, String reason) {
        detail.setStatus("FAILED"); detail.setTiktokStatus("FAILED"); detail.setFailReason(StrUtil.maxLength(reason, 1000));
        detail.setLastSyncTime(LocalDateTime.now()); detailMapper.updateById(detail);
        if (task != null) publishEvent(detail, task, "publish.failed");
    }

    private void refreshSummary(String clientId, String taskId) {
        TkOpenTiktokPublishTaskDO task = taskMapper.selectByClientAndTaskId(clientId, taskId);
        if (task == null) return;
        List<TkOpenTiktokPublishDetailDO> details = detailMapper.selectListByClientAndTaskId(clientId, taskId);
        int success = (int) details.stream().filter(item -> "SUCCESS".equals(item.getStatus())).count();
        int failed = (int) details.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int pending = details.size() - success - failed;
        String status = pending > 0 ? "PROCESSING" : failed == 0 ? "SUCCESS" : success > 0 ? "PARTIAL_SUCCESS" : "FAILED";
        task.setSuccessCount(success); task.setFailedCount(failed); task.setPendingCount(pending); task.setStatus(status);
        taskMapper.updateById(task);
    }

    private void publishEvent(TkOpenTiktokPublishDetailDO detail, TkOpenTiktokPublishTaskDO task, String type) {
        if (callbackService == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId()); payload.put("detailId", detail.getDetailId());
        payload.put("connectionId", detail.getConnectionId()); payload.put("externalRequestId", task.getExternalRequestId());
        payload.put("status", detail.getStatus()); payload.put("publishId", detail.getPublishId());
        payload.put("publicPostId", detail.getPublicPostId());
        payload.put("publishUrl", detail.getPublishUrl()); payload.put("failReason", detail.getFailReason());
        callbackService.enqueue(detail.getClientId(), type, "PUBLISH_DETAIL", detail.getDetailId(), payload);
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
        Runnable submit = () -> executor.submit(() -> processTask(clientId, taskId));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        } else submit.run();
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
    private boolean isSuccess(String status) { return StrUtil.equalsAnyIgnoreCase(status, "PUBLISH_COMPLETE", "SUCCESS", "SEND_TO_USER_INBOX"); }
    private boolean isFailed(String status) { return StrUtil.containsIgnoreCase(status, "FAIL") || StrUtil.containsIgnoreCase(status, "REJECT"); }

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
        response.setCreateTime(task.getCreateTime()); response.setUpdateTime(task.getUpdateTime()); return response;
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

    @PreDestroy public void destroy() { executor.shutdown(); }

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
