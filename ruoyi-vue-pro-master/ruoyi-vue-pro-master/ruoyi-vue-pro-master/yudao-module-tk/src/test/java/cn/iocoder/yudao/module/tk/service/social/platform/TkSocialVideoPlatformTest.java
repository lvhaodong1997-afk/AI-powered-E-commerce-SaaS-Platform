package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialVideoPlatformTest {
    private final Deque<String> responses = new ArrayDeque<>();
    private final List<String> calls = new ArrayList<>();
    private final List<Map<String,String>> params = new ArrayList<>();
    private TkSocialPlatformClient client() {
        TkSocialProperties config = new TkSocialProperties(); config.setEnabled(true);
        return new TkSocialPlatformClient(config, (method,url,p,token,mutation) -> {
            calls.add(method + " " + url); params.add(p);
            String response = responses.removeFirst();
            if ("TIMEOUT".equals(response)) throw TkSocialHttpTransport.failure(503, null, mutation);
            try { return new ObjectMapper().readTree(response); } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test void instagramVideoUsesReelsAndResumesWithoutRecreating() {
        responses.add("{\"id\":\"123\"}");
        responses.add("{\"status_code\":\"IN_PROGRESS\"}");
        TkSocialPlatformClient c = client();
        assertEquals("123", c.advance("INSTAGRAM","42","t","VIDEO","caption","https://cdn.test/a.mp4",null,null).getContainerId());
        assertEquals("REELS",params.get(0).get("media_type"));
        assertEquals("https://cdn.test/a.mp4",params.get(0).get("video_url"));
        assertEquals("WAITING",c.advance("INSTAGRAM","42","t","VIDEO","caption","https://cdn.test/a.mp4","123","IN_PROGRESS").getStatus());
        assertEquals(2,calls.size()); assertTrue(calls.get(1).startsWith("GET "));
    }

    @Test void instagramProcessingErrorStopsBeforePublish() {
        responses.add("{\"status_code\":\"ERROR\",\"status\":\"upstream secret\"}");
        TkSocialPlatformException e = assertThrows(TkSocialPlatformException.class,
                () -> client().advance("INSTAGRAM","42","t","VIDEO","","https://cdn.test/a.mp4","123","IN_PROGRESS"));
        assertFalse(e.isRetryable()); assertFalse(e.isUncertain());
        assertFalse(e.getMessage().contains("secret")); assertEquals(1,calls.size());
    }

    @Test void facebookReelStagesAreDurableAndFinishDoesNotMeanPublished() {
        responses.add("{\"video_id\":\"123\",\"upload_url\":\"https://rupload.facebook.com/video-upload/v25.0/123\"}");
        responses.add("{\"success\":true}");
        responses.add("{\"success\":true}");
        responses.add("{\"status\":{\"video_status\":\"processing\",\"publishing_phase\":{\"status\":\"not_started\"}}}");
        responses.add("{\"status\":{\"video_status\":\"ready\",\"publishing_phase\":{\"status\":\"complete\"}}}");
        TkSocialPlatformClient c = client();
        TkSocialPlatformClient.PublishResult r = c.advance("FACEBOOK_PAGE","42","t","VIDEO","caption","https://cdn.test/a.mp4",null,null);
        assertEquals("FB_STARTED",r.getPlatformStatus()); assertEquals("123",r.getContainerId());
        r=c.advance("FACEBOOK_PAGE","42","t","VIDEO","caption","https://cdn.test/a.mp4","123",r.getPlatformStatus());
        assertEquals("FB_UPLOADED",r.getPlatformStatus());
        assertEquals("https://cdn.test/a.mp4",params.get(1).get("file_url"));
        assertTrue(calls.get(1).contains("rupload.facebook.com"));
        r=c.advance("FACEBOOK_PAGE","42","t","VIDEO","caption","https://cdn.test/a.mp4","123",r.getPlatformStatus());
        assertEquals("WAITING",r.getStatus()); assertEquals("FB_FINISH_SUBMITTED",r.getPlatformStatus());
        assertEquals("finish",params.get(2).get("upload_phase")); assertEquals("PUBLISHED",params.get(2).get("video_state"));
        r=c.advance("FACEBOOK_PAGE","42","t","VIDEO","caption","https://cdn.test/a.mp4","123",r.getPlatformStatus());
        assertEquals("WAITING",r.getStatus()); assertEquals("FB_FINISH_SUBMITTED",r.getPlatformStatus());
        r=c.advance("FACEBOOK_PAGE","42","t","VIDEO","caption","https://cdn.test/a.mp4","123",r.getPlatformStatus());
        assertEquals("SUCCESS",r.getStatus()); assertEquals("123",r.getMediaId());
        assertTrue(calls.get(3).startsWith("GET ")); assertTrue(calls.get(4).startsWith("GET "));
        assertTrue(calls.stream().noneMatch(x -> x.endsWith("/photos")));
    }

    @Test void facebookResumesAtFinishAndAmbiguousMutationCannotRetry() {
        responses.add("TIMEOUT");
        TkSocialPlatformException e=assertThrows(TkSocialPlatformException.class,
                () -> client().advance("FACEBOOK_PAGE","42","t","VIDEO","","https://cdn.test/a.mp4","123","FB_UPLOADED"));
        assertTrue(e.isUncertain()); assertFalse(e.isRetryable());
        assertEquals("finish",params.get(0).get("upload_phase")); assertEquals(1,calls.size());
    }

    @Test void facebookProcessingFailureIsNotSuccessfulOrResubmitted() {
        responses.add("{\"status\":{\"video_status\":\"error\",\"processing_phase\":{\"status\":\"error\"}}}");
        assertThrows(TkSocialPlatformException.class,
                () -> client().advance("FACEBOOK_PAGE","42","t","VIDEO","","https://cdn.test/a.mp4","123","FB_FINISH_SUBMITTED"));
        assertEquals(1,calls.size()); assertTrue(calls.get(0).startsWith("GET "));
    }

    @Test void readyVideoWithoutPublishingCompleteRemainsWaiting() {
        responses.add("{\"status\":{\"video_status\":\"ready\",\"publishing_phase\":{\"status\":\"not_started\"}}}");
        assertEquals("WAITING",client().advance("FACEBOOK_PAGE","42","t","VIDEO","","https://cdn.test/a.mp4","123","FB_FINISH_SUBMITTED").getStatus());
    }
}
