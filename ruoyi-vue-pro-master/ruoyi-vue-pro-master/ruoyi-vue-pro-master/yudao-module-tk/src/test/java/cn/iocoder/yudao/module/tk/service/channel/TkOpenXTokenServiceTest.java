package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXConnectionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenXTokenServiceTest {
    private final TkOpenXConnectionMapper connections = mock(TkOpenXConnectionMapper.class);
    private final TkSocialAccountMapper accounts = mock(TkSocialAccountMapper.class);
    private final TkSocialTokenCipher cipher = mock(TkSocialTokenCipher.class);
    private final TkChannelOAuthClient oauth = mock(TkChannelOAuthClient.class);
    private final TkChannelProperties properties = new TkChannelProperties();
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private TkOpenXTokenService service;

    @BeforeEach
    void setup() {
        TkChannelProperties.Credentials x = new TkChannelProperties.Credentials();
        x.setEnabled(true);
        x.setClientId("cid");
        x.setClientSecret("secret");
        x.setRedirectUri("https://platform.example.com/admin-api/tk/channel-publish/x/callback");
        properties.setX(x);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new TkOpenXTokenService(connections, accounts, cipher, oauth, properties, tx);
    }

    @Test
    void storeEncryptsWithConnectionContextAndDoesNotWriteWorkbenchAccounts() {
        TkOpenXConnectionDO row = connection("client-a", "xconn_a", "user-1");
        when(cipher.encrypt(contains("access-a"), eq(TkOpenXTokenService.context(row)))).thenReturn("cipher-a");
        service.store(row, "access-a", "refresh-a", LocalDateTime.now().plusHours(2));
        verify(connections).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(updated -> "cipher-a".equals(updated.getAccessTokenCipher())
                && updated.getRefreshTokenCipher() == null
                && "client-a".equals(updated.getClientId())));
        verify(accounts, never()).updateById(any(TkSocialAccountDO.class));
        verify(cipher, never()).encrypt(anyString(), startsWith("channel:account:"));
    }

    @Test
    void revokedOrMissingConnectionIsRejectedWithoutRefreshing() {
        TkOpenXConnectionDO revoked = connection("client-a", "xconn_a", "user-1").setAuthStatus("REVOKED");
        assertEquals("X_CONNECTION_DISABLED", assertThrows(TkOpenApiException.class,
                () -> service.accessToken(revoked)).getCode());
        verify(oauth, never()).refresh(any(), any(), any());
        verify(connections, never()).updateById(ArgumentMatchers.<TkOpenXConnectionDO>any());
    }

    @Test
    void refreshUpdatesOnlyTheRequestedConnectionAndLeavesOtherClientsAlone() throws Exception {
        TkOpenXConnectionDO clientA = connection("client-a", "xconn_a", "user-1")
                .setAccessTokenCipher("cipher-a")
                .setTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        TkOpenXConnectionDO clientB = connection("client-b", "xconn_b", "user-1")
                .setAccessTokenCipher("cipher-b")
                .setTokenExpiresAt(LocalDateTime.now().plusHours(2));
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", "xconn_a")).thenReturn(clientA);
        when(cipher.decrypt("cipher-a", TkOpenXTokenService.context(clientA)))
                .thenReturn("{\"access\":\"old-a\",\"refresh\":\"ref-a\"}");
        ObjectNode renewed = TkChannelHttp.JSON.createObjectNode();
        renewed.put("access_token", "new-a");
        renewed.put("refresh_token", "ref-a2");
        renewed.put("expires_in", 7200);
        when(oauth.refresh(eq(TkChannelProperties.X), any(), eq("ref-a"))).thenReturn(renewed);
        when(cipher.encrypt(contains("new-a"), eq(TkOpenXTokenService.context(clientA)))).thenReturn("cipher-a-new");

        assertEquals("new-a", service.accessToken(clientA));
        verify(connections).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(updated -> "client-a".equals(updated.getClientId())
                && "xconn_a".equals(updated.getConnectionId())
                && "cipher-a-new".equals(updated.getAccessTokenCipher())));
        verify(connections, never()).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(updated -> updated != null && "client-b".equals(updated.getClientId())));
        verify(oauth, never()).refresh(any(), any(), eq("ref-b"));
        assertEquals("cipher-b", clientB.getAccessTokenCipher());
        verify(accounts, never()).updateById(any(TkSocialAccountDO.class));
    }

    @Test
    void lazyCopyFromWorkbenchDoesNotWriteBackToTheSourceAccount() throws Exception {
        TkOpenXConnectionDO row = connection("client-a", "xconn_a", "user-1")
                .setSourceSocialAccountId(88L)
                .setTokenExpiresAt(LocalDateTime.now().plusHours(2));
        TkSocialAccountDO source = new TkSocialAccountDO();
        source.setId(88L);
        source.setTenantId(1L);
        source.setCompanyId(2L);
        source.setPlatform("x");
        source.setExternalAccountId("user-1");
        source.setAccessTokenCiphertext("workbench-cipher");
        when(connections.selectByClientAndConnectionIdForUpdate("client-a", "xconn_a")).thenReturn(row);
        when(accounts.selectById(88L)).thenReturn(source);
        when(cipher.decrypt("workbench-cipher", "channel:account:1:2:x:user-1"))
                .thenReturn("{\"access\":\"wb-access\",\"refresh\":\"wb-refresh\"}");
        when(cipher.encrypt(contains("wb-access"), eq(TkOpenXTokenService.context(row)))).thenReturn("cipher-copied");

        assertEquals("wb-access", service.accessToken(row));
        verify(connections).updateById(ArgumentMatchers.<TkOpenXConnectionDO>argThat(updated -> "cipher-copied".equals(updated.getAccessTokenCipher())
                && "client-a".equals(updated.getClientId())));
        verify(accounts, never()).updateById(any(TkSocialAccountDO.class));
        verify(accounts, never()).insert(any(TkSocialAccountDO.class));
    }

    private static TkOpenXConnectionDO connection(String clientId, String connectionId, String externalId) {
        return new TkOpenXConnectionDO()
                .setClientId(clientId)
                .setConnectionId(connectionId)
                .setExternalAccountId(externalId)
                .setAuthStatus("AUTHORIZED");
    }
}