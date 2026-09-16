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
        responses.add("{\"id\":\"42\",\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"MEDIA_CREATOR\"}");
        TkSocialPlatformClient.Authorization a = client().authorizeInstagram("code", "https://app.example/callback");
        assertEquals("long", a.getAccessToken()); assertEquals("42", a.getExternalId()); assertEquals("42", a.getProviderUserId());
        assertEquals("ig-app", parameters.get(0).get("client_id"));
        assertEquals("ig-secret", parameters.get(0).get("client_secret"));
        assertEquals("ig_exchange_token", parameters.get(1).get("grant_type"));
        assertFalse(parameters.get(1).containsValue("fb-secret"));
        assertEquals("id,user_id,username,account_type", parameters.get(2).get("fields"));
    }

    @Test void facebookAcceptsLongTokenWithoutExpiresInForPageAuthorization() {
        responses.add("{\"access_token\":\"short\"}");
        responses.add("{\"access_token\":\"long\"}");
        responses.add("{\"id\":\"100\",\"name\":\"owner\"}");
        responses.add("{\"data\":[{\"permission\":\"pages_show_list\",\"status\":\"granted\"},{\"permission\":\"pages_read_engagement\",\"status\":\"granted\"},{\"permission\":\"pages_manage_posts\",\"status\":\"granted\"}]}");
        responses.add("{\"data\":[{\"id\":\"200\",\"name\":\"page\",\"access_token\":\"page-token\",\"tasks\":[\"CREATE_CONTENT\"]}]}");

        TkSocialPlatformClient.Authorization authorization=client().authorizeFacebook("code","https://app.example/callback");

        assertEquals("long",authorization.getAccessToken());
        assertNull(authorization.getExpiresAt());
        assertEquals("100",authorization.getExternalId());
        assertEquals(1,authorization.getPages().size());
        assertEquals("200",authorization.getPages().get(0).getId());
    }

    @Test void facebookKeepsReturnedTokenExpiry() {
        responses.add("{\"access_token\":\"short\"}");
        responses.add("{\"access_token\":\"long\",\"expires_in\":3600}");
        responses.add("{\"id\":\"100\",\"name\":\"owner\"}");
        responses.add("{\"data\":[\"pages_show_list\",\"pages_read_engagement\",\"pages_manage_posts\"]}");
        responses.add("{\"data\":[]}");
        java.time.LocalDateTime before=java.time.LocalDateTime.now();
        TkSocialPlatformClient.Authorization authorization=client().authorizeFacebook("code","https://app.example/callback");
        assertFalse(authorization.getExpiresAt().isBefore(before.plusSeconds(3600)));
        assertFalse(authorization.getExpiresAt().isAfter(java.time.LocalDateTime.now().plusSeconds(3600)));
        assertTrue(responses.isEmpty());
    }

    @Test void facebookRejectsMissingAccessTokenBeforeProfileLookup() {
        responses.add("{\"access_token\":\"short\"}");
        responses.add("{\"expires_in\":3600}");
        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().authorizeFacebook("code","https://app.example/callback"));
        assertEquals("RESPONSE_INCOMPLETE",error.getCode());
        assertEquals(2,urls.size());
    }

    @Test void facebookWithoutExpiryStillRejectsMissingPublishPermission() {
        responses.add("{\"access_token\":\"short\"}");
        responses.add("{\"access_token\":\"long\"}");
        responses.add("{\"id\":\"100\",\"name\":\"owner\"}");
        responses.add("{\"data\":[{\"permission\":\"pages_show_list\",\"status\":\"granted\"},{\"permission\":\"pages_read_engagement\",\"status\":\"granted\"},{\"permission\":\"pages_manage_posts\",\"status\":\"declined\"}]}");
        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().authorizeFacebook("code","https://app.example/callback"));
        assertEquals("PERMISSIONS_MISSING",error.getCode());
        assertEquals(4,urls.size());
    }

    @Test void instagramStillRejectsLongTokenWithoutExpiry() {
        responses.add("{\"access_token\":\"short\"}");
        responses.add("{\"access_token\":\"long\"}");
        TkSocialPlatformException error=assertThrows(TkSocialPlatformException.class,
                ()->client().authorizeInstagram("code","https://app.example/callback"));
        assertEquals("TOKEN_EXPIRY_MISSING",error.getCode());
        assertEquals("INSTAGRAM_LONG_TOKEN",error.getStage());
        assertEquals(2,urls.size());
    }

    @Test void instagramAcceptsTokenIdentityMatchingProfileIdAndKeepsPublishingIdentitySeparate() {
        responses.add("{\"access_token\":\"short\",\"user_id\":\"100\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\"]}");
        responses.add("{\"access_token\":\"long\",\"expires_in\":5184000}");
        responses.add("{\"id\":\"100\",\"user_id\":\"200\",\"username\":\"creator\",\"account_type\":\"BUSINESS\"}");

        TkSocialPlatformClient.Authorization authorization=client().authorizeInstagram("code","https://app.example/callback");

        assertEquals("200",authorization.getExternalId());
        assertEquals("100",authorization.getProviderUserId());
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
            responses.add("{\"id\":\"41\",\"user_id\":\"42\",\"username\":\"creator\",\"account_type\":\"BUSINESS\"}");
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
