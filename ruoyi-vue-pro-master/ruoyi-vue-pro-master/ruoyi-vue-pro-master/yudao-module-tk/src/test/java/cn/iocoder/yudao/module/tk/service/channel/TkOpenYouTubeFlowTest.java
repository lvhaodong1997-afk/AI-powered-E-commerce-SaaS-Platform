package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenYouTubeMediaDO;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.youtube.TkOpenYouTubeMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenYouTubeFlowTest {
    private static final String WORK = "0a506738-16b5-4c09-ac86-1a0c56dd3808";
    private static final String WORK_OTHER = "0a506738-16b5-4c09-ac86-1a0c56dd3809";
    private final TkOpenYouTubeAccess access = mock(TkOpenYouTubeAccess.class);
    private final TkChannelOAuthClient google = mock(TkChannelOAuthClient.class);
    private final TkSocialTokenCipher cipher = mock(TkSocialTokenCipher.class);
    private final TkOpenYouTubeMediaService media = mock(TkOpenYouTubeMediaService.class);
    private final TkChannelVideoClient videos = mock(TkChannelVideoClient.class);
    private final TkOpenApiCallbackService callbacks = mock(TkOpenApiCallbackService.class);
    private JdbcTemplate db;
    private TransactionTemplate tx;
    private TkOpenYouTubeAuthService auth;
    private TkOpenYouTubePublishService publish;

    @BeforeEach
    void setup() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        DataSourceTransactionManager mgr = new DataSourceTransactionManager(source);
        tx = new TransactionTemplate(mgr);
        try (java.io.InputStream sql = new ClassPathResource("sql/tk_open_youtube_openapi_mysql.sql").getInputStream()) {
            String ddl = StreamUtils.copyToString(sql, StandardCharsets.UTF_8).replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
                    .replace(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", "").replace("b'0'", "FALSE");
            for (String statement : ddl.split(";")) if (!statement.trim().isEmpty()) db.execute(statement);
        }
        when(access.requireClient(anyString())).thenReturn("client_a");
        when(access.workspaceId()).thenReturn(WORK);
        when(access.isEnabled()).thenReturn(true);
        when(access.allowed(eq("client_a"), anyString())).thenReturn(true);
        TkChannelProperties.Credentials creds = new TkChannelProperties.Credentials();
        creds.setRedirectUri("https://example.test/admin-api/tk/channel-publish/youtube/callback");
        when(access.credentials()).thenReturn(creds);
        when(cipher.encrypt(anyString(), anyString())).thenAnswer(i -> "cipher:" + i.getArgument(0));
        when(cipher.decrypt(anyString(), anyString())).thenAnswer(i -> i.<String>getArgument(0).substring(7));
        when(google.authorization(eq("youtube"), any(), anyString(), anyString()))
                .thenAnswer(i -> "https://accounts.google.com/o/oauth2/v2/auth?state=" + i.getArgument(2));
        when(google.exchange(eq("youtube"), any(), eq("code"), anyString()))
                .thenReturn(TkChannelHttp.JSON.readTree("{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"expires_in\":3600}"));
        when(google.account("youtube", "access")).thenReturn(TkChannelHttp.JSON.readTree("{\"id\":\"UC_example\",\"snippet\":{\"title\":\"Channel\"}}"));
        auth = new TkOpenYouTubeAuthService(access, google, cipher, db, mgr);
        publish = new TkOpenYouTubePublishService(access, auth, media, videos, cipher, callbacks, db, mgr);
    }

    @Test
    void oauthTicketAndCallbackAreSingleUseAndWorkspaceScoped() {
        Map<String, Object> session = auth.create("member-1");
        String id = session.get("authSessionId").toString();
        String ticket = query(URI.create(session.get("launchUrl").toString()));
        assertThrows(TkOpenApiException.class, () -> auth.launch(id, ticket + "x"));
        String state = query(auth.launch(id, ticket));
        assertThrows(TkOpenApiException.class, () -> auth.launch(id, ticket));
        assertTrue(auth.callback("code", state, null));
        assertFalse(auth.callback("code", state, null));
        verify(google, times(1)).exchange(eq("youtube"), any(), eq("code"), anyString());
        assertEquals("SUCCESS", auth.session(id).get("status"));
        assertFalse(auth.connections().get(0).containsKey("tokenCipher"));
        when(access.workspaceId()).thenReturn(WORK_OTHER);
        assertThrows(TkOpenApiException.class, () -> auth.session(id));
        assertTrue(auth.connections().isEmpty());
        when(access.workspaceId()).thenReturn(WORK);
        when(access.requireClient(anyString())).thenReturn("client_b");
        assertTrue(auth.connections().isEmpty());
    }

    @Test
    void expiredOAuthStateCannotBeExchanged() {
        Map<String, Object> session = auth.create(null);
        String id = session.get("authSessionId").toString();
        String state = query(auth.launch(id, query(URI.create(session.get("launchUrl").toString()))));
        db.update("UPDATE tk_open_youtube_auth_session SET expire_time='2000-01-01 00:00:00' WHERE session_id=?", id);
        assertFalse(auth.callback("code", state, null));
        assertEquals("EXPIRED", auth.session(id).get("status"));
        verify(google, never()).exchange(anyString(), any(), anyString(), anyString());
    }

    @Test
    void oneTaskResumesWithSameUploadAndPollsKnownVideoWithoutUploadingAgain() {
        TkOpenYouTubePublishService.Create request = request();
        String id = publish.create(request, "key-1").get("taskId").toString();
        assertEquals(id, publish.create(request, "key-1").get("taskId"));
        request.setTitle("changed");
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", assertThrows(TkOpenApiException.class, () -> publish.create(request, "key-1")).getCode());
        when(access.workspaceId()).thenReturn(WORK_OTHER);
        assertThrows(TkOpenApiException.class, () -> publish.get(id));
        when(access.workspaceId()).thenReturn(WORK);
        when(videos.advanceYouTube(any(), any(), eq("access"), eq("https://storage.invalid/video"), isNull(), eq("etag"), eq(12L), isNull()))
                .thenReturn(new TkChannelVideoClient.Checkpoint("YT_UPLOAD", null, "resumable", null, 0, 0));
        when(videos.reconcileYouTube("resumable", "access", 12))
                .thenReturn(new TkChannelVideoClient.Checkpoint("YT_PROCESSING", null, null, "abcdefghijk", 12, 5));
        when(videos.youtubeStatus("abcdefghijk", "Bearer access", 12))
                .thenReturn(new TkChannelVideoClient.Checkpoint("PUBLISHED", null, null, "abcdefghijk", 12, 0));
        run(id);
        assertEquals("YT_UPLOAD", publish.get(id).get("platformStatus"));
        when(media.requireReady("client_a", "media_a", WORK)).thenThrow(
                TkOpenApiException.badRequest("MEDIA_NOT_READY", "expired"));
        run(id);
        assertEquals("abcdefghijk", publish.get(id).get("youtubeVideoId"));
        run(id);
        assertEquals("SUCCESS", publish.get(id).get("status"));
        verify(videos, times(1)).advanceYouTube(any(), any(), anyString(), anyString(), isNull(), eq("etag"), eq(12L), isNull());
        verify(videos).reconcileYouTube("resumable", "access", 12);
        verify(callbacks).enqueueOnce(eq("client_a"), eq("publish.completed"), eq("PUBLISH_TASK"), eq(id), anyMap(), eq(0));
    }

    @Test
    void uncertainUploadCannotBeRetried() {
        String id = publish.create(request(), "key-2").get("taskId").toString();
        when(videos.advanceYouTube(any(), any(), anyString(), anyString(), isNull(), eq("etag"), eq(12L), isNull()))
                .thenThrow(new TkSocialPlatformException("YT_SESSION_GONE", "unconfirmed", false, false, true));
        run(id);
        assertEquals("UNKNOWN", publish.get(id).get("status"));
        assertEquals(false, publish.get(id).get("retryable"));
        assertThrows(TkOpenApiException.class, () -> publish.retry(id));
    }

    private TkOpenYouTubePublishService.Create request() {
        Map<String, Object> session = auth.create(null);
        String state = query(auth.launch(session.get("authSessionId").toString(), query(URI.create(session.get("launchUrl").toString()))));
        assertTrue(auth.callback("code", state, null));
        TkOpenYouTubeMediaDO ready = TkOpenYouTubeMediaDO.builder().fileSize(12L).uploadMode("OSS").objectEtag("etag").build();
        when(media.requireReady("client_a", "media_a", WORK)).thenReturn(ready);
        when(media.resolveReadUrl(ready)).thenReturn("https://storage.invalid/video");
        TkOpenYouTubePublishService.Create request = new TkOpenYouTubePublishService.Create();
        request.setConnectionId(auth.connections().get(0).get("connectionId").toString());
        request.setMediaId("media_a");
        request.setPrivacyStatus("private");
        request.setTitle("test title");
        request.setMadeForKids(false);
        return request;
    }

    private void run(String id) {
        db.update("UPDATE tk_open_youtube_publish_task SET next_retry_time='2000-01-01 00:00:00' WHERE task_id=?", id);
        Long row = db.queryForObject("SELECT id FROM tk_open_youtube_publish_task WHERE task_id=?", Long.class, id);
        tx.executeWithoutResult(status -> publish.process(row));
    }

    private static String query(URI uri) { return uri.getQuery().substring(uri.getQuery().indexOf('=') + 1); }
}
