package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAuthService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TkOpenXPublishService {
    private final TkOpenXAccess access;
    private final TkOpenXAuthService auth;
    private final TkOpenXMediaService media;
    private final TkOpenXConnectionMapper connections;
    private final TkOpenXPublishTaskMapper tasks;
    private final TkOpenApiClientMapper clients;
    private final TkSocialAuthSessionMapper socialSessions;
    private final TkOpenXTokenService tokens;
    private final PlatformTransactionManager transactions;

    public Map<String, Object> createSession(String endUserId, String returnUrl, String clientState) {
        return auth.createSession(endUserId, returnUrl, clientState);
    }

    public Map<String, Object> session(String sessionId) {
        return auth.session(sessionId);
    }

    public String consentClient(String sessionId) {
        return auth.consentClient(sessionId);
    }

    public URI consent(String sessionId, String browserToken, String formToken) {
        return auth.consent(sessionId, browserToken, formToken);
    }

    public Map<String, Object> redeemTicket(String ticket) {
        return auth.redeemTicket(ticket);
    }

    public List<Map<String, Object>> connections() {
        String clientId = access.requireClient("x.auth");
        List<Map<String, Object>> result = new ArrayList<>();
        for (TkOpenXConnectionDO row : connections.selectListByClient(clientId)) {
            result.add(connectionResult(row));
        }
        return result;
    }

    public boolean revoke(String connectionId) {
        String clientId = access.requireClient("x.auth");
        return Boolean.TRUE.equals(new TransactionTemplate(transactions).execute(status -> {
            TkOpenXConnectionDO row = connections.selectByClientAndConnectionIdForUpdate(clientId, connectionId);
            if (row == null) {
                throw TkOpenApiException.notFound("X_CONNECTION_NOT_FOUND", "connection does not exist");
            }
            String externalId = row.getExternalAccountId();
            row.setAuthStatus("REVOKED");
            row.setFailReason("Revoked by client");
            tokens.clearSecrets(row);
            connections.updateById(row);
            if (externalId != null) {
                auth.collapseDuplicates(clientId, externalId, row.getId());
                for (TkOpenXConnectionDO other : connections.selectListByClientAndExternalAccountIdForUpdate(clientId, externalId)) {
                    if (other.getId() != null && !other.getId().equals(row.getId())
                            && "AUTHORIZED".equals(other.getAuthStatus())) {
                        other.setAuthStatus("REVOKED");
                        other.setFailReason("Revoked by client");
                        tokens.clearSecrets(other);
                        connections.updateById(other);
                    }
                }
            }
            return true;
        }));
    }

    public Map<String, Object> create(String connectionId, String mediaId, String text, String key,
                                      String endUserId, Long generationTaskId) {
        String clientId = access.requireClient("x.publish");
        if (generationTaskId != null) {
            throw TkOpenApiException.badRequest("X_GENERATION_TASK_UNSUPPORTED",
                    "generationTaskId is not supported; upload media and pass mediaId");
        }
        if (connectionId == null || connectionId.isEmpty() || mediaId == null || mediaId.isEmpty()
                || text == null || text.length() > 280 || key == null || !key.matches("[!-~]{1,128}")) {
            throw TkOpenApiException.badRequest("X_PUBLISH_INPUT_INVALID", "Invalid connectionId, mediaId, text or Idempotency-Key");
        }
        String payloadHash = TkSocialAuthService.hash(connectionId + "\n" + mediaId + "\n" + text);
        try {
            return new TransactionTemplate(transactions).execute(status -> {
                TkOpenXPublishTaskDO existing = tasks.selectByClientAndIdempotencyKey(clientId, key);
                if (existing != null) {
                    if (!payloadHash.equals(existing.getPayloadHash())) {
                        throw TkOpenApiException.conflict("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key is in use");
                    }
                    return taskResult(existing);
                }
                TkOpenXConnectionDO connection = connections.selectByClientAndConnectionIdForUpdate(clientId, connectionId);
                if (connection == null || !"AUTHORIZED".equals(connection.getAuthStatus())) {
                    throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
                }
                media.requireReady(clientId, mediaId);
                TkOpenXPublishTaskDO task = TkOpenXPublishTaskDO.builder()
                        .taskId(TkOpenApiIds.next("xtask"))
                        .clientId(clientId)
                        .connectionId(connectionId)
                        .mediaId(mediaId)
                        .text(text)
                        .endUserId(endUserId == null || endUserId.isEmpty() ? null : endUserId)
                        .idempotencyKey(key)
                        .payloadHash(payloadHash)
                        .status("PENDING")
                        .nextRetryTime(LocalDateTime.now())
                        .uploadOffset(0L)
                        .pollCount(0)
                        .build();
                tasks.insert(task);
                return taskResult(task);
            });
        } catch (DuplicateKeyException ex) {
            TkOpenXPublishTaskDO raced = tasks.selectByClientAndIdempotencyKey(clientId, key);
            if (raced == null) throw ex;
            if (!payloadHash.equals(raced.getPayloadHash())) {
                throw TkOpenApiException.conflict("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key is in use");
            }
            return taskResult(raced);
        }
    }

    public Map<String, Object> get(String taskId) {
        String clientId = access.requireClient("x.publish");
        TkOpenXPublishTaskDO task = tasks.selectByClientAndTaskId(clientId, taskId);
        if (task == null) throw TkOpenApiException.notFound("X_TASK_NOT_FOUND", "publish task does not exist");
        return taskResult(task);
    }

    /**
     * Leftover social-table OpenAPI tasks only. Workbench tasks (no openXConnectionId) are unchanged.
     * Workbench accounts without open_client_id are never absorbed.
     * Leftover OpenAPI tasks are not executed: they cannot safely use workbench tokens,
     * and a dedicated revoke must not be bypassed. Rows are left in place for operator review.
     */
    public void assertRunnable(TkSocialPublishTaskDO task, TkSocialAccountDO account) {
        if (task == null || task.getOpenXConnectionId() == null) return;
        TkSocialAuthSessionDO row = socialSessions.selectById(task.getOpenXConnectionId());
        if (row == null || row.getOpenClientId() == null || row.getOpenClientId().isEmpty()) {
            throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
        }
        if (!access.isEnabled()) {
            throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        }
        TkOpenApiClientDO client = clients.selectByClientId(row.getOpenClientId());
        if (client == null || !Integer.valueOf(0).equals(client.getStatus())
                || !new TkOpenApiPrincipal(client.getClientId(), client.getClientName(), client.getPermissions())
                .hasPermission("x.publish")) {
            throw TkOpenApiException.forbidden("X_PERMISSION_DENIED", "client is not granted x.publish");
        }
        TkOpenXConnectionDO connection = connections.selectBySourceSocialSessionId(row.getOpenClientId(), row.getId());
        if (connection == null && account != null && account.getExternalAccountId() != null) {
            connection = connections.selectByClientAndExternalAccountId(row.getOpenClientId(), account.getExternalAccountId());
        }
        if (connection != null && !"AUTHORIZED".equals(connection.getAuthStatus())) {
            throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
        }
        // Leftover social-table OpenAPI tasks used workbench tokens and bypassed dedicated revoke.
        // Do not execute or auto-cancel them; clients should retry with mediaId + connectionId.
        throw TkOpenApiException.conflict("X_LEGACY_OPEN_X_TASK_UNSUPPORTED",
                "Legacy social-table X OpenAPI tasks are not executed after client isolation");
    }

    public void prepareMedia(TkSocialPublishTaskDO task) {
        throw TkOpenApiException.badRequest("X_GENERATION_TASK_UNSUPPORTED",
                "generationTaskId is not supported; upload media and pass mediaId");
    }

    public boolean isClientRunnable(String clientId) {
        if (!access.isEnabled() || clientId == null || clientId.isEmpty()) return false;
        TkOpenApiClientDO client = clients.selectByClientId(clientId);
        return client != null && Integer.valueOf(0).equals(client.getStatus())
                && new TkOpenApiPrincipal(client.getClientId(), client.getClientName(), client.getPermissions())
                .hasPermission("x.publish");
    }

    private Map<String, Object> connectionResult(TkOpenXConnectionDO row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectionId", row.getConnectionId());
        result.put("externalAccountId", row.getExternalAccountId());
        result.put("displayName", row.getDisplayName());
        result.put("username", row.getUsername());
        result.put("status", row.getAuthStatus());
        result.put("endUserId", row.getEndUserId());
        return result;
    }

    private Map<String, Object> taskResult(TkOpenXPublishTaskDO task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getTaskId());
        result.put("connectionId", task.getConnectionId());
        result.put("mediaId", task.getMediaId());
        result.put("status", task.getStatus());
        result.put("failCode", task.getFailCode());
        result.put("failReason", task.getFailReason());
        result.put("tweetId", task.getTweetId());
        result.put("publishUrl", task.getPublishUrl());
        return result;
    }
}