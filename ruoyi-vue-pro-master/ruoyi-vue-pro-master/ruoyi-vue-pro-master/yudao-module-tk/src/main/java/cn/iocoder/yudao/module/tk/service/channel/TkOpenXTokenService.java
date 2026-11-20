package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Per-connection X tokens. Refresh and revoke never read or write workbench social accounts.
 * Historical migrated rows without their own ciphertext cannot be used until the client
 * re-authorizes; copying a workbench refresh token would rotate it and break the workbench.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TkOpenXTokenService {
    private final TkOpenXConnectionMapper connections;
    private final TkSocialTokenCipher cipher;
    private final TkChannelOAuthClient oauth;
    private final TkChannelProperties properties;
    private final PlatformTransactionManager transactions;

    public void store(TkOpenXConnectionDO connection, String access, String refresh, LocalDateTime expiresAt) {
        connection.setAccessTokenCipher(encrypt(connection, access, refresh));
        connection.setTokenExpiresAt(expiresAt);
        connection.setRefreshTokenCipher(null);
        connection.setSourceSocialAccountId(null);
        connections.updateById(connection);
    }

    public void clearSecrets(TkOpenXConnectionDO connection) {
        connection.setAccessTokenCipher(null);
        connection.setRefreshTokenCipher(null);
        connection.setTokenExpiresAt(null);
        connection.setSourceSocialAccountId(null);
    }

    public String accessToken(TkOpenXConnectionDO requested) {
        if (requested == null || !"AUTHORIZED".equals(requested.getAuthStatus())) {
            throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
        }
        return new TransactionTemplate(transactions).execute(status -> {
            TkOpenXConnectionDO connection = connections.selectByClientAndConnectionIdForUpdate(
                    requested.getClientId(), requested.getConnectionId());
            if (connection == null || !"AUTHORIZED".equals(connection.getAuthStatus())) {
                throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
            }
            if (connection.getAccessTokenCipher() == null || connection.getAccessTokenCipher().isEmpty()) {
                markReauthRequired(connection);
                throw TkOpenApiException.conflict("X_REAUTH_REQUIRED",
                        "This X connection has no isolated token and must be authorized again");
            }
            JsonNode current;
            try {
                current = payload(connection);
            } catch (RuntimeException ex) {
                markExpired(connection);
                throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
            }
            if (connection.getTokenExpiresAt() != null
                    && connection.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(2))) {
                return required(current, "access");
            }
            try {
                String refresh = required(current, "refresh");
                JsonNode renewed = oauth.refresh(TkChannelProperties.X, properties.require(TkChannelProperties.X), refresh);
                String replacement = required(renewed, "access_token");
                String nextRefresh = renewed.path("refresh_token").asText(refresh);
                connection.setAccessTokenCipher(encrypt(connection, replacement, nextRefresh));
                connection.setTokenExpiresAt(expires(renewed));
                connections.updateById(connection);
                return replacement;
            } catch (TkSocialPlatformException ex) {
                if (ex.isRetryable()) throw ex;
                markExpired(connection);
                throw TkOpenApiException.conflict("X_CONNECTION_DISABLED", "X connection is missing, expired or revoked");
            }
        });
    }

    private JsonNode payload(TkOpenXConnectionDO connection) {
        try {
            return TkChannelHttp.JSON.readTree(cipher.decrypt(connection.getAccessTokenCipher(), context(connection)));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("token payload unavailable");
        }
    }

    public static String context(TkOpenXConnectionDO connection) {
        return "open-x:connection:" + connection.getClientId() + ":" + connection.getConnectionId()
                + ":" + connection.getExternalAccountId();
    }

    private String encrypt(TkOpenXConnectionDO connection, String access, String refresh) {
        ObjectNode payload = TkChannelHttp.JSON.createObjectNode().put("access", access).put("refresh", refresh);
        return cipher.encrypt(payload.toString(), context(connection));
    }

    private void markExpired(TkOpenXConnectionDO connection) {
        connection.setAuthStatus("EXPIRED");
        connection.setFailReason("Authorization expired");
        clearSecrets(connection);
        connections.updateById(connection);
    }

    private void markReauthRequired(TkOpenXConnectionDO connection) {
        connection.setAuthStatus("REAUTH_REQUIRED");
        connection.setFailReason("Re-authorization required; workbench tokens are not copied");
        clearSecrets(connection);
        connections.updateById(connection);
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isEmpty()) throw new IllegalStateException("missing token field");
        return value;
    }

    private static LocalDateTime expires(JsonNode token) {
        int seconds = token.path("expires_in").asInt();
        if (seconds <= 0) throw new IllegalArgumentException("Missing token expiration");
        return LocalDateTime.now().plusSeconds(seconds);
    }
}