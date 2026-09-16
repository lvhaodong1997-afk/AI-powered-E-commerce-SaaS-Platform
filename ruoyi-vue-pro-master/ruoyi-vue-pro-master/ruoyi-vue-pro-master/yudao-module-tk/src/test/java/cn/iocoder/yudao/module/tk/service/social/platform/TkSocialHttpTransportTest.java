package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialHttpTransportTest {
    private final Deque<StubConnection> connections=new ArrayDeque<>();
    private final List<URL> urls=new ArrayList<>();
    private TkSocialHttpTransport transport() {
        return new TkSocialHttpTransport(url->{ urls.add(url); return connections.removeFirst(); });
    }
    private StubConnection response(int code,String body) throws Exception {
        StubConnection c=new StubConnection(code,body); connections.add(c); return c;
    }

    @Test void hostedVideoUploadUsesOAuthAndFileUrlHeadersAndThenResumesFinish() throws Exception {
        StubConnection upload=response(200,"{\"success\":true}");
        StubConnection finish=response(200,"{\"success\":true}");
        TkSocialProperties p=new TkSocialProperties(); p.setEnabled(true);
        TkSocialPlatformClient c=new TkSocialPlatformClient(p,transport());
        TkSocialPlatformClient.PublishResult stage=c.advance("FACEBOOK_PAGE","42","page-token","VIDEO","caption","https://cdn.test/a.mp4","123","FB_STARTED");
        assertEquals("FB_UPLOADED",stage.getPlatformStatus());
        assertEquals("https://rupload.facebook.com/video-upload/v25.0/123",urls.get(0).toString());
        assertEquals("POST",upload.getRequestMethod());
        assertEquals("OAuth page-token",upload.getRequestProperty("Authorization"));
        assertEquals("https://cdn.test/a.mp4",upload.getRequestProperty("file_url"));
        assertEquals(0,upload.body.size()); assertFalse(upload.getInstanceFollowRedirects());
        assertEquals("WAITING",c.advance("FACEBOOK_PAGE","42","page-token","VIDEO","caption","https://cdn.test/a.mp4","123",stage.getPlatformStatus()).getStatus());
        assertEquals("Bearer page-token",finish.getRequestProperty("Authorization"));
        assertEquals("upload_phase=finish&video_id=123&video_state=PUBLISHED&description=caption",finish.body.toString("UTF-8"));
        assertTrue(upload.disconnected); assertTrue(finish.disconnected);
    }

    @Test void upstreamServerErrorDoesNotLeakResponseAndIsUncertainForMutation() throws Exception {
        response(500,"{\"error\":{\"message\":\"access_token=secret\",\"code\":2}}");
        TkSocialPlatformException e=assertThrows(TkSocialPlatformException.class,
                ()->transport().request("POST","https://graph.facebook.com/v25.0/42/video_reels",TkSocialPlatformClient.map(),"page-token",true));
        assertTrue(e.isUncertain()); assertFalse(e.isRetryable());
        assertNull(e.getCause()); assertFalse(e.getMessage().contains("secret"));
    }

    @Test void authorizationAndPermissionDenialsRequireReauthorization() throws Exception {
        for (int code:Arrays.asList(190,10,200)) {
            response(400,"{\"error\":{\"message\":\"secret\",\"code\":"+code+"}}");
            TkSocialPlatformException e=assertThrows(TkSocialPlatformException.class,
                    ()->transport().request("GET","https://graph.instagram.com/v25.0/me",TkSocialPlatformClient.map(),"token",false));
            assertTrue(e.isReauthRequired()); assertFalse(e.isUncertain());
        }
    }

    @Test void instagramTopLevelOAuthErrorRetainsSafeMetaCode() throws Exception {
        response(400,"{\"error_type\":\"OAuthException\",\"code\":190,\"error_message\":\"access_token=sentinel-secret\"}");

        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->transport().request("POST","https://api.instagram.com/oauth/access_token",
                        TkSocialPlatformClient.map("client_secret","sentinel-secret"),null,false));

        assertEquals("META_190",error.getCode());
        assertTrue(error.isReauthRequired());
        assertFalse(error.getMessage().contains("sentinel"));
    }

    @Test void unexpectedMutationResponseIsUncertain() throws Exception {
        response(200,"<html>proxy failure</html>");
        TkSocialPlatformException e=assertThrows(TkSocialPlatformException.class,
                ()->transport().request("POST","https://graph.facebook.com/v25.0/42/video_reels",TkSocialPlatformClient.map(),"token",true));
        assertTrue(e.isUncertain());
    }

    @Test void ioFailureDoesNotExposeSecretUrlOrExceptionCause() throws Exception {
        response(0,"").fail=true;
        TkSocialPlatformException e=assertThrows(TkSocialPlatformException.class,
                ()->transport().request("GET","https://graph.instagram.com/access_token",
                        TkSocialPlatformClient.map("client_secret","sentinel-secret"),null,false));
        assertFalse(e.getMessage().contains("sentinel-secret")); assertNull(e.getCause());
    }

    @Test void directTransportDoesNotEmitSecretsWithApplicationDebugLogging() throws Exception {
        ch.qos.logback.classic.Logger logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Level old=logger.getLevel();
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> captured=new ch.qos.logback.core.read.ListAppender<>();
        captured.start(); logger.addAppender(captured); logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            response(200,"{\"access_token\":\"returned-sentinel-secret\"}");
            transport().request("GET","https://graph.instagram.com/access_token",
                    TkSocialPlatformClient.map("client_secret","sentinel-secret","access_token","sentinel-token"),null,false);
            assertTrue(captured.list.stream().noneMatch(e->e.getFormattedMessage().contains("sentinel")));
        } finally { logger.detachAppender(captured); logger.setLevel(old); captured.stop(); }
    }

    private static final class StubConnection extends HttpURLConnection {
        private final int code;
        private final byte[] response;
        private final ByteArrayOutputStream body=new ByteArrayOutputStream();
        private boolean disconnected,fail;
        StubConnection(int code,String response) throws Exception {
            super(new URL("https://graph.facebook.com")); this.code=code; this.response=response.getBytes(StandardCharsets.UTF_8);
        }
        @Override public int getResponseCode() throws IOException { if(fail)throw new IOException("sentinel-secret"); return code; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(response); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(response); }
        @Override public OutputStream getOutputStream() { return body; }
        @Override public void disconnect() { disconnected=true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
