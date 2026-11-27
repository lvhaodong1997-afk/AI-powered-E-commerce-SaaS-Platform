package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.tk.service.channel.TkChannelProperties.YOUTUBE;

@Service
@RequiredArgsConstructor
public class TkOpenYouTubeAuthService {
    private static final String SESSIONS = "tk_open_youtube_auth_session";
    private static final String CONNECTIONS = "tk_open_youtube_connection";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TkOpenYouTubeAccess access;
    private final TkChannelOAuthClient oauth;
    private final TkSocialTokenCipher cipher;
    private final JdbcTemplate db;
    private final PlatformTransactionManager transactions;

    public Map<String, Object> create(String endUserId) {
        String client = access.requireClient("youtube.auth"), workspace = access.workspaceId();
        TkChannelProperties.Credentials cfg = access.credentials();
        cipher.validateKey();
        String session = TkOpenApiIds.next("ytauth"), launch = random();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);
        db.update("INSERT INTO " + SESSIONS + " (session_id,client_id,workspace_id,end_user_id,launch_hash,status,expire_time) VALUES (?,?,?,?,?,'PENDING',?)",
                session, client, workspace, endUserId, hash(launch), expiry);
        URI origin = URI.create(cfg.getRedirectUri()).resolve("/");
        String url = UriComponentsBuilder.fromUri(origin.resolve(TkOpenYouTubeAccess.ROOT + "/auth/sessions/" + session + "/launch"))
                .queryParam("ticket", launch).toUriString();
        return fields("authSessionId", session, "status", "PENDING", "launchUrl", url, "expireTime", expiry);
    }

    public Map<String, Object> session(String id) {
        String client = access.requireClient("youtube.auth"), workspace = access.workspaceId();
        Session row = first(db.query("SELECT * FROM " + SESSIONS + " WHERE session_id=? AND client_id=? AND workspace_id=?",
                BeanPropertyRowMapper.newInstance(Session.class), id, client, workspace));
        if (row == null) throw TkOpenApiException.notFound("YOUTUBE_AUTH_NOT_FOUND", "authorization session does not exist");
        boolean expired = ("PENDING".equals(row.status) || "EXCHANGING".equals(row.status))
                && !row.expireTime.isAfter(LocalDateTime.now());
        return fields("authSessionId", row.sessionId, "status", expired ? "EXPIRED" : row.status,
                "connectionId", row.connectionId, "errorCode", expired ? "YOUTUBE_AUTH_EXPIRED" : row.failCode,
                "workspaceId", row.workspaceId, "expireTime", row.expireTime);
    }

    public URI launch(String id, String ticket) {
        TkChannelProperties.Credentials cfg = access.credentials();
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw invalidLaunch();
        Session row = first(db.query("SELECT * FROM " + SESSIONS + " WHERE session_id=? AND launch_hash=?",
                BeanPropertyRowMapper.newInstance(Session.class), id, hash(ticket)));
        if (row == null || !access.allowed(row.clientId, "youtube.auth")) throw invalidLaunch();
        String state = "yto_" + random(), verifier = random();
        String authorization = oauth.authorization(YOUTUBE, cfg, state, verifier);
        if (db.update("UPDATE " + SESSIONS + " SET state_hash=?,verifier_cipher=? WHERE id=? AND state_hash IS NULL AND status='PENDING' AND expire_time>?",
                hash(state), cipher.encrypt(verifier, sessionContext(id)), row.id, LocalDateTime.now()) != 1) throw invalidLaunch();
        return URI.create(authorization);
    }

    public boolean callback(String code, String state, String error) {
        if (!access.isEnabled() || !isOpenState(state)) return false;
        Session row = first(db.query("SELECT * FROM " + SESSIONS + " WHERE state_hash=?",
                BeanPropertyRowMapper.newInstance(Session.class), hash(state)));
        if (row == null || db.update("UPDATE " + SESSIONS + " SET state_hash=NULL,verifier_cipher=NULL,status='EXCHANGING' "
                + "WHERE id=? AND state_hash=? AND status='PENDING' AND expire_time>?", row.id, hash(state), LocalDateTime.now()) != 1) return false;
        String fail = "YOUTUBE_AUTH_FAILED";
        try {
            if (!access.allowed(row.clientId, "youtube.auth") || error != null || code == null || code.isEmpty()) {
                fail = "YOUTUBE_AUTH_DENIED";
            } else {
                JsonNode result = oauth.exchange(YOUTUBE, access.credentials(), code, cipher.decrypt(row.verifierCipher, sessionContext(row.sessionId)));
                String token = required(result, "access_token"), refresh = required(result, "refresh_token");
                JsonNode profile = oauth.account(YOUTUBE, token);
                String channel = required(profile, "id"), title = required(profile.path("snippet"), "title");
                String context = tokenContext(row.clientId, row.workspaceId, channel);
                String encrypted = encrypt(token, refresh, context);
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    if (!access.allowed(row.clientId, "youtube.auth")) throw invalidLaunch();
                    db.update("INSERT INTO " + CONNECTIONS + " (connection_id,client_id,workspace_id,external_channel_id,channel_title,token_cipher,token_expires_at,status) "
                                + "VALUES (?,?,?,?,?,?,?,'AUTHORIZED') ON DUPLICATE KEY UPDATE channel_title=VALUES(channel_title),"
                                + "token_cipher=VALUES(token_cipher),token_expires_at=VALUES(token_expires_at),status='AUTHORIZED'",
                        TkOpenApiIds.next("ytconn"), row.clientId, row.workspaceId, channel, title, encrypted, expires(result));
                    String connection = db.queryForObject("SELECT connection_id FROM " + CONNECTIONS + " WHERE client_id=? AND workspace_id=? AND external_channel_id=?",
                        String.class, row.clientId, row.workspaceId, channel);
                    if (db.update("UPDATE " + SESSIONS + " SET status='SUCCESS',connection_id=? WHERE id=? AND status='EXCHANGING'", connection, row.id) != 1)
                        throw invalidLaunch();
                });
                return true;
            }
        } catch (RuntimeException ignored) {
            // OAuth responses and launch credentials must not enter application logs.
        }
        db.update("UPDATE " + SESSIONS + " SET status='FAILED',fail_code=? WHERE id=?", fail, row.id);
        return false;
    }

    public List<Map<String, Object>> connections() {
        return db.query("SELECT connection_id,workspace_id,external_channel_id,channel_title,status FROM " + CONNECTIONS
                        + " WHERE client_id=? AND workspace_id=? ORDER BY id DESC",
                BeanPropertyRowMapper.newInstance(Connection.class), access.requireClient("youtube.auth"), access.workspaceId())
                .stream().map(TkOpenYouTubeAuthService::connectionResult).collect(Collectors.toList());
    }

    @Transactional(noRollbackFor = TkOpenApiException.class)
    public Map<String, Object> refreshProfile(String id) {
        Connection row = requireConnection(access.requireClient("youtube.auth"), access.workspaceId(), id, true);
        try {
            JsonNode profile = oauth.account(YOUTUBE, accessToken(row));
            if (!row.externalChannelId.equals(required(profile, "id"))) throw disabledConnection();
            row.channelTitle = required(profile.path("snippet"), "title");
            db.update("UPDATE " + CONNECTIONS + " SET channel_title=? WHERE id=?", row.channelTitle, row.id);
            return connectionResult(row);
        } catch (TkSocialPlatformException ex) {
            if (ex.isReauthRequired()) throw expire(row);
            throw TkOpenApiException.unavailable("YOUTUBE_PROFILE_UNAVAILABLE", "YouTube channel profile is unavailable");
        }
    }

    public boolean revoke(String id) {
        if (db.update("UPDATE " + CONNECTIONS + " SET status='REVOKED',token_cipher=NULL,token_expires_at=NULL WHERE client_id=? AND workspace_id=? AND connection_id=?",
                access.requireClient("youtube.auth"), access.workspaceId(), id) == 0) {
            throw TkOpenApiException.notFound("YOUTUBE_CONNECTION_NOT_FOUND", "connection does not exist");
        }
        return true;
    }

    Connection requireConnection(String client, String workspace, String id, boolean lock) {
        Connection row = first(db.query("SELECT * FROM " + CONNECTIONS + " WHERE client_id=? AND workspace_id=? AND connection_id=?"
                + (lock ? " FOR UPDATE" : ""), BeanPropertyRowMapper.newInstance(Connection.class), client, workspace, id));
        if (row == null || !"AUTHORIZED".equals(row.status)) throw disabledConnection();
        return row;
    }

    String accessToken(Connection row) {
        String context = tokenContext(row.clientId, row.workspaceId, row.externalChannelId);
        JsonNode token;
        try {
            token = TkChannelHttp.JSON.readTree(cipher.decrypt(row.tokenCipher, context));
        } catch (Exception ex) { throw expire(row); }
        if (row.tokenExpiresAt != null && row.tokenExpiresAt.isAfter(LocalDateTime.now().plusMinutes(2))) return required(token, "access");
        try {
            String refresh = required(token, "refresh");
            JsonNode renewed = oauth.refresh(YOUTUBE, access.credentials(), refresh);
            String next = required(renewed, "access_token");
            db.update("UPDATE " + CONNECTIONS + " SET token_cipher=?,token_expires_at=? WHERE id=?",
                    encrypt(next, renewed.path("refresh_token").asText(refresh), context), expires(renewed), row.id);
            return next;
        } catch (TkSocialPlatformException ex) {
            if ("CHANNEL_HTTP_400".equals(ex.getCode()) || ex.isReauthRequired()) throw expire(row);
            throw ex;
        }
    }

    TkOpenApiException expire(Connection row) {
        db.update("UPDATE " + CONNECTIONS + " SET status='EXPIRED',token_cipher=NULL,token_expires_at=NULL WHERE id=?", row.id);
        return disabledConnection();
    }

    private String encrypt(String token, String refresh, String context) {
        return cipher.encrypt(TkChannelHttp.JSON.createObjectNode().put("access", token).put("refresh", refresh).toString(), context);
    }

    private static Map<String, Object> connectionResult(Connection row) {
        return fields("connectionId", row.connectionId, "channelId", row.externalChannelId,
                "channelTitle", row.channelTitle, "status", row.status, "workspaceId", row.workspaceId);
    }

    public static boolean isOpenState(String state) { return state != null && state.matches("yto_[A-Za-z0-9_-]{43}"); }

    private static String sessionContext(String id) { return "open-youtube:session:" + id; }
    private static String tokenContext(String c, String w, String ch) { return "open-youtube:connection:" + c + ":" + w + ":" + ch; }
    private static TkOpenApiException disabledConnection() { return TkOpenApiException.conflict("YOUTUBE_CONNECTION_DISABLED", "connection is missing, expired or revoked"); }
    private static TkOpenApiException invalidLaunch() { return TkOpenApiException.notFound("YOUTUBE_AUTH_LAUNCH_INVALID", "authorization launch link is invalid, used or expired"); }
    private static String random() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static LocalDateTime expires(JsonNode n) {
        int seconds = n.path("expires_in").asInt();
        if (seconds <= 0) throw new IllegalArgumentException("OAuth expiration missing");
        return LocalDateTime.now().plusSeconds(seconds);
    }
    private static String required(JsonNode n, String field) {
        String value = n.path(field).asText();
        if (value.isEmpty()) throw new IllegalArgumentException("OAuth response incomplete");
        return value;
    }
    static String hash(String value) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }
    static <T> T first(List<T> values) { return values.isEmpty() ? null : values.get(0); }
    static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    @Data public static class Session {
        private Long id;
        private String sessionId, clientId, workspaceId, verifierCipher, status, connectionId, failCode;
        private LocalDateTime expireTime;
    }
    @Data public static class Connection {
        private Long id;
        private String connectionId, clientId, workspaceId, externalChannelId, channelTitle, tokenCipher, status;
        private LocalDateTime tokenExpiresAt;
    }
}
