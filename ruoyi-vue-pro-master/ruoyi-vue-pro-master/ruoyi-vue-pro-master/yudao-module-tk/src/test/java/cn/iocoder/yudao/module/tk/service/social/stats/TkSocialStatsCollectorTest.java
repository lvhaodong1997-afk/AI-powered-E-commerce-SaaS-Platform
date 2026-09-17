package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import cn.iocoder.yudao.module.tk.service.social.platform.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialStatsCollectorTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final List<String> calls = new ArrayList<>();
    private final Map<String,Object> responses = new HashMap<>();

    private JsonNode collect(String platform, TkSocialPublishDetailDO detail) throws Exception {
        TkSocialProperties properties = new TkSocialProperties(); properties.setEnabled(true);
        TkSocialPlatformClient client = new TkSocialPlatformClient(properties, (method,url,params,token,mutation) -> {
            assertEquals("GET",method); assertFalse(mutation);
            assertTrue(url.contains("INSTAGRAM".equals(platform)?"graph.instagram.com/":"graph.facebook.com/"));
            String key=url.substring(url.lastIndexOf('/')+1)+":"+params.getOrDefault("fields",params.get("metric"));
            calls.add(key);
            Object answer=responses.getOrDefault(key,"{}");
            if(answer instanceof RuntimeException) throw (RuntimeException)answer;
            try { return json.readTree((String)answer); } catch(Exception e) { throw new AssertionError(e); }
        });
        // Reflection permits a real RED assertion before the new backend class exists.
        Class<?> type=assertDoesNotThrow(()->Class.forName("cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsCollector"),
                "Statistics collector is not implemented");
        Object collector=type.getConstructor(TkSocialPlatformClient.class).newInstance(client);
        TkSocialAccountDO account=new TkSocialAccountDO(); account.setPlatform(platform); account.setExternalAccountId("42");
        Object metrics;
        try {
            metrics=detail==null?type.getMethod("account",TkSocialAccountDO.class,String.class).invoke(collector,account,"fixture-token"):
                    type.getMethod("media",TkSocialAccountDO.class,TkSocialPublishDetailDO.class,String.class)
                            .invoke(collector,account,detail,"fixture-token");
        } catch(InvocationTargetException e) { throw (RuntimeException)e.getCause(); }
        return json.valueToTree(metrics);
    }

    private JsonNode metric(JsonNode list,String key) {
        for(JsonNode m:list) if(key.equals(m.path("key").asText())) return m;
        fail("Missing metric: "+key); return null;
    }

    @Test void accountKeepsTrueZeroAndUnknownDistinctAndIsolatesUnsupportedField() throws Exception {
        responses.put("42:followers_count","{\"followers_count\":0}");
        responses.put("42:follows_count",new TkSocialPlatformException("META_100","unsupported",false,false,false));
        responses.put("42:media_count","{}");
        JsonNode result=collect("INSTAGRAM",null);
        assertEquals(0,metric(result,"followers").path("value").longValue());
        assertEquals("AVAILABLE",metric(result,"followers").path("availability").asText());
        assertEquals("current",metric(result,"followers").path("period").asText());
        assertTrue(metric(result,"following").path("value").isNull());
        assertEquals("UNSUPPORTED",metric(result,"following").path("availability").asText());
        assertEquals("UNKNOWN",metric(result,"mediaCount").path("availability").asText());
    }

    @Test void facebookAccountDoesNotInventFollowingOrMediaCount() throws Exception {
        responses.put("42:followers_count","{\"followers_count\":4000000000}");
        JsonNode result=collect("FACEBOOK_PAGE",null);
        assertEquals(4000000000L,metric(result,"followers").path("value").longValue());
        assertEquals("UNSUPPORTED",metric(result,"following").path("availability").asText());
        assertEquals("UNSUPPORTED",metric(result,"mediaCount").path("availability").asText());
        assertEquals(1,calls.size());
    }

    @Test void instagramUsesPublishedMediaNotContainerAndKeepsPartialResults() throws Exception {
        TkSocialPublishDetailDO d=detail("INSTAGRAM");
        responses.put("200:media_type,media_product_type","{\"media_type\":\"VIDEO\",\"media_product_type\":\"REELS\"}");
        responses.put("200:like_count","{\"like_count\":9}");
        responses.put("200:comments_count","{\"comments_count\":0}");
        responses.put("insights:views","{\"data\":[{\"name\":\"views\",\"period\":\"lifetime\",\"values\":[{\"value\":12}]}]}");
        responses.put("insights:saved",new TkSocialPlatformException("META_10","permission",false,true,false));
        JsonNode result=collect("INSTAGRAM",d);
        assertEquals(12,metric(result,"views").path("value").longValue());
        assertEquals("lifetime",metric(result,"views").path("period").asText());
        assertEquals(9,metric(result,"likes").path("value").longValue());
        assertEquals("current",metric(result,"likes").path("period").asText());
        assertEquals("PERMISSION_REQUIRED",metric(result,"saves").path("availability").asText());
        assertTrue(calls.stream().noneMatch(s->s.startsWith("100:")));
        assertTrue(calls.contains("insights:shares"));
    }

    @Test void facebookPostUsesPostIdAndActualLikesNotAllReactions() throws Exception {
        TkSocialPublishDetailDO d=detail("FACEBOOK_PAGE");
        responses.put("42_300:likes.limit(0).summary(true)","{\"likes\":{\"summary\":{\"total_count\":5}}}");
        responses.put("42_300:comments.limit(0).summary(true)","{\"comments\":{\"summary\":{\"total_count\":0}}}");
        responses.put("42_300:shares","{}");
        responses.put("insights:post_media_view","{\"data\":[{\"name\":\"post_media_view\",\"values\":[{\"value\":7}]}]}");
        JsonNode result=collect("FACEBOOK_PAGE",d);
        assertEquals(5,metric(result,"likes").path("value").longValue());
        assertEquals(7,metric(result,"views").path("value").longValue());
        assertTrue(metric(result,"views").path("period").isNull(),"Never invent a lifetime window missing from the response");
        assertEquals("UNKNOWN",metric(result,"shares").path("availability").asText());
        assertEquals("UNSUPPORTED",metric(result,"saves").path("availability").asText());
        assertTrue(calls.stream().noneMatch(s->s.startsWith("200:")||s.contains("reactions")));
    }

    @Test void containerOnlySuccessDoesNotQueryContainerAsMedia() throws Exception {
        TkSocialPublishDetailDO d=detail("INSTAGRAM"); d.setExternalMediaId(null);
        JsonNode result=collect("INSTAGRAM",d);
        assertTrue(calls.isEmpty());
        assertEquals("UNKNOWN",metric(result,"views").path("availability").asText());
    }

    @Test void failedPublishIsNeverFetched() {
        TkSocialPublishDetailDO d=detail("INSTAGRAM"); d.setStatus("FAILED");
        assertThrows(IllegalArgumentException.class,()->collect("INSTAGRAM",d));
        assertTrue(calls.isEmpty());
    }

    @Test void malformedCountsNeverBecomeZero() throws Exception {
        responses.put("42:followers_count","{\"followers_count\":\"not a number\"}");
        responses.put("42:follows_count","{\"follows_count\":-1}");
        responses.put("42:media_count","{\"media_count\":1.5}");
        JsonNode result=collect("INSTAGRAM",null);
        for(JsonNode m:result) { assertTrue(m.path("value").isNull()); assertEquals("UNKNOWN",m.path("availability").asText()); }
    }

    @Test void unavailablePublishedObjectIsDistinctFromUnsupportedMetric() throws Exception {
        TkSocialPublishDetailDO d=detail("INSTAGRAM");
        Object error=TkSocialHttpTransport.failure(400,json.readTree("{\"error\":{\"code\":100,\"error_subcode\":33}}"),false);
        responses.put("200:like_count",error);
        JsonNode result=collect("INSTAGRAM",d);
        assertEquals("OBJECT_UNAVAILABLE",metric(result,"likes").path("availability").asText());
        assertTrue(metric(result,"likes").path("value").isNull());
        assertEquals("META_100_33",metric(result,"likes").path("errorCode").asText());
    }

    @Test void facebookReelsKeepsVideoMetricsWhenActualPostRequiresAdditionalPermission() throws Exception {
        TkSocialPublishDetailDO d=detail("FACEBOOK_PAGE");
        d.setExternalPostId("200"); d.setPublishUrl("https://www.facebook.com/reel/200");
        responses.put("200:post_id","{\"post_id\":\"42_900\"}");
        responses.put("200:likes.limit(0).summary(true)","{\"likes\":{\"summary\":{\"total_count\":0}}}");
        responses.put("200:comments.limit(0).summary(true)","{\"comments\":{\"summary\":{\"total_count\":0}}}");
        responses.put("video_insights:blue_reels_play_count","{\"data\":[{\"name\":\"blue_reels_play_count\",\"values\":[{\"value\":0}]}]}");
        responses.put("42_900:shares",new TkSocialPlatformException("META_10","permission",false,true,false));
        JsonNode result=collect("FACEBOOK_PAGE",d);
        assertEquals("AVAILABLE",metric(result,"views").path("availability").asText());
        assertEquals(0,metric(result,"views").path("value").longValue());
        assertEquals("blue_reels_play_count",metric(result,"views").path("sourceMetric").asText());
        assertEquals("AVAILABLE",metric(result,"likes").path("availability").asText());
        assertEquals("PERMISSION_REQUIRED",metric(result,"shares").path("availability").asText());
        assertFalse(calls.contains("200:shares")); assertTrue(calls.contains("42_900:shares"));
    }

    @Test void permissionFailureIsNotRetriedDuringNextMediaPollButGoodFieldsStillRefresh() {
        TkSocialProperties p=new TkSocialProperties(); p.setEnabled(true);
        int[] shareCalls={0};
        TkSocialPlatformClient client=new TkSocialPlatformClient(p,(method,url,params,token,mutation)->{
            try {
                if("shares".equals(params.get("metric"))) {
                    shareCalls[0]++; throw new TkSocialPlatformException("META_10","permission",false,true,false);
                }
                if("like_count".equals(params.get("fields"))) return json.readTree("{\"like_count\":4}");
                return json.readTree("{}");
            } catch(java.io.IOException e) { throw new AssertionError(e); }
        });
        TkSocialAccountDO account=new TkSocialAccountDO(); account.setPlatform("INSTAGRAM"); account.setExternalAccountId("42");
        TkSocialStatsCollector collector=new TkSocialStatsCollector(client);
        List<TkSocialStatsMetric> first=collector.media(account,detail("INSTAGRAM"),"fixture-token");
        List<TkSocialStatsMetric> next=collector.media(account,detail("INSTAGRAM"),"fixture-token","VIDEO",first);
        assertEquals(1,shareCalls[0],"Permission failures wait six hours between calls");
        assertEquals(4L,next.stream().filter(m->"likes".equals(m.getKey())).findFirst().get().getValue());
        account.setLastAuthTime(java.time.LocalDateTime.now().plusSeconds(1));
        collector.media(account,detail("INSTAGRAM"),"new-fixture-token","VIDEO",next);
        assertEquals(2,shareCalls[0],"Reauthorization invalidates permission cache");
    }

    private TkSocialPublishDetailDO detail(String platform) {
        TkSocialPublishDetailDO d=new TkSocialPublishDetailDO(); d.setPlatform(platform); d.setStatus("SUCCESS");
        d.setExternalContainerId("100"); d.setExternalMediaId("200"); d.setExternalPostId("42_300"); return d;
    }
}
