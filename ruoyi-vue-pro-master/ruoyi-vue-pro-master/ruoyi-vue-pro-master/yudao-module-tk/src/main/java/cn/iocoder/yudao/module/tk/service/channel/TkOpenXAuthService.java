package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXReturnTicketDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXTicketService;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXReturnUrlValidator;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXCallbackOutcome;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TkOpenXAuthService {
    private final TkOpenXAccess access;
    private final TkOpenXAuthSessionMapper sessions;
    private final TkOpenXConnectionMapper connections;
    private final TkOpenApiClientMapper clients;
    private final TkChannelProperties channels;
    private final TkChannelOAuthClient oauth;
    private final TkSocialTokenCipher cipher;
    private final TkOpenXTicketService tickets;
    private final TkOpenXTokenService tokens;
    private final TkOpenApiCallbackService callbacks;
    private final PlatformTransactionManager transactions;
    private final SecureRandom random = new SecureRandom();

    public Map<String, Object> createSession(String endUserId, String returnUrl, String clientState) {
        String clientId = access.requireClient("x.auth");
        if (!channelReady()) {
            throw TkOpenApiException.unavailable("X_NOT_CONFIGURED", "X publishing is not configured");
        }
        String normalizedReturn = null;
        if (returnUrl != null && !returnUrl.trim().isEmpty()) {
            URI allowed = TkOpenXReturnUrlValidator.requireWhitelisted(returnUrl, whitelist(clientId),
                    access.isAllowInsecureLoopbackReturnUrl());
            normalizedReturn = TkOpenXReturnUrlValidator.key(allowed);
        }
        if (clientState != null && !clientState.isEmpty() && !clientState.matches("[A-Za-z0-9._~-]{1,256}")) {
            throw TkOpenApiException.badRequest("CLIENT_STATE_INVALID", "clientState is invalid");
        }
        if (endUserId != null && !endUserId.isEmpty() && !endUserId.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw TkOpenApiException.badRequest("END_USER_INVALID", "endUser is invalid");
        }
        String sessionId = secret();
        TkOpenXAuthSessionDO row = TkOpenXAuthSessionDO.builder()
                .authSessionId(sessionId)
                .clientId(clientId)
                .returnUrl(normalizedReturn)
                .clientState(emptyToNull(clientState))
                .endUserId(emptyToNull(endUserId))
                .status("CONSENT_PENDING")
                .expireTime(LocalDateTime.now().plusMinutes(10))
                .build();
        sessions.insert(row);
        Map<String, Object> result = sessionResult(row);
        result.put("authorizeUrl", consentUrl(sessionId));
        return result;
    }

    public Map<String, Object> session(String sessionId) {
        String clientId = access.requireClient("x.auth");
        TkOpenXAuthSessionDO row = sessions.selectByClientAndSessionId(clientId, requireSessionId(sessionId));
        if (row == null) throw missingSession();
        return sessionResult(row);
    }

    public String consentClient(String sessionId) {
        TkOpenXAuthSessionDO row = requireConsentSession(sessionId);
        return "Authorize " + row.getClientId() + " to publish videos to an X account until access is revoked.";
    }

    public URI consent(String sessionId, String browserToken, String formToken) {
        if (browserToken == null || !browserToken.matches("[0-9a-f]{64}") || !browserToken.equals(formToken)) {
            throw missingSession();
        }
        if (!channelReady()) {
            throw TkOpenApiException.unavailable("X_NOT_CONFIGURED", "X publishing is not configured");
        }
        return new TransactionTemplate(transactions).execute(status -> {
            TkOpenXAuthSessionDO row = requireConsentSession(sessionId);
            TkOpenXAuthSessionDO locked = sessions.selectByClientAndSessionIdForUpdate(row.getClientId(), row.getAuthSessionId());
            if (locked == null || !"CONSENT_PENDING".equals(locked.getStatus())
                    || locked.getExpireTime().isBefore(LocalDateTime.now())) {
                throw missingSession();
            }
            String state = secret();
            String verifier = secret();
            locked.setOauthStateHash(TkSocialAuthService.hash(state));
            locked.setConsentHash(TkSocialAuthService.hash(browserToken));
            locked.setCodeVerifierCipher(cipher.encrypt(verifier, sessionContext(locked)));
            locked.setStatus("PENDING");
            locked.setAuthorizeUrl(oauth.authorization(TkChannelProperties.X,
                    channels.require(TkChannelProperties.X), state, verifier));
            sessions.updateById(locked);
            return URI.create(locked.getAuthorizeUrl());
        });
    }

    public TkOpenXCallbackOutcome handleOfficialCallback(String channel, String code, String state, String error,
                                                         String browserToken) {
        if (!TkChannelProperties.X.equalsIgnoreCase(channel) || state == null || !state.matches("[A-Za-z0-9_-]{43}")) {
            return TkOpenXCallbackOutcome.unhandled();
        }
        return new TransactionTemplate(transactions).execute(status -> {
            TkOpenXAuthSessionDO row = sessions.selectByOauthStateHashForUpdate(TkSocialAuthService.hash(state));
            if (row == null) return TkOpenXCallbackOutcome.unhandled();
            return finishCallback(row, code, error, browserToken, false);
        });
    }

    public Map<String, Object> redeemTicket(String rawTicket) {
        String clientId = access.requireClient("x.auth");
        TkOpenXReturnTicketDO ticket = tickets.redeem(clientId, rawTicket, TkOpenXTicketService.PURPOSE_AUTH_RESULT);
        TkOpenXAuthSessionDO row = sessions.selectByClientAndSessionId(clientId, ticket.getAuthSessionId());
        if (row == null) throw TkOpenApiException.notFound("TICKET_NOT_FOUND", "ticket does not exist");
        Map<String, Object> result = sessionResult(row);
        result.put("clientState", row.getClientState());
        return result;
    }

    public void assertClientPermission(String clientId, String permission) {
        if (!access.isEnabled()) {
            throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        }
        TkOpenApiClientDO client = clients.selectByClientId(clientId);
        if (client == null || !Integer.valueOf(0).equals(client.getStatus())) {
            throw TkOpenApiException.unauthorized("X_CLIENT_UNAUTHORIZED", "X OpenAPI client is not authenticated");
        }
        if (permission != null && !new cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal(
                client.getClientId(), client.getClientName(), client.getPermissions()).hasPermission(permission)) {
            throw TkOpenApiException.forbidden("X_PERMISSION_DENIED", "client is not granted " + permission);
        }
    }
    public void importAuthorized(String clientId, String endUserId, String externalId, String displayName,
                                 String username, String access, String refresh, LocalDateTime expiresAt) {
        upsertConnection(clientId, endUserId, externalId, displayName, username, access, refresh, expiresAt, null, null);
    }

    private TkOpenXCallbackOutcome finishCallback(TkOpenXAuthSessionDO row, String code, String error,
                                                  String browserToken, boolean skipConsentCookie) {
        if (row.getExpireTime() != null && row.getExpireTime().isBefore(LocalDateTime.now())
                && "PENDING".equals(row.getStatus())) {
            fail(row, "AUTH_EXPIRED");
            return outcome(row, false);
        }
        if ("SUCCESS".equals(row.getStatus()) || "FAILED".equals(row.getStatus())) {
            return outcome(row, "SUCCESS".equals(row.getStatus()));
        }
        if (!"PENDING".equals(row.getStatus())) {
            return outcome(row, false);
        }
        if (!skipConsentCookie && (browserToken == null || !browserToken.matches("[0-9a-f]{64}")
                || row.getConsentHash() == null
                || !TkSocialAuthService.hash(browserToken).equals(row.getConsentHash()))) {
            fail(row, "CONSENT_MISMATCH");
            return outcome(row, false);
        }
        row.setStatus("PROCESSING");
        row.setConsentHash(null);
        sessions.updateById(row);
        try {
            if (error != null || code == null || code.isEmpty() || code.length() > 4096) {
                throw new IllegalArgumentException("denied");
            }
            if (!channelReady()) throw new IllegalStateException("not configured");
            String verifier = cipher.decrypt(row.getCodeVerifierCipher(), sessionContext(row));
            JsonNode token = oauth.exchange(TkChannelProperties.X, channels.require(TkChannelProperties.X), code, verifier);
            String accessToken = required(token, "access_token");
            String refreshToken = required(token, "refresh_token");
            JsonNode profile = oauth.account(TkChannelProperties.X, accessToken);
            String external = required(profile, "id");
            if (!external.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("profile");
            String name = truncate(profile.path("name").asText(external));
            String username = truncate(profile.path("username").asText());
            TkOpenXConnectionDO connection = upsertConnection(row.getClientId(), row.getEndUserId(), external, name,
                    username, accessToken, refreshToken, expires(token), row.getId(), null);
            row.setStatus("SUCCESS");
            row.setConnectionId(connection.getConnectionId());
            row.setFailReason(null);
            row.setCodeVerifierCipher(null);
            sessions.updateById(row);
            enqueue(row, connection, "authorization.completed", "CONNECTION", connection.getConnectionId(), "AUTHORIZED");
            return outcome(row, true);
        } catch (RuntimeException ex) {
            log.warn("X OpenAPI OAuth callback failed: reason={}", sanitize(ex));
            fail(row, sanitize(ex));
            return outcome(row, false);
        }
    }

    private TkOpenXConnectionDO upsertConnection(String clientId, String endUserId, String externalId, String displayName,
                                                 String username, String access, String refresh, LocalDateTime expiresAt,
                                                 Long sourceSessionId, Long sourceAccountId) {
        TkOpenXConnectionDO existing = connections.selectByClientAndExternalAccountIdForUpdate(clientId, externalId);
        if (existing == null) {
            existing = TkOpenXConnectionDO.builder()
                    .connectionId(TkOpenApiIds.next("xconn"))
                    .clientId(clientId)
                    .externalAccountId(externalId)
                    .build();
        }
        existing.setDisplayName(displayName);
        existing.setUsername(username);
        existing.setAuthStatus("AUTHORIZED");
        existing.setEndUserId(emptyToNull(endUserId));
        existing.setFailReason(null);
        existing.setLastAuthTime(LocalDateTime.now());
        if (sourceSessionId != null) existing.setSourceSocialSessionId(sourceSessionId);
        if (sourceAccountId != null) existing.setSourceSocialAccountId(sourceAccountId);
        if (existing.getId() == null) connections.insert(existing);
        tokens.store(existing, access, refresh, expiresAt);
        return existing;
    }

    private TkOpenXCallbackOutcome outcome(TkOpenXAuthSessionDO row, boolean success) {
        if (row.getReturnUrl() == null || row.getReturnUrl().isEmpty()) {
            return TkOpenXCallbackOutcome.page(success);
        }
        try {
            URI allowed = TkOpenXReturnUrlValidator.requireWhitelisted(row.getReturnUrl(), whitelist(row.getClientId()),
                    access.isAllowInsecureLoopbackReturnUrl());
            LocalDateTime expire = LocalDateTime.now().plusMinutes(10);
            String ticket = tickets.issueOrReuse(row.getClientId(), row.getAuthSessionId(),
                    TkOpenXTicketService.PURPOSE_AUTH_RESULT, expire);
            row.setTicketHash(TkSocialAuthService.hash(ticket));
            sessions.updateById(row);
            return TkOpenXCallbackOutcome.redirect(success,
                    TkOpenXReturnUrlValidator.redirect(allowed, TkOpenXReturnUrlValidator.ticketParams(ticket, success)));
        } catch (RuntimeException ex) {
            log.warn("X OpenAPI returnUrl redirect skipped: reason=RETURN_URL_NOT_ALLOWED");
            return TkOpenXCallbackOutcome.page(success);
        }
    }

    private void fail(TkOpenXAuthSessionDO row, String reason) {
        row.setStatus("FAILED");
        row.setFailReason(reason);
        row.setCodeVerifierCipher(null);
        row.setConsentHash(null);
        sessions.updateById(row);
        enqueue(row, null, "authorization.failed", "AUTH_SESSION", row.getAuthSessionId(), "FAILED");
    }

    private void enqueue(TkOpenXAuthSessionDO row, TkOpenXConnectionDO connection, String event, String resourceType,
                         String resourceId, String status) {
        try {
            callbacks.enqueueOnce(row.getClientId(), event, resourceType, resourceId, authPayload(row, connection, status));
        } catch (RuntimeException ex) {
            log.warn("X OpenAPI auth callback enqueue skipped: event={}", event);
        }
    }

    private Map<String, Object> authPayload(TkOpenXAuthSessionDO row, TkOpenXConnectionDO connection, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authSessionId", row.getAuthSessionId());
        payload.put("connectionId", connection == null ? row.getConnectionId() : connection.getConnectionId());
        payload.put("externalAccountId", connection == null ? null : connection.getExternalAccountId());
        payload.put("accountName", connection == null ? null : connection.getDisplayName());
        payload.put("username", connection == null ? null : connection.getUsername());
        payload.put("status", status);
        payload.put("endUserId", row.getEndUserId());
        return payload;
    }

    public List<String> whitelist(String clientId) {
        TkOpenApiClientDO client = clients.selectByClientId(clientId);
        if (client == null || client.getReturnUrlWhitelist() == null || client.getReturnUrlWhitelist().trim().isEmpty()) {
            return new ArrayList<>();
        }
        String raw = client.getReturnUrlWhitelist().trim();
        try {
            if (raw.startsWith("[")) {
                return TkChannelHttp.JSON.readValue(raw, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
            throw TkOpenApiException.badRequest("RETURN_URL_INVALID", "returnUrl whitelist is invalid");
        }
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) lines.add(line.trim());
        }
        return lines;
    }

    private Map<String, Object> sessionResult(TkOpenXAuthSessionDO row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authSessionId", row.getAuthSessionId());
        result.put("status", row.getStatus());
        result.put("connectionId", row.getConnectionId());
        result.put("failReason", row.getFailReason());
        result.put("expireTime", row.getExpireTime());
        result.put("endUserId", row.getEndUserId());
        return result;
    }

    private String consentUrl(String sessionId) {
        String redirect = channels.getX() == null ? null : channels.getX().getRedirectUri();
        if (redirect == null || redirect.isEmpty()) {
            throw TkOpenApiException.unavailable("X_NOT_CONFIGURED", "X publishing is not configured");
        }
        return URI.create(redirect).resolve("/admin-api/tk/open/v1/x/auth/sessions/" + sessionId + "/consent").toString();
    }

    private TkOpenXAuthSessionDO requireConsentSession(String sessionId) {
        TkOpenXAuthSessionDO row = sessions.selectByAuthSessionId(requireSessionId(sessionId));
        if (row == null || !"CONSENT_PENDING".equals(row.getStatus())
                || row.getExpireTime() == null || !row.getExpireTime().isAfter(LocalDateTime.now())) {
            throw missingSession();
        }
        if (!access.isEnabled()) {
            throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        }
        return row;
    }

    private boolean channelReady() {
        try {
            return channels.ready(TkChannelProperties.X);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String sessionContext(TkOpenXAuthSessionDO row) {
        return "open-x:session:" + row.getClientId() + ":" + row.getAuthSessionId();
    }

    private static String requireSessionId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{43}")) throw missingSession();
        return id;
    }

    private static TkOpenApiException missingSession() {
        return TkOpenApiException.notFound("AUTH_SESSION_NOT_FOUND", "authorization session does not exist");
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("missing field");
        return value;
    }

    private static LocalDateTime expires(JsonNode token) {
        int seconds = token.path("expires_in").asInt();
        if (seconds <= 0) throw new IllegalArgumentException("Missing token expiration");
        return LocalDateTime.now().plusSeconds(seconds);
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String sanitize(RuntimeException ex) {
        if (ex instanceof TkSocialPlatformException) {
            String code = ((TkSocialPlatformException) ex).getCode();
            if (code != null && code.matches("CHANNEL_HTTP_[1-5][0-9]{2}")) return code;
            return "PLATFORM_ERROR";
        }
        if (ex instanceof IllegalArgumentException) return "INVALID_INPUT_OR_RESPONSE";
        if (ex instanceof IllegalStateException) return "INVALID_STATE_OR_CONFIGURATION";
        return "INTERNAL_ERROR";
    }

    private String secret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
