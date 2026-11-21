package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXPublishTaskMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.channel.TkChannelProperties;
import cn.iocoder.yudao.module.tk.service.channel.TkChannelVideoClient;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAccess;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXPublishService;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXTokenService;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TkOpenXPublishJob {
    private final TkOpenXPublishTaskMapper tasks;
    private final TkOpenXConnectionMapper connections;
    private final TkOpenXMediaService media;
    private final TkOpenXTokenService tokens;
    private final TkChannelVideoClient videos;
    private final TkOpenXPublishService publish;
    private final TkOpenXAccess access;
    private final TkOpenApiCallbackService callbacks;
    private final PlatformTransactionManager transactions;

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void tick() {
        if (!access.isEnabled()) return;
        List<TkOpenXPublishTaskDO> due = tasks.selectDue(LocalDateTime.now(), 16);
        for (TkOpenXPublishTaskDO item : due) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> process(item.getId()));
        }
    }

    void process(Long id) {
        TkOpenXPublishTaskDO task = tasks.selectByIdForUpdate(id);
        if (task == null || !("PENDING".equals(task.getStatus()) || "PROCESSING".equals(task.getStatus()))) return;
        if (task.getLeaseUntil() != null && task.getLeaseUntil().isAfter(LocalDateTime.now())) return;
        String lease = UUID.randomUUID().toString().replace("-", "");
        task.setStatus("PROCESSING");
        task.setLeaseToken(lease);
        task.setLeaseUntil(LocalDateTime.now().plusMinutes(5));
        tasks.updateById(task);
        String stage = "connection";
        try {
            if (!publish.isClientRunnable(task.getClientId())) {
                fail(task, "X_PERMISSION_DENIED", "client is not allowed to publish");
                return;
            }
            TkOpenXConnectionDO connection = connections.selectByClientAndConnectionId(
                    task.getClientId(), task.getConnectionId());
            if (connection == null || !java.util.Objects.equals(connection.getWorkspaceId(), task.getWorkspaceId())
                    || !"AUTHORIZED".equals(connection.getAuthStatus())) {
                fail(task, "X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
                return;
            }
            stage = "media";
            TkOpenXMediaDO ready = media.requireReady(task.getClientId(), task.getMediaId(), task.getWorkspaceId());
            String source = media.resolveReadUrl(ready);
            stage = "token";
            String token = tokens.accessToken(connection);
            TkSocialPublishTaskDO socialTask = new TkSocialPublishTaskDO();
            socialTask.setChannelText(task.getText());
            socialTask.setChannel(TkChannelProperties.X);
            TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();
            detail.setPlatform(TkChannelProperties.X);
            detail.setExternalContainerId(task.getExternalContainerId());
            detail.setPlatformStatus(task.getPlatformStatus());
            detail.setUploadOffset(task.getUploadOffset() == null ? 0L : task.getUploadOffset());
            stage = "platform";
            TkChannelVideoClient.Checkpoint step = videos.advance(socialTask, detail, token, source, ready.getFileSize(), null);
            stage = "checkpoint";
            task.setExternalContainerId(step.getContainerId());
            task.setPlatformStatus(step.getStage());
            task.setUploadOffset(step.getOffset());
            task.setLeaseToken(null);
            task.setLeaseUntil(null);
            if ("PUBLISHED".equals(step.getStage())) {
                task.setStatus("SUCCESS");
                task.setTweetId(step.getPostId());
                task.setPublishUrl("https://x.com/i/web/status/" + step.getPostId());
                task.setNextRetryTime(null);
                tasks.updateById(task);
                enqueue(task, "publish.completed");
            } else {
                int polls = (task.getPollCount() == null ? 0 : task.getPollCount()) + 1;
                task.setPollCount(polls);
                if (polls >= 360) {
                    fail(task, "POLL_TIMEOUT", "platform processing timed out");
                    return;
                }
                task.setStatus("PENDING");
                task.setNextRetryTime(LocalDateTime.now().plusSeconds(Math.max(2, step.getDelay())));
                tasks.updateById(task);
            }
        } catch (TkOpenApiException ex) {
            log.warn("X OpenAPI publish failed: task={} stage={} code={}", task.getTaskId(), stage, ex.getCode());
            fail(task, ex.getCode(), safeMessage(ex.getCode()));
        } catch (TkSocialPlatformException ex) {
            log.warn("X OpenAPI publish platform error: task={} code={}", task.getTaskId(), ex.getCode());
            if (ex.isUncertain()) fail(task, "PLATFORM_UNCERTAIN", "platform result requires verification");
            else if (ex.isRetryable()) {
                task.setStatus("PENDING");
                task.setLeaseToken(null);
                task.setLeaseUntil(null);
                task.setNextRetryTime(LocalDateTime.now().plusSeconds(15));
                tasks.updateById(task);
            } else fail(task, "PLATFORM_ERROR", "platform rejected the publish request");
        } catch (RuntimeException ex) {
            log.warn("X OpenAPI publish failed: task={} stage={} type={} code=INTERNAL_ERROR",
                    task.getTaskId(), stage, ex.getClass().getSimpleName());
            fail(task, "INTERNAL_ERROR", "publish failed");
        }
    }

    private void fail(TkOpenXPublishTaskDO task, String code, String message) {
        task.setStatus("FAILED");
        task.setFailCode(code);
        task.setFailReason(message);
        task.setLeaseToken(null);
        task.setLeaseUntil(null);
        task.setNextRetryTime(null);
        tasks.updateById(task);
        enqueue(task, "publish.failed");
    }

    private void enqueue(TkOpenXPublishTaskDO task, String event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.getTaskId());
            payload.put("connectionId", task.getConnectionId());
            payload.put("mediaId", task.getMediaId());
            payload.put("status", task.getStatus());
            payload.put("failCode", task.getFailCode());
            payload.put("tweetId", task.getTweetId());
            payload.put("publishUrl", task.getPublishUrl());
            callbacks.enqueueOnce(task.getClientId(), event, "PUBLISH_TASK", task.getTaskId(), payload);
        } catch (RuntimeException ex) {
            log.warn("X OpenAPI publish callback enqueue skipped: event={}", event);
        }
    }

    private static String safeMessage(String code) {
        if ("X_CONNECTION_DISABLED".equals(code)) return "X connection is missing, expired or revoked";
        if ("MEDIA_NOT_READY".equals(code) || "MEDIA_NOT_FOUND".equals(code)) return "media is not available";
        if ("MEDIA_SOURCE_UNAVAILABLE".equals(code)) return "uploaded video cannot be read";
        if ("X_PERMISSION_DENIED".equals(code) || "X_OPEN_API_DISABLED".equals(code)) return "client is not allowed to publish";
        return "publish failed";
    }
}
