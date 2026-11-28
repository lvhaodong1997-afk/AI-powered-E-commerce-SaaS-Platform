package cn.iocoder.yudao.module.tk.service.open.meta;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TkOpenMetaAuth {
    static final String SCOPED = " WHERE id=? AND client_id=? AND workspace_id=? AND platform=?";
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final TkOpenMetaAccess access;
    private final TkSocialProperties social;
    private final TkSocialPlatformClient provider;
    private final TkSocialTokenCipher cipher;
    private final TkOpenApiCallbackService callbacks;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();

    public Map<String, Object> start(String platform) {
        TkOpenMetaAccess.Scope scope = access.require(platform, "auth");
        cipher.validateKey();
        String session = TkOpenApiIds.next("metaauth"), state = "tkmeta_" + secret(random);
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);
        String authorize = provider.authorizeUrl(TkOpenMetaAccess.provider(platform), state);
        db.update("INSERT INTO tk_open_meta_auth_session (id,client_id,workspace_id,platform,state_hash,status,expires_at) VALUES (?,?,?,?,?,'PENDING',?)",
                session, scope.clientId, scope.workspaceId, platform, TkSocialAuthService.hash(state), expiry);
        return result("authSessionId", session, "workspaceId", scope.workspaceId, "authorizeUrl", authorize, "status", "PENDING", "expireTime", expiry);
    }

    public static boolean isOpenState(String state) { return state != null && state.startsWith("tkmeta_"); }

    public boolean callback(String platform, String code, String state, String error) {
        if (!access.enabled(platform) || state == null || !state.matches("tkmeta_[A-Za-z0-9_-]{43}")) return false;
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_auth_session WHERE state_hash=? AND platform=?",
                TkSocialAuthService.hash(state), platform));
        if (row == null) return false;
        String id = str(row, "id"), client = str(row, "client_id"), workspace = str(row, "workspace_id");
        if (!access.allowed(client, platform, "auth") || db.update("UPDATE tk_open_meta_auth_session SET status='EXCHANGING' "
                + "WHERE id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP(3)", id) != 1) return false;
        try {
            if (error != null || code == null || code.isEmpty() || code.length() > 4096) {
                fail(id, "META_AUTH_DENIED");
                return false;
            }
            boolean facebook = TkOpenMetaAccess.FACEBOOK.equals(platform);
            String uri = social.credentials(TkOpenMetaAccess.provider(platform)).getRedirectUri();
            TkSocialPlatformClient.Authorization grant = facebook ? provider.authorizeFacebook(code, uri) : provider.authorizeInstagram(code, uri);
            TkOpenMetaAccess.Scope scope = new TkOpenMetaAccess.Scope(client, workspace, platform);
            if (facebook) {
                grant.setAccessToken(null);
                if (grant.getPages() == null || grant.getPages().isEmpty()) throw invalidPage();
                String encrypted = cipher.encrypt(json.writeValueAsString(grant), sessionContext(scope, id));
                return db.update("UPDATE tk_open_meta_auth_session SET status='PAGES_READY',candidate_cipher=? WHERE id=? "
                        + "AND status='EXCHANGING' AND expires_at>CURRENT_TIMESTAMP(3)", encrypted, id) == 1;
            }
            transactions.executeWithoutResult(status -> {
                String connection = bind(scope, grant.getExternalId(), grant.getAccountName(), grant.getAccessToken(), grant.getExpiresAt());
                finish(scope, id, "EXCHANGING", connection);
            });
            return true;
        } catch (Exception ex) {
            log.warn("Meta OpenAPI authorization failed: platform={}, session={}, type={}", platform, id, ex.getClass().getSimpleName());
            fail(id, "META_AUTH_FAILED");
            return false;
        }
    }

    public Map<String, Object> session(String platform, String id) {
        TkOpenMetaAccess.Scope scope = access.require(platform, "auth");
        Map<String, Object> row = findSession(scope, id, false);
        String status = str(row, "status");
        boolean expired = !time(row, "expires_at").isAfter(LocalDateTime.now());
        if (expired && Arrays.asList("PENDING", "EXCHANGING", "PAGES_READY").contains(status)) status = "EXPIRED";
        Map<String, Object> result = result("authSessionId", id, "workspaceId", scope.workspaceId, "status", status,
                "connectionId", row.get("connection_id"), "errorCode", row.get("error_code"));
        if ("PAGES_READY".equals(status)) {
            List<Map<String, Object>> pages = new ArrayList<>();
            for (TkSocialPlatformClient.PageCandidate page : candidates(scope, row).getPages())
                if (TkSocialPlatformClient.canPublish(page.getTasks())) pages.add(result("pageId", page.getId(), "name", page.getName()));
            result.put("pages", pages);
        }
        return result;
    }

    public Map<String, Object> selectPage(String platform, String id, String pageId) {
        TkOpenMetaAccess.Scope scope = access.require(platform, "auth");
        if (!TkOpenMetaAccess.FACEBOOK.equals(platform)) throw invalidPage();
        return transactions.execute(status -> {
            Map<String, Object> row = findSession(scope, id, true);
            if ("SUCCESS".equals(row.get("status"))) {
                Map<String, Object> conn = connection(scope, str(row, "connection_id"));
                if (Objects.equals(pageId, conn.get("external_id"))) return connectionView(conn);
                throw invalidPage();
            }
            if (!"PAGES_READY".equals(row.get("status")) || !time(row, "expires_at").isAfter(LocalDateTime.now())) throw invalidPage();
            TkSocialPlatformClient.Authorization grant = candidates(scope, row);
            TkSocialPlatformClient.PageCandidate page = grant.getPages().stream().filter(p -> pageId.equals(p.getId())
                    && TkSocialPlatformClient.canPublish(p.getTasks())).findFirst().orElseThrow(TkOpenMetaAuth::invalidPage);
            String connection = bind(scope, pageId, page.getName(), page.getAccessToken(), grant.getExpiresAt());
            finish(scope, id, "PAGES_READY", connection);
            return connectionView(connection(scope, connection));
        });
    }

    public List<Map<String, Object>> connections(String platform) {
        TkOpenMetaAccess.Scope scope = access.require(platform, "auth");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : db.queryForList("SELECT * FROM tk_open_meta_connection WHERE client_id=? AND workspace_id=? AND platform=? ORDER BY id",
                scope.clientId, scope.workspaceId, platform)) result.add(connectionView(row));
        return result;
    }

    public boolean revoke(String platform, String id) {
        TkOpenMetaAccess.Scope scope = access.require(platform, "auth");
        connection(scope, id);
        db.update("UPDATE tk_open_meta_connection SET status='REVOKED',token_cipher=NULL,token_expires_at=NULL" + SCOPED, scope.args(id));
        return true;
    }

    Map<String, Object> connection(TkOpenMetaAccess.Scope scope, String id) {
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_connection" + SCOPED, scope.args(id)));
        if (row == null) throw TkOpenApiException.notFound("META_CONNECTION_NOT_FOUND", "Connection not found");
        return row;
    }

    String token(TkOpenMetaAccess.Scope scope, Map<String, Object> row) {
        if (!"AUTHORIZED".equals(connectionStatus(row)))
            throw TkOpenApiException.conflict("META_REAUTH_REQUIRED", "Connection is no longer authorized");
        return cipher.decrypt(str(row, "token_cipher"), tokenContext(scope, str(row, "id")));
    }

    static String connectionStatus(Map<String, Object> row) {
        LocalDateTime until = time(row, "token_expires_at");
        return "AUTHORIZED".equals(row.get("status")) && until != null && !until.isAfter(LocalDateTime.now()) ? "EXPIRED" : str(row, "status");
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void expire() {
        if (access.enabled("facebook") || access.enabled("instagram")) db.update("UPDATE tk_open_meta_auth_session SET status='EXPIRED',candidate_cipher=NULL "
                + "WHERE status IN ('PENDING','EXCHANGING','PAGES_READY') AND expires_at<=CURRENT_TIMESTAMP(3)");
    }

    private String bind(TkOpenMetaAccess.Scope scope, String ext, String name, String token, LocalDateTime expires) {
        if (ext == null || !ext.matches("[0-9]{1,100}") || token == null || token.trim().isEmpty()) throw invalidPage();
        String id = TkOpenApiIds.next("metaconn");
        String display = name == null || name.isEmpty() ? ext : name.substring(0, Math.min(255, name.length()));
        try {
            db.update("INSERT INTO tk_open_meta_connection (id,client_id,workspace_id,platform,external_id,account_name,token_cipher,token_expires_at,status) "
                    + "VALUES (?,?,?,?,?,?,?,?,'AUTHORIZED')", id, scope.clientId, scope.workspaceId, scope.platform, ext, display,
                    cipher.encrypt(token, tokenContext(scope, id)), expires);
        } catch (DuplicateKeyException duplicate) {
            Map<String, Object> existing = one(db.queryForList("SELECT id FROM tk_open_meta_connection WHERE client_id=? "
                    + "AND workspace_id=? AND platform=? AND external_id=? FOR UPDATE", scope.clientId, scope.workspaceId, scope.platform, ext));
            if (existing == null) throw duplicate;
            id = str(existing, "id");
            db.update("UPDATE tk_open_meta_connection SET account_name=?,token_cipher=?,token_expires_at=?,status='AUTHORIZED' WHERE id=?",
                    display, cipher.encrypt(token, tokenContext(scope, id)), expires, id);
        }
        return id;
    }

    private void finish(TkOpenMetaAccess.Scope scope, String id, String previous, String connection) {
        if (db.update("UPDATE tk_open_meta_auth_session SET status='SUCCESS',connection_id=?,candidate_cipher=NULL "
                + "WHERE id=? AND status=? AND expires_at>CURRENT_TIMESTAMP(3)", connection, id, previous) != 1) throw invalidPage();
        callbacks.enqueueOnce(scope.clientId, "authorization.completed", "META_AUTH", id,
                result("platform", scope.platform, "workspaceId", scope.workspaceId, "authSessionId", id, "connectionId", connection, "status", "SUCCESS"));
    }

    private void fail(String id, String error) {
        db.update("UPDATE tk_open_meta_auth_session SET status='FAILED',candidate_cipher=NULL,error_code=? WHERE id=? AND status='EXCHANGING'", error, id);
    }

    private Map<String, Object> findSession(TkOpenMetaAccess.Scope scope, String id, boolean lock) {
        Map<String, Object> row = one(db.queryForList("SELECT * FROM tk_open_meta_auth_session" + SCOPED + (lock ? " FOR UPDATE" : ""), scope.args(id)));
        if (row == null) throw TkOpenApiException.notFound("META_AUTH_NOT_FOUND", "Authorization session not found");
        return row;
    }

    private TkSocialPlatformClient.Authorization candidates(TkOpenMetaAccess.Scope scope, Map<String, Object> row) {
        try {
            return json.readValue(cipher.decrypt(str(row, "candidate_cipher"), sessionContext(scope, str(row, "id"))), TkSocialPlatformClient.Authorization.class);
        } catch (Exception ex) { throw invalidPage(); }
    }

    private static Map<String, Object> connectionView(Map<String, Object> row) {
        return result("connectionId", row.get("id"), "platform", row.get("platform"), "workspaceId", row.get("workspace_id"),
                "externalAccountId", row.get("external_id"), "accountName", row.get("account_name"), "status", connectionStatus(row));
    }

    private static TkOpenApiException invalidPage() { return TkOpenApiException.conflict("META_PAGE_UNAVAILABLE", "The authorized Page or session is unavailable"); }
    private static String sessionContext(TkOpenMetaAccess.Scope s, String id) { return context("session", s, id); }
    private static String tokenContext(TkOpenMetaAccess.Scope s, String id) { return context("connection", s, id); }
    private static String context(String type, TkOpenMetaAccess.Scope s, String id) {
        return "meta-open:" + type + ":" + s.clientId + ":" + s.workspaceId + ":" + s.platform + ":" + id;
    }
    static String secret(SecureRandom random) { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static Map<String, Object> one(List<Map<String, Object>> rows) { return rows.isEmpty() ? null : rows.get(0); }
    static String str(Map<String, Object> row, String key) { return (String) row.get(key); }
    static LocalDateTime time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Timestamp ? ((Timestamp) value).toLocalDateTime() : (LocalDateTime) value;
    }
    static Map<String, Object> result(Object... fields) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        return result;
    }
}
