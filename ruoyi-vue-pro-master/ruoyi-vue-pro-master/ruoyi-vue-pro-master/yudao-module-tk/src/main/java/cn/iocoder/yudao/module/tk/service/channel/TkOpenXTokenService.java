package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
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
import java.util.Objects;

/**
 * Per-connection X tokens. Refresh and revoke never write workbench social accounts.
 * A historical source account is read once to copy ciphertext into the connection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TkOpenXTokenService {
    private final TkOpenXConnectionMapper connections;
    private final TkSocialAccountMapper accounts;
    private final TkSocialTokenCipher cipher;
    private final TkChannelOAuthClient oauth;
    private final TkChannelProperties properties;
    private final PlatformTransactionManager transactions;

    public void store(TkOpenXConnectionDO connection, String access, String refresh, LocalDateTime expiresAt) {
        connection.setAccessTokenCipher(encrypt(connection, access, refresh));
        connection.setTokenExpiresAt(expiresAt);
        connection.setRefreshTokenCipher(null);
        connections.updateById(connection);
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
        if (connection.getAccessTokenCipher() != null && !connection.getAccessTokenCipher().isEmpty()) {
            return TkChannelHttp.JSON.readTree(cipher.decrypt(connection.getAccessTokenCipher(), context(connection)));
        }
        if (connection.getSourceSocialAccountId() == null) {
            throw new IllegalStateException("missing token");
        }
        TkSocialAccountDO account = accounts.selectById(connection.getSourceSocialAccountId());
        if (account == null || !Objects.equals(account.getExternalAccountId(), connection.getExternalAccountId())) {
            throw new IllegalStateException("missing source account");
        }
        JsonNode copied = TkChannelHttp.JSON.readTree(cipher.decrypt(account.getAccessTokenCiphertext(), workbenchContext(account)));
        connection.setAccessTokenCipher(encrypt(connection, required(copied, "access"), required(copied, "refresh")));
        if (connection.getTokenExpiresAt() == null) connection.setTokenExpiresAt(account.getTokenExpiresAt());
        connections.updateById(connection);
        return copied;
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

    private static String workbenchContext(TkSocialAccountDO account) {
        return "channel:account:" + account.getTenantId() + ":" + account.getCompanyId() + ":"
                + account.getPlatform() + ":" + account.getExternalAccountId();
    }

    private String encrypt(TkOpenXConnectionDO connection, String access, String refresh) {
        ObjectNode payload = TkChannelHttp.JSON.createObjectNode().put("access", access).put("refresh", refresh);
        return cipher.encrypt(payload.toString(), context(connection));
    }

    private void markExpired(TkOpenXConnectionDO connection) {
        connection.setAuthStatus("EXPIRED");
        connection.setFailReason("Authorization expired");
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