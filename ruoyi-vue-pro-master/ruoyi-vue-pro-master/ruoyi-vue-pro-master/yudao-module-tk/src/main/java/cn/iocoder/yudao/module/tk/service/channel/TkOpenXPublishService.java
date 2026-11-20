package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.tk.service.channel.TkChannelProperties.X;

@Service
@RequiredArgsConstructor
public class TkOpenXPublishService {
    private final TkOpenXAccess access;
    private final TkChannelAccountService channelAccounts;
    private final TkChannelProperties channels;
    private final TkSocialAuthSessionMapper sessions;
    private final TkSocialAccountMapper accounts;
    private final TkSocialPublishTaskMapper tasks;
    private final TkSocialPublishDetailMapper details;
    private final TkGenerationTaskMapper generations;
    private final TkSocialMediaService media;
    private final PlatformTransactionManager transactions;

    public Map<String, Object> createSession() {
        TkOpenXProperties.Grant grant = grant();
        TkSocialAuthSessionDO row = inTenant(grant.getTenantId(), () -> channelAccounts.openSession(grant));
        String url = URI.create(channels.getX().getRedirectUri()).resolve(
                "/admin-api/tk/open/v1/x/auth/sessions/" + row.getSessionId() + "/consent").toString();
        Map<String, Object> result = sessionResult(row);
        result.put("authorizeUrl", url);
        return result;
    }

    public Map<String, Object> session(String id) {
        TkOpenXProperties.Grant grant = grant();
        return inTenant(grant.getTenantId(), () -> sessionResult(ownedSession(grant, id, false)));
    }

    public String consentClient(String id) {
        TkSocialAuthSessionDO row = consentSession(id);
        return "Authorize " + row.getOpenClientId() + " to publish videos to the designated X account until access is revoked.";
    }

    public URI consent(String id, String browserToken, String formToken) {
        if (browserToken == null || !browserToken.matches("[0-9a-f]{64}") || !browserToken.equals(formToken)) throw missing();
        TkSocialAuthSessionDO row = consentSession(id);
        return inTenant(row.getTenantId(), () -> URI.create(channelAccounts.openConsent(row, browserToken)));
    }

    private TkSocialAuthSessionDO consentSession(String id) {
        checkSessionId(id);
        TkSocialAuthSessionDO row = sessions.findOpenXConsent(id);
        if (row == null || !"CONSENT_PENDING".equals(row.getStatus()) || !row.getExpireTime().isAfter(LocalDateTime.now())) {
            throw missing();
        }
        access.requireSessionGrant(row, "x.auth");
        return row;
    }

    public boolean revoke(String id) {
        TkOpenXProperties.Grant grant = grant();
        return inTenant(grant.getTenantId(), () -> {
            TkSocialAuthSessionDO row = ownedSession(grant, id, false);
            sessions.update(null, new UpdateWrapper<TkSocialAuthSessionDO>().eq("id", row.getId())
                    .eq("tenant_id", grant.getTenantId()).eq("status", "SUCCESS").set("status", "REVOKED"));
            return true;
        });
    }

    public Map<String, Object> create(String connectionId, Long generationId, String text, String key) {
        TkOpenXProperties.Grant grant = grant();
        checkSessionId(connectionId);
        if (generationId == null || generationId <= 0 || text == null || text.length() > 280 || key == null
                || !key.matches("[!-~]{1,128}")) {
            throw TkOpenApiException.badRequest("X_PUBLISH_INPUT_INVALID", "Invalid generationTaskId, text or Idempotency-Key");
        }
        String storageKey = "xopen_" + TkSocialAuthService.hash(grant.getClientId() + "\n" + key);
        String hash = TkSocialAuthService.hash(connectionId + "\n" + generationId + "\n" + text);
        return inTenant(grant.getTenantId(), () -> {
            TkSocialPublishTaskDO existing = idempotent(grant, storageKey, hash);
            if (existing != null) return result(existing);
            if (!channelAccounts.ready(X)) throw TkOpenApiException.unavailable("X_NOT_CONFIGURED", "X publishing is not configured");
            TkSocialPublishTaskDO task;
            try {
                task = new TransactionTemplate(transactions).execute(status -> {
                    TkSocialAuthSessionDO row = ownedSession(grant, connectionId, true);
                    TkSocialAccountDO account = row.getOpenAccountId() == null ? null : accounts.selectById(row.getOpenAccountId());
                    if (!TkOpenXAccess.usable(row, account, grant)) throw connectionDisabled();
                    requireGeneration(generationId, row.getTenantId(), row.getCompanyId(), row.getCreator());
                    return insertTask(row, account, generationId, text, storageKey, hash);
                });
            } catch (DuplicateKeyException ex) {
                task = idempotent(grant, storageKey, hash);
                if (task == null) throw ex;
            }
            return result(task);
        });
    }

    public Map<String, Object> get(String id) {
        TkOpenXProperties.Grant grant = grant();
        if (!id.matches("xtask_[0-9a-f]{32}")) throw missing();
        return inTenant(grant.getTenantId(), () -> {
            TkSocialPublishTaskDO task = tasks.selectOne(new QueryWrapper<TkSocialPublishTaskDO>().eq("open_x_task_id", id)
                    .eq("tenant_id", grant.getTenantId()).eq("company_id", grant.getCompanyId()).eq("channel", X));
            TkSocialAuthSessionDO row = task == null || task.getOpenXConnectionId() == null
                    ? null : sessions.selectById(task.getOpenXConnectionId());
            if (row == null || !TkOpenXAccess.matches(row, grant)) throw missing();
            return result(task);
        });
    }

    public void assertRunnable(TkSocialPublishTaskDO task, TkSocialAccountDO account) {
        TkSocialAuthSessionDO row = sessions.selectById(task.getOpenXConnectionId());
        if (row == null || !Objects.equals(row.getTenantId(), task.getTenantId())
                || !Objects.equals(row.getCompanyId(), task.getCompanyId()) || !Objects.equals(row.getCreator(), task.getCreator())) {
            throw connectionDisabled();
        }
        TkOpenXProperties.Grant grant = access.requireSessionGrant(row, "x.publish");
        if (!TkOpenXAccess.usable(row, account, grant)) throw connectionDisabled();
    }

    public void prepareMedia(TkSocialPublishTaskDO task) {
        TkGenerationTaskDO generation = requireGeneration(task.getOpenXGenerationTaskId(), task.getTenantId(),
                task.getCompanyId(), task.getCreator());
        TkSocialMediaDO source = media.importGeneratedForOwner(generation, task.getTenantId(), task.getCompanyId(),
                task.getCreator(), TkChannelPublishService.maxUploadBytes(X));
        try {
            TkChannelPublishService.verifyVideo(X, source);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                media.lockReady(source.getId(), task.getTenantId());
                tasks.update(null, new UpdateWrapper<TkSocialPublishTaskDO>().eq("id", task.getId())
                        .eq("tenant_id", task.getTenantId()).isNull("media_id").set("media_id", source.getId()));
            });
        } finally {
            try { media.cleanupUnreferenced(source.getId(), task.getTenantId()); }
            catch (RuntimeException ignored) { /* The existing storage sweep will retry. */ }
        }
    }

    private TkSocialPublishTaskDO insertTask(TkSocialAuthSessionDO row, TkSocialAccountDO account, Long generationId,
            String text, String key, String hash) {
        TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
        task.setTenantId(row.getTenantId()); task.setCompanyId(row.getCompanyId()); task.setCreator(row.getCreator());
        task.setChannel(X); task.setChannelText(text); task.setTitle("generation-" + generationId + ".mp4");
        task.setOpenXTaskId("xtask_" + UUID.randomUUID().toString().replace("-", ""));
        task.setOpenXConnectionId(row.getId()); task.setOpenXGenerationTaskId(generationId);
        task.setIdempotencyKey(key); task.setRequestHash(hash);
        task.setTargetCount(1); task.setPendingCount(1); task.setSuccessCount(0); task.setFailedCount(0); task.setStatus("PENDING");
        tasks.insert(task);
        TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();
        detail.setTenantId(row.getTenantId()); detail.setCompanyId(row.getCompanyId()); detail.setCreator(row.getCreator());
        detail.setPublishTaskId(task.getId()); detail.setSocialAccountId(account.getId()); detail.setPlatform(X);
        detail.setAccountName(account.getAccountName()); detail.setStatus("PENDING"); detail.setRetryCount(0); detail.setPollCount(0);
        detail.setNextRetryTime(LocalDateTime.now()); details.insert(detail);
        return task;
    }

    private TkGenerationTaskDO requireGeneration(Long id, Long tenant, Long company, String creator) {
        TkGenerationTaskDO generation = generations.selectOne(new QueryWrapper<TkGenerationTaskDO>().eq("id", id)
                .eq("tenant_id", tenant).eq("company_id", company).eq("creator", creator).eq("status", "SUCCESS"));
        if (generation == null || generation.getOutputUrl() == null) {
            throw TkOpenApiException.notFound("X_VIDEO_NOT_AVAILABLE", "Authorized completed video not found");
        }
        return generation;
    }

    private TkSocialPublishTaskDO idempotent(TkOpenXProperties.Grant grant, String key, String hash) {
        TkSocialPublishTaskDO row = tasks.selectOne(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id", grant.getTenantId())
                .eq("creator", String.valueOf(grant.getOwnerUserId())).eq("idempotency_key", key));
        if (row == null) return null;
        TkSocialAuthSessionDO session = row.getOpenXConnectionId() == null ? null : sessions.selectById(row.getOpenXConnectionId());
        if (!hash.equals(row.getRequestHash()) || session == null || !TkOpenXAccess.matches(session, grant)) {
            throw TkOpenApiException.conflict("IDEMPOTENCY_KEY_CONFLICT", "Idempotency key is in use");
        }
        return row;
    }

    private Map<String, Object> result(TkSocialPublishTaskDO task) {
        TkSocialPublishDetailDO detail = details.selectOne(new QueryWrapper<TkSocialPublishDetailDO>()
                .eq("publish_task_id", task.getId()).eq("tenant_id", task.getTenantId()).eq("company_id", task.getCompanyId())
                .eq("platform", X));
        if (detail == null) throw missing();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getOpenXTaskId());
        out.put("status", "SUCCESS".equals(detail.getStatus()) ? "SUCCEEDED" : detail.getStatus());
        if (detail.getPublishUrl() != null) out.put("postUrl", detail.getPublishUrl());
        if (detail.getExternalPostId() != null) out.put("postId", detail.getExternalPostId());
        if (detail.getErrorCode() != null) out.put("errorCode", detail.getErrorCode());
        return out;
    }

    private TkSocialAuthSessionDO ownedSession(TkOpenXProperties.Grant grant, String id, boolean lock) {
        checkSessionId(id);
        QueryWrapper<TkSocialAuthSessionDO> query = new QueryWrapper<TkSocialAuthSessionDO>().eq("session_id", id)
                .eq("tenant_id", grant.getTenantId()).eq("open_client_id", grant.getClientId());
        if (lock) query.last("FOR UPDATE");
        TkSocialAuthSessionDO row = sessions.selectOne(query);
        if (row == null || !id.equals(row.getSessionId()) || !TkOpenXAccess.matches(row, grant)) throw missing();
        return row;
    }

    private static Map<String, Object> sessionResult(TkSocialAuthSessionDO row) {
        Map<String, Object> out = new LinkedHashMap<>();
        String status = row.getStatus();
        if (("CONSENT_PENDING".equals(status) || "PENDING".equals(status) || "PROCESSING".equals(status))
                && !row.getExpireTime().isAfter(LocalDateTime.now())) status = "EXPIRED";
        out.put("sessionId", row.getSessionId()); out.put("status", status);
        if ("SUCCESS".equals(status)) out.put("connectionId", row.getSessionId());
        return out;
    }

    private TkOpenXProperties.Grant grant() {
        if (TkOpenApiContext.getPrincipal() == null) {
            throw TkOpenApiException.unauthorized("OPEN_API_AUTH_HEADER_MISSING", "HMAC authentication is required");
        }
        return access.requireGrant(TkOpenApiContext.getRequiredPrincipal().getClientId());
    }

    private static void checkSessionId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{43}")) throw missing();
    }

    private static <T> T inTenant(Long tenantId, Supplier<T> operation) {
        AtomicReference<T> result = new AtomicReference<>();
        // The Callable overload wraps business exceptions and loses the API status code.
        TenantUtils.execute(tenantId, (Runnable) () -> result.set(operation.get()));
        return result.get();
    }

    private static TkOpenApiException missing() {
        return TkOpenApiException.notFound("X_RESOURCE_NOT_FOUND", "X resource not found");
    }

    private static TkOpenApiException connectionDisabled() {
        return TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
    }
}
