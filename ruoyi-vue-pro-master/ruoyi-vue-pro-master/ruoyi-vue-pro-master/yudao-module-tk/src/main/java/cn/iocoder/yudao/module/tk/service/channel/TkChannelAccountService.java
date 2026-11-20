package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TkChannelAccountService {
    private final TkChannelProperties properties;
    private final TkChannelOAuthClient oauth;
    private final TkSocialAccountMapper accounts;
    private final TkSocialAuthSessionMapper sessions;
    private final TkDataScopeService scope;
    private final TkSocialTokenCipher cipher;
    private final PlatformTransactionManager transactions;
    private final TkOpenXAccess openAccess;
    private final SecureRandom random = new SecureRandom();

    public boolean ready(String channel) {
        if (!properties.ready(channel)) return false;
        try { cipher.validateKey(); return true; }
        catch (IllegalStateException ex) { return false; }
    }

    public Map<String, String> redirect(String channel) {
        TkChannelProperties.Credentials cfg = properties.require(channel);
        cipher.validateKey();
        TkUserScope user = owner();
        String state = secret(), verifier = secret();
        TkSocialAuthSessionDO session = new TkSocialAuthSessionDO();
        session.setSessionId(secret());
        session.setStateHash(TkSocialAuthService.hash(state));
        session.setTenantId(user.getTenantId());
        session.setCompanyId(company());
        session.setCreator(user.getUserIdString());
        session.setPlatform(channel);
        session.setStatus("PENDING");
        session.setExpireTime(LocalDateTime.now().plusMinutes(10));
        session.setPayloadCiphertext(cipher.encrypt(verifier, TkSocialAuthService.sessionContext(session)));
        String url = oauth.authorization(channel, cfg, state, verifier);
        sessions.insert(session);
        return TkChannelOAuthClient.params("authorizeUrl", url, "sessionId", session.getSessionId());
    }

    public TkSocialAuthSessionDO openSession(TkOpenXProperties.Grant grant, String endUserId, Long ownerUserId) {
        if (!ready(TkChannelProperties.X)) throw TkOpenApiException.unavailable("X_NOT_CONFIGURED", "X publishing is not configured");
        TkSocialAuthSessionDO row = new TkSocialAuthSessionDO();
        row.setSessionId(secret());
        row.setStateHash(TkSocialAuthService.hash(secret()));
        row.setTenantId(grant.getTenantId());
        row.setCompanyId(grant.getCompanyId());
        row.setCreator(String.valueOf(ownerUserId));
        row.setPlatform(TkChannelProperties.X);
        row.setOpenClientId(grant.getClientId());
        row.setOpenEndUserId(endUserId);
        row.setStatus("CONSENT_PENDING");
        row.setExpireTime(LocalDateTime.now().plusMinutes(10));
        sessions.insert(row);
        return row;
    }

    public String openConsent(TkSocialAuthSessionDO row, String browserToken) {
        openAccess.requireSessionGrant(row, "x.auth");
        String state = secret(), verifier = secret();
        String url = oauth.authorization(TkChannelProperties.X, properties.require(TkChannelProperties.X), state, verifier);
        int updated = sessions.update(null, new UpdateWrapper<TkSocialAuthSessionDO>()
                .eq("id", row.getId()).eq("tenant_id", row.getTenantId()).eq("status", "CONSENT_PENDING")
                .gt("expire_time", LocalDateTime.now()).set("status", "PENDING")
                .set("state_hash", TkSocialAuthService.hash(state))
                .set("open_consent_hash", TkSocialAuthService.hash(browserToken))
                .set("payload_ciphertext", cipher.encrypt(verifier, TkSocialAuthService.sessionContext(row))));
        if (updated != 1) throw TkOpenApiException.conflict("X_CONSENT_NOT_PENDING", "Authorization link is no longer available");
        return url;
    }

    public boolean callback(String channel, String code, String state, String error, String browserToken) {
        if (!ready(channel) || state == null || !state.matches("[A-Za-z0-9_-]{43}")) return false;
        TkSocialAuthSessionDO session = sessions.findByStateHash(TkSocialAuthService.hash(state));
        if (session == null || !channel.equals(session.getPlatform()) || session.getTenantId() == null) return false;
        if (session.getOpenClientId() != null && (browserToken == null || !browserToken.matches("[0-9a-f]{64}")
                || !TkSocialAuthService.hash(browserToken).equals(session.getOpenConsentHash()))) return false;
        return TenantUtils.execute(session.getTenantId(), () -> {
            if (sessions.claim(session.getId(), "PENDING", "PROCESSING", LocalDateTime.now()) != 1) return false;
            String stage = "CALLBACK_PARAMS";
            try {
                if (error != null || code == null || code.length() > 4096 || code.isEmpty()) throw new IllegalArgumentException();
                stage = "DECRYPT_VERIFIER";
                String verifier = cipher.decrypt(session.getPayloadCiphertext(), TkSocialAuthService.sessionContext(session));
                stage = "EXCHANGE_TOKEN";
                JsonNode token = oauth.exchange(channel, properties.require(channel), code, verifier);
                String access = required(token, "access_token"), refresh = required(token, "refresh_token");
                stage = "READ_ACCOUNT";
                JsonNode profile = oauth.account(channel, access);
                String external = required(profile, "id");
                if (!external.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException();
                if (session.getOpenClientId() != null) {
                    TkOpenXProperties.Grant grant = openAccess.requireSessionGrant(session, "x.auth");
                    if (!TkChannelProperties.X.equals(channel)
                            || (grant.isPilot() && !external.equals(grant.getXUserId()))) {
                        throw new IllegalArgumentException("Unexpected X account");
                    }
                    rejectEndUserAccountReuse(session, external);
                }
                stage = "SAVE_ACCOUNT";
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    TkSocialAccountDO account = accounts.findExternal(session.getTenantId(), channel, external);
                    boolean existing = account != null;
                    if (existing && (!Objects.equals(account.getCompanyId(), session.getCompanyId())
                            || !Objects.equals(account.getCreator(), session.getCreator()))) throw new IllegalArgumentException();
                    if (!existing) {
                        account = new TkSocialAccountDO();
                        account.setTenantId(session.getTenantId()); account.setCompanyId(session.getCompanyId());
                        account.setCreator(session.getCreator()); account.setPlatform(channel); account.setExternalAccountId(external);
                    } else {
                        account = lock(account.getId(), session.getTenantId());
                        if (account == null) throw new IllegalArgumentException();
                    }
                    boolean x = TkChannelProperties.X.equals(channel);
                    account.setAccountType(x ? "X_USER" : "YOUTUBE_CHANNEL");
                    account.setAccountName(truncate(x ? profile.path("name").asText(external) : profile.path("snippet").path("title").asText(external)));
                    account.setUsername(x ? truncate(profile.path("username").asText()) : null);
                    account.setAccessTokenCiphertext(encrypt(account, access, refresh));
                    account.setTokenType("OAUTH2"); account.setTokenExpiresAt(expires(token));
                    account.setStatus("AUTHORIZED"); account.setFailReason(null); account.setLastAuthTime(LocalDateTime.now());
                    if (existing) accounts.updateById(account); else accounts.insert(account);
                    if (session.getOpenClientId() != null) {
                        sessions.update(null, new UpdateWrapper<TkSocialAuthSessionDO>().eq("id", session.getId())
                                .eq("tenant_id", session.getTenantId()).set("open_account_id", account.getId())
                                .set("open_consent_hash", null));
                    }
                    if (sessions.finish(session.getId(), "PROCESSING", "SUCCESS", null, null, LocalDateTime.now()) != 1)
                        throw new IllegalStateException("OAuth session expired");
                });
                return true;
            } catch (RuntimeException ex) {
                if (TkChannelProperties.X.equals(channel)) {
                    log.warn("X OAuth callback failed: stage={}, reason={}", stage, callbackFailureReason(ex));
                }
                sessions.finish(session.getId(), "PROCESSING", "FAILED", null, "Authorization failed", LocalDateTime.now());
                return false;
            }
        });
    }

    private static String callbackFailureReason(RuntimeException ex) {
        if (ex instanceof TkSocialPlatformException) {
            String code = ((TkSocialPlatformException) ex).getCode();
            if ("CHANNEL_HTTP_0".equals(code)) return "TRANSPORT_OR_RESPONSE_ERROR";
            if (code != null && code.matches("CHANNEL_HTTP_[1-5][0-9]{2}")) return code;
            return "PLATFORM_ERROR";
        }
        if (ex instanceof IllegalArgumentException) return "INVALID_INPUT_OR_RESPONSE";
        if (ex instanceof IllegalStateException) return "INVALID_STATE_OR_CONFIGURATION";
        return "INTERNAL_ERROR";
    }

    public String session(String channel, String sessionId) {
        TkUserScope user = owner();
        TkSocialAuthSessionDO row = sessions.findBySessionId(sessionId);
        if (row == null || !channel.equals(row.getPlatform()) || !Objects.equals(row.getTenantId(), user.getTenantId())
                || !Objects.equals(row.getCompanyId(), company()) || !Objects.equals(row.getCreator(), user.getUserIdString()))
            throw new IllegalArgumentException("OAuth session not found");
        if (!row.getExpireTime().isAfter(LocalDateTime.now()) && !"SUCCESS".equals(row.getStatus())) return "EXPIRED";
        return row.getStatus();
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void expireSessions() {
        if (properties.getX().isEnabled() || properties.getYoutube().isEnabled()) sessions.expire(LocalDateTime.now());
    }

    public List<Map<String, String>> list(String channel) {
        TkUserScope user = owner();
        return accounts.selectList(new QueryWrapper<TkSocialAccountDO>().eq("tenant_id", user.getTenantId())
                        .eq("company_id", company()).eq("platform", channel).eq("creator", user.getUserIdString())
                        .ne("status", "DELETED").orderByDesc("id"))
                .stream().map(a -> TkChannelOAuthClient.params("id", String.valueOf(a.getId()), "name", a.getAccountName(),
                        "username", a.getUsername(), "status", a.getStatus())).collect(Collectors.toList());
    }

    public TkSocialAccountDO owned(String channel, Long id) {
        TkUserScope user = owner();
        TkSocialAccountDO a = accounts.selectById(id);
        if (a == null || !channel.equals(a.getPlatform()) || !Objects.equals(user.getTenantId(), a.getTenantId())
                || !Objects.equals(company(), a.getCompanyId()) || !Objects.equals(user.getUserIdString(), a.getCreator()))
            throw new IllegalArgumentException("Account not found");
        return a;
    }

    public void disconnect(String channel, Long id) {
        TkSocialAccountDO a = owned(channel, id);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            accounts.update(null, new UpdateWrapper<TkSocialAccountDO>().eq("id", id).eq("tenant_id", a.getTenantId())
                    .set("status", "DELETED").set("access_token_ciphertext", null).set("token_expires_at", null));
            if (TkChannelProperties.X.equals(channel)) {
                sessions.update(null, new UpdateWrapper<TkSocialAuthSessionDO>().eq("tenant_id", a.getTenantId())
                        .eq("platform", channel).eq("open_account_id", id).eq("status", "SUCCESS").set("status", "REVOKED"));
            }
        });
    }

    public String token(TkSocialAccountDO requested) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(requested.getTenantId(), TenantContextHolder.getTenantId()))
            throw new IllegalStateException("Invalid tenant context");
        TkChannelProperties.Credentials cfg = properties.require(requested.getPlatform());
        // Serialize rotating refresh tokens across workers and application instances.
        String access = new TransactionTemplate(transactions).execute(status -> {
            TkSocialAccountDO account = lock(requested.getId(), requested.getTenantId());
            if (account == null || !"AUTHORIZED".equals(account.getStatus())) return null;
            JsonNode current;
            try { current = TkChannelHttp.JSON.readTree(cipher.decrypt(account.getAccessTokenCiphertext(), context(account))); }
            catch (Exception ex) { markReauth(account); return null; }
            if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(2)))
                return required(current, "access");
            try {
                String refresh = required(current, "refresh");
                JsonNode renewed = oauth.refresh(account.getPlatform(), cfg, refresh);
                String replacement = required(renewed, "access_token");
                account.setAccessTokenCiphertext(encrypt(account, replacement, renewed.path("refresh_token").asText(refresh)));
                account.setTokenExpiresAt(expires(renewed));
                accounts.updateById(account);
                return replacement;
            } catch (TkSocialPlatformException ex) {
                if (ex.isRetryable()) throw ex;
                markReauth(account); return null;
            }
        });
        if (access == null) throw TkChannelHttp.failure(401, false);
        return access;
    }

    public void rejected(TkSocialAccountDO account, String token) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            TkSocialAccountDO latest = lock(account.getId(), account.getTenantId());
            if (latest == null || !"AUTHORIZED".equals(latest.getStatus())) return;
            try {
                JsonNode stored = TkChannelHttp.JSON.readTree(cipher.decrypt(latest.getAccessTokenCiphertext(), context(latest)));
                if (token.equals(stored.path("access").asText())) markReauth(latest);
            } catch (java.io.IOException ignored) { markReauth(latest); }
        });
    }

    private TkSocialAccountDO lock(Long id, Long tenant) {
        return accounts.selectOne(new QueryWrapper<TkSocialAccountDO>().eq("id", id).eq("tenant_id", tenant).last("FOR UPDATE"));
    }
    private void markReauth(TkSocialAccountDO account) {
        accounts.markReauth(account.getId(), account.getTenantId(), account.getAccessTokenCiphertext(), "Authorization expired", LocalDateTime.now());
    }
    private static String context(TkSocialAccountDO a) {
        return "channel:account:" + a.getTenantId() + ":" + a.getCompanyId() + ":" + a.getPlatform() + ":" + a.getExternalAccountId();
    }
    private String encrypt(TkSocialAccountDO a, String access, String refresh) {
        ObjectNode payload = TkChannelHttp.JSON.createObjectNode().put("access", access).put("refresh", refresh);
        return cipher.encrypt(payload.toString(), context(a));
    }
    private String secret() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static LocalDateTime expires(JsonNode token) {
        int seconds = token.path("expires_in").asInt();
        if (seconds <= 0) throw new IllegalArgumentException("Missing token expiration");
        return LocalDateTime.now().plusSeconds(seconds);
    }
    private static String required(JsonNode node, String key) {
        String value = node.path(key).asText();
        if (value.isEmpty() || value.length() > 8192) throw new IllegalArgumentException("Invalid authorization response");
        return value;
    }
    private static String truncate(String value) { return value.substring(0, Math.min(value.length(), 128)); }
    private TkUserScope owner() {
        TkUserScope current = scope.getCurrentScope();
        if (!current.hasTenantScope() || current.getUserId() == null) throw new IllegalArgumentException("Select a tenant first");
        return current;
    }
    private Long company() {
        Long company = scope.getWritableCompanyId(null);
        if (company == null) throw new IllegalArgumentException("Select a company first");
        return company;
    }

    private void rejectEndUserAccountReuse(TkSocialAuthSessionDO session, String externalAccountId) {
        if (externalAccountId == null) {
            return;
        }
        List<TkSocialAuthSessionDO> rows = sessions.selectList(new QueryWrapper<TkSocialAuthSessionDO>()
                .eq("tenant_id", session.getTenantId())
                .eq("platform", TkChannelProperties.X)
                .eq("status", "SUCCESS")
                .isNotNull("open_account_id"));
        for (TkSocialAuthSessionDO row : rows) {
            if (row.getOpenAccountId() == null || Objects.equals(row.getId(), session.getId())) {
                continue;
            }
            TkSocialAccountDO other = accounts.selectById(row.getOpenAccountId());
            if (other == null || !externalAccountId.equals(other.getExternalAccountId())) {
                continue;
            }
            if (!Objects.equals(row.getOpenEndUserId(), session.getOpenEndUserId())
                    || !Objects.equals(row.getOpenClientId(), session.getOpenClientId())) {
                throw new IllegalArgumentException("X account already bound");
            }
        }
    }
}
