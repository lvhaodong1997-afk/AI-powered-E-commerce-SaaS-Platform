package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialPlatformClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final List<String> urls = new ArrayList<>();
    private final List<Map<String, String>> parameters = new ArrayList<>();
    private final Deque<Object> responses = new ArrayDeque<>();
    private TkSocialPlatformClient client() {
        TkSocialProperties p = new TkSocialProperties();
        p.setEnabled(true);
        p.getInstagram().setAppId("ig-app"); p.getInstagram().setAppSecret("ig-secret");
        p.getFacebook().setAppId("fb-app"); p.getFacebook().setAppSecret("fb-secret");
        return new TkSocialPlatformClient(p, (method, url, params, token, mutation) -> {
            urls.add(method + " " + url); parameters.add(new HashMap<>(params));
            Object response=responses.removeFirst();
            if (response instanceof RuntimeException) throw (RuntimeException)response;
            try { return json.readTree((String)response); } catch (Exception e) { throw new AssertionError(e); }
        });
    }

    @Test void instagramCreatesContainerWithoutPublishingInSameAdvance() {
        responses.add("{\"id\":\"100\"}");
        TkSocialPlatformClient.PublishResult r = client().advance("INSTAGRAM", "42", "secret", "IMAGE", "caption", "https://cdn.example/a.jpg", null, null);
        assertEquals("WAITING", r.getStatus()); assertEquals("100", r.getContainerId());
        assertEquals(1, urls.size()); assertTrue(urls.get(0).contains("graph.instagram.com/"));
        assertEquals("https://cdn.example/a.jpg", parameters.get(0).get("image_url"));
    }

    @Test void instagramResumesExistingContainerAndPublishesOnlyWhenFinished() {
        responses.add("{\"status_code\":\"FINISHED\"}");
        responses.add("{\"id\":\"200\"}");
        responses.add("{\"permalink\":\"https://www.instagram.com/p/example/\"}");
        TkSocialPlatformClient.PublishResult r = client().advance("INSTAGRAM", "42", "secret", "IMAGE", "caption", "https://cdn.example/a.jpg", "100", "IN_PROGRESS");
        assertEquals("SUCCESS", r.getStatus()); assertEquals("200", r.getMediaId());
        assertTrue(urls.get(0).contains("/100")); assertTrue(urls.get(1).endsWith("/42/media_publish"));
        assertEquals("100", parameters.get(1).get("creation_id"));
    }

    @Test void alreadyPublishedContainerIsNeverSubmittedAgain() {
        responses.add("{\"status_code\":\"PUBLISHED\"}");
        TkSocialPlatformException e = assertThrows(TkSocialPlatformException.class,
                () -> client().advance("INSTAGRAM", "42", "secret", "IMAGE", "caption", "https://cdn.example/a.jpg", "100", "IN_PROGRESS"));
        assertTrue(e.isUncertain()); assertEquals(1, urls.size());
    }

    @Test void pageCandidatesFollowCursorsWithoutTrustingNextUrlAndRequireContentTask() {
        responses.add("{\"data\":[{\"id\":\"1\",\"name\":\"read only\",\"access_token\":\"x\",\"tasks\":[\"ANALYZE\"]}],\"paging\":{\"next\":\"https://evil.example/steal\",\"cursors\":{\"after\":\"cursor-2\"}}}");
        responses.add("{\"data\":[{\"id\":\"2\",\"name\":\"writer\",\"access_token\":\"page-secret\",\"tasks\":[\"CREATE_CONTENT\"]}]}");
        List<TkSocialPlatformClient.PageCandidate> pages = client().facebookPages("user-secret");
        assertEquals(1, pages.size()); assertEquals("2", pages.get(0).getId());
        assertEquals("page-secret", pages.get(0).getAccessToken());
        assertEquals("cursor-2", parameters.get(1).get("after"));
        assertTrue(urls.stream().allMatch(x -> x.contains("graph.facebook.com/")));
    }

    @Test void instagramExchangesShortTokenWithInstagramCredentialsThenLongLived() {
        responses.add("{\"access_token\":\"short\",\"user_id\":\"42\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\"]}");
        responses.add("{\"access_token\":\"long\",\"expires_in\":5184000}");
        responses.add("{\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"MEDIA_CREATOR\"}");
        TkSocialPlatformClient.Authorization a = client().authorizeInstagram("code", "https://app.example/callback");
        assertEquals("long", a.getAccessToken()); assertEquals("42", a.getExternalId());
        assertEquals("ig-app", parameters.get(0).get("client_id"));
        assertEquals("ig-secret", parameters.get(0).get("client_secret"));
        assertEquals("ig_exchange_token", parameters.get(1).get("grant_type"));
        assertFalse(parameters.get(1).containsValue("fb-secret"));
    }

    @Test void instagramRefreshUsesExistingLongLivedAccessTokenNotRefreshToken() {
        responses.add("{\"access_token\":\"renewed\",\"expires_in\":5184000}");
        assertEquals("renewed", client().refreshInstagram("old").getAccessToken());
        assertEquals("ig_refresh_token", parameters.get(0).get("grant_type"));
        assertEquals("old", parameters.get(0).get("access_token"));
    }

    @Test void instagramAcceptsSingletonWrappedTokenWithoutLosingIdentityOrPermissions() {
        responses.add("{\"data\":[{\"access_token\":\"wrapped-short\",\"user_id\":\"42\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\"]}]}");
        responses.add("{\"access_token\":\"long\",\"expires_in\":5184000}");
        responses.add("{\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"MEDIA_CREATOR\"}");
        TkSocialPlatformClient.Authorization result=client().authorizeInstagram("code","https://app.example/callback");
        assertEquals("long",result.getAccessToken()); assertEquals("42",result.getExternalId());
        assertEquals("instagram_business_basic,instagram_business_content_publish",result.getScopes());
        assertEquals("wrapped-short",parameters.get(1).get("access_token"));
        assertEquals(3,urls.size());
    }

    @Test void bothInstagramTokenShapesRejectMismatchedIdentity() {
        for (boolean wrapped:new boolean[]{false,true}) {
            String token="{\"access_token\":\"short\",\"user_id\":\"99\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\"]}";
            responses.add(wrapped?"{\"data\":["+token+"]}":token);
            responses.add("{\"access_token\":\"long\",\"expires_in\":5184000}");
            responses.add("{\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"BUSINESS\"}");
            TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                    ()->client().authorizeInstagram("code","https://app.example/callback"));
            assertEquals("IG_IDENTITY_MISMATCH",error.getCode());
            assertTrue(responses.isEmpty());
        }
    }

    @Test void bothInstagramTokenShapesRejectMissingPublishPermission() {
        for (boolean wrapped:new boolean[]{false,true}) {
            String token="{\"access_token\":\"short\",\"user_id\":\"42\",\"permissions\":[\"instagram_business_basic\"]}";
            responses.add(wrapped?"{\"data\":["+token+"]}":token);
            responses.add("{\"access_token\":\"long\",\"expires_in\":5184000}");
            responses.add("{\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"BUSINESS\"}");
            TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                    ()->client().authorizeInstagram("code","https://app.example/callback"));
            assertEquals("PERMISSIONS_MISSING",error.getCode()); assertTrue(error.isReauthRequired());
            assertTrue(responses.isEmpty());
        }
    }

    @Test void malformedOrAmbiguousInstagramWrappersStopBeforeLongLivedExchange() {
        for (String response:Arrays.asList("{\"data\":[]}",
                "{\"data\":[{\"access_token\":\"a\"},{\"access_token\":\"b\"}]}",
                "{\"data\":null}","{\"data\":{}}","{\"data\":[\"not-an-object\"]}")) {
            responses.add(response);
            int previous=urls.size();
            TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                    ()->client().authorizeInstagram("code","https://app.example/callback"));
            assertEquals("IG_TOKEN_RESPONSE_INVALID",error.getCode());
            assertEquals(previous+1,urls.size()); assertTrue(responses.isEmpty());
        }
    }

    @Test void instagramLongTokenFailureCarriesSafeStageWithoutChangingProviderCode() {
        responses.add("{\"access_token\":\"short\",\"user_id\":\"42\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\"]}");
        responses.add(new TkSocialPlatformException("HTTP_400","provider-message-with-secret",false,false,false));

        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().authorizeInstagram("callback-code-secret","https://app.example/callback"));

        assertEquals("INSTAGRAM_LONG_TOKEN",error.getStage());
        assertEquals("HTTP_400",error.getCode());
        assertEquals(2,urls.size());
    }

    @Test void mutationErrorsAreSanitizedAndCannotBeBlindlyRetried() {
        TkSocialPlatformException timeout = TkSocialHttpTransport.failure(503, null, true);
        assertTrue(timeout.isUncertain()); assertFalse(timeout.isRetryable());
        assertFalse(timeout.getMessage().contains("secret"));
        assertTrue(TkSocialHttpTransport.failure(429, null, true).isRetryable());
        assertFalse(TkSocialHttpTransport.failure(429, null, true).isUncertain());
    }
}
