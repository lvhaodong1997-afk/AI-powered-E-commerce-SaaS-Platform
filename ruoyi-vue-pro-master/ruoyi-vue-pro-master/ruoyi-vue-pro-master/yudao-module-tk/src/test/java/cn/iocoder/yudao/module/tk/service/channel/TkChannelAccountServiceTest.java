package cn.iocoder.yudao.module.tk.service.channel;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXCallbackOutcome;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkChannelAccountServiceTest {
    @Test
    void logsYouTubeFailureStageWithoutLoggingProviderDetails() {
        String channel = TkChannelProperties.YOUTUBE;
        String state = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        TkChannelProperties properties = mock(TkChannelProperties.class);
        TkChannelProperties.Credentials credentials = new TkChannelProperties.Credentials();
        when(properties.ready(channel)).thenReturn(true);
        when(properties.require(channel)).thenReturn(credentials);
        TkSocialAuthSessionMapper sessions = mock(TkSocialAuthSessionMapper.class);
        TkSocialAuthSessionDO session = new TkSocialAuthSessionDO();
        session.setId(1L);
        session.setTenantId(2L);
        session.setPlatform(channel);
        when(sessions.findByStateHash(TkSocialAuthService.hash(state))).thenReturn(session);
        when(sessions.claim(eq(1L), eq("PENDING"), eq("PROCESSING"), any())).thenReturn(1);
        TkOpenXAuthService openXAuth = mock(TkOpenXAuthService.class);
        when(openXAuth.handleOfficialCallback(channel, "authorization-code", state, null, null))
                .thenReturn(TkOpenXCallbackOutcome.unhandled());
        TkSocialTokenCipher cipher = mock(TkSocialTokenCipher.class);
        when(cipher.decrypt(null, TkSocialAuthService.sessionContext(session))).thenReturn("private-verifier");
        TkChannelOAuthClient oauth = mock(TkChannelOAuthClient.class);
        when(oauth.exchange(channel, credentials, "authorization-code", "private-verifier"))
                .thenReturn(TkChannelHttp.JSON.createObjectNode().put("access_token", "private-access")
                        .put("refresh_token", "private-refresh"));
        when(oauth.account(channel, "private-access")).thenThrow(TkChannelHttp.failure(403, false));
        TkChannelAccountService service = new TkChannelAccountService(properties, oauth, null, sessions,
                null, cipher, null, null, openXAuth);

        Logger logger = (Logger) LoggerFactory.getLogger(TkChannelAccountService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            assertFalse(service.callback(channel, "authorization-code", state, null, null));
            verify(sessions).finish(eq(1L), eq("PROCESSING"), eq("FAILED"), isNull(), eq("Authorization failed"), any());
            assertEquals(1, logs.list.size());
            assertEquals("YouTube OAuth callback failed: stage=READ_ACCOUNT, reason=CHANNEL_HTTP_403",
                    logs.list.get(0).getFormattedMessage());
            assertNull(logs.list.get(0).getThrowableProxy());
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
