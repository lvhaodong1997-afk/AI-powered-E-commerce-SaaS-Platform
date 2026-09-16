package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialReconcileTest {
    private final Deque<String> responses=new ArrayDeque<>();
    private final List<String> urls=new ArrayList<>();

    private TkSocialPlatformClient client() {
        TkSocialProperties config=new TkSocialProperties(); config.setEnabled(true);
        return new TkSocialPlatformClient(config,(method,url,params,token,mutation)->{
            assertEquals("GET",method,"Reconciliation must never create, upload or publish");
            assertFalse(mutation,"Reconciliation must classify failures as read failures");
            urls.add(url);
            String response=responses.removeFirst();
            if ("TIMEOUT".equals(response)) throw TkSocialHttpTransport.failure(503,null,mutation);
            try { return new ObjectMapper().readTree(response); }
            catch(Exception e) { throw new AssertionError(e); }
        });
    }

    @Test void instagramFinishedRemainsWaitingWithoutPublishing() {
        for(String status:Arrays.asList("FINISHED","IN_PROGRESS","UNKNOWN")) {
            responses.add("{\"status_code\":\""+status+"\"}");
            TkSocialPlatformClient.PublishResult result=client().reconcile("INSTAGRAM","42","token","123");
            assertEquals("WAITING",result.getStatus()); assertEquals("123",result.getContainerId());
            assertEquals(status,result.getPlatformStatus()); assertNull(result.getMediaId());
        }
        assertEquals(3,urls.size());
        assertTrue(urls.stream().allMatch(url->url.endsWith("/123") && url.contains("graph.instagram.com/")));
    }

    @Test void instagramPublishedConfirmsSuccessWithoutInventingMediaIdOrUrl() {
        responses.add("{\"status_code\":\"PUBLISHED\"}");
        TkSocialPlatformClient.PublishResult result=client().reconcile("INSTAGRAM","42","token","123");
        assertEquals("SUCCESS",result.getStatus()); assertEquals("123",result.getContainerId());
        assertEquals("PUBLISHED",result.getPlatformStatus());
        assertNull(result.getMediaId()); assertNull(result.getPostId()); assertNull(result.getPublishUrl());
        assertEquals(1,urls.size());
    }

    @Test void instagramDefinitiveFailuresKeepSameTerminalErrorCodes() {
        for(String status:Arrays.asList("ERROR","EXPIRED")) {
            responses.add("{\"status_code\":\""+status+"\"}");
            TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                    ()->client().reconcile("INSTAGRAM","42","token","123"));
            assertEquals("IG_CONTAINER_"+status,error.getCode());
            assertFalse(error.isRetryable()); assertFalse(error.isUncertain());
        }
        assertEquals(2,urls.size());
    }

    @Test void facebookConfirmedPublicationReconcilesTimedOutFinishWithSameIds() {
        responses.add("{\"status\":{\"video_status\":\"ready\",\"publishing_phase\":{\"status\":\"complete\"}}}");
        TkSocialPlatformClient.PublishResult result=client().reconcile("FACEBOOK_PAGE","42","token","123");
        assertEquals("SUCCESS",result.getStatus()); assertEquals("123",result.getContainerId());
        assertEquals("123",result.getMediaId()); assertEquals("123",result.getPostId());
        assertEquals("https://www.facebook.com/reel/123",result.getPublishUrl());
        assertEquals("PUBLISHED",result.getPlatformStatus()); assertEquals(1,urls.size());
    }

    @Test void facebookPrefinishAndProcessingStatesOnlyPollWithoutFinish() {
        responses.add("{\"status\":{\"video_status\":\"processing\",\"uploading_phase\":{\"status\":\"not_started\"},\"publishing_phase\":{\"status\":\"not_started\"}}}");
        responses.add("{\"status\":{\"video_status\":\"ready\",\"publishing_phase\":{\"status\":\"not_started\"}}}");
        for(int i=0;i<2;i++) {
            TkSocialPlatformClient.PublishResult result=client().reconcile("FACEBOOK_PAGE","42","token","123");
            assertEquals("WAITING",result.getStatus()); assertEquals("123",result.getContainerId());
            assertEquals("FB_RECONCILING",result.getPlatformStatus()); assertNull(result.getMediaId());
        }
        assertEquals(2,urls.size());
        assertTrue(urls.stream().allMatch(url->url.endsWith("/123") && url.contains("graph.facebook.com/")));
    }

    @Test void facebookFailedPhasesAndVideoErrorRemainDefinitive() {
        for(String phase:Arrays.asList("uploading_phase","processing_phase","publishing_phase")) {
            responses.add("{\"status\":{\""+phase+"\":{\"status\":\"error\"}}}");
            TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                    ()->client().reconcile("FACEBOOK_PAGE","42","token","123"));
            assertEquals("FB_VIDEO_PROCESSING_FAILED",error.getCode()); assertFalse(error.isUncertain());
        }
        responses.add("{\"status\":{\"video_status\":\"error\"}}");
        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().reconcile("FACEBOOK_PAGE","42","token","123"));
        assertEquals("FB_VIDEO_ERROR",error.getCode()); assertFalse(error.isUncertain());
        assertEquals(4,urls.size());
    }

    @Test void transientReadFailureMayRetryWithoutAmbiguousMutation() {
        responses.add("TIMEOUT");
        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().reconcile("FACEBOOK_PAGE","42","token","123"));
        assertTrue(error.isRetryable()); assertFalse(error.isUncertain()); assertEquals(1,urls.size());
    }
}
