package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.service.social.platform.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Read-only Meta adapters. Each optional field/metric is isolated from the others. */
@Component
public class TkSocialStatsCollector {
    private final TkSocialPlatformClient client;
    private final Map<String,TkSocialStatsMetric> previous;
    @org.springframework.beans.factory.annotation.Autowired
    public TkSocialStatsCollector(TkSocialPlatformClient client) { this.client=client; this.previous=Collections.emptyMap(); }

    private TkSocialStatsCollector(TkSocialPlatformClient client,TkSocialAccountDO account,List<TkSocialStatsMetric> previous) {
        this.client=client; this.previous=new HashMap<>();
        for(TkSocialStatsMetric metric:previous) {
            // Reauthorization invalidates negative permission cache immediately.
            if(account.getLastAuthTime()!=null && (metric.getLastAttemptTime()==null || account.getLastAuthTime().isAfter(metric.getLastAttemptTime()))) continue;
            this.previous.put(metric.getKey(),metric);
        }
    }

    public List<TkSocialStatsMetric> account(TkSocialAccountDO account,String token,List<TkSocialStatsMetric> previous) {
        return new TkSocialStatsCollector(client,account,previous).account(account,token);
    }

    public List<TkSocialStatsMetric> media(TkSocialAccountDO account,TkSocialPublishDetailDO detail,String token,String type,List<TkSocialStatsMetric> previous) {
        return new TkSocialStatsCollector(client,account,previous).media(account,detail,token,type);
    }

    public List<TkSocialStatsMetric> account(TkSocialAccountDO account,String token) {
        String platform=account.getPlatform(), id=account.getExternalAccountId();
        requirePlatform(platform);
        List<TkSocialStatsMetric> result=new ArrayList<>();
        result.add(field(platform,id,token,"followers","followers_count","account","followers_count"));
        if ("INSTAGRAM".equals(platform)) {
            result.add(field(platform,id,token,"following","follows_count","account","follows_count"));
            result.add(field(platform,id,token,"mediaCount","media_count","account","media_count"));
        } else {
            result.add(TkSocialStatsMetric.missing("following",null,"account","UNSUPPORTED"));
            result.add(TkSocialStatsMetric.missing("mediaCount",null,"account","UNSUPPORTED"));
        }
        return result;
    }

    public List<TkSocialStatsMetric> media(TkSocialAccountDO account,TkSocialPublishDetailDO detail,String token) {
        return media(account,detail,token,detail.getPublishUrl()!=null && detail.getPublishUrl().contains("facebook.com/reel/")?"VIDEO":null);
    }

    public List<TkSocialStatsMetric> media(TkSocialAccountDO account,TkSocialPublishDetailDO detail,String token,String mediaType) {
        if (!"SUCCESS".equals(detail.getStatus())) throw new IllegalArgumentException("仅已发布成功的明细支持统计");
        String platform=account.getPlatform(); requirePlatform(platform);
        if (!Objects.equals(platform,detail.getPlatform())) throw new IllegalArgumentException("发布平台不匹配");
        if ("FACEBOOK_PAGE".equals(platform) && "VIDEO".equals(mediaType)) return facebookVideo(detail,token);
        String id="INSTAGRAM".equals(platform)?detail.getExternalMediaId():detail.getExternalPostId();
        // Facebook video/photo object can expose basic engagement, but is not a Page post.
        boolean facebookObjectOnly="FACEBOOK_PAGE".equals(platform) && blank(id);
        if (facebookObjectOnly) id=detail.getExternalMediaId();
        if (blank(id)) return unavailableMedia("UNKNOWN","PUBLISHED_ID_MISSING",false);
        List<TkSocialStatsMetric> result=new ArrayList<>();
        if ("INSTAGRAM".equals(platform)) {
            String product=null;
            try { product=client.statisticsFields(platform,id,token,"media_type,media_product_type").path("media_product_type").asText(); }
            catch(TkSocialPlatformException ignored) { /* Basic engagement and isolated insights still work. */ }
            result.add(field(platform,id,token,"likes","like_count","media","like_count"));
            result.add(field(platform,id,token,"comments","comments_count","media","comments_count"));
            result.add(insight(platform,id,token,"views","views"));
            result.add(insight(platform,id,token,"reach","reach"));
            // Stories do not expose saved/shares under the feed/Reels metric contract.
            if ("STORY".equals(product)) {
                result.add(TkSocialStatsMetric.missing("shares",null,"media","UNSUPPORTED"));
                result.add(TkSocialStatsMetric.missing("saves",null,"media","UNSUPPORTED"));
            } else {
                result.add(insight(platform,id,token,"shares","shares"));
                result.add(insight(platform,id,token,"saves","saved"));
            }
        } else {
            result.add(field(platform,id,token,"likes","likes.limit(0).summary(true)","media","likes","summary","total_count"));
            result.add(field(platform,id,token,"comments","comments.limit(0).summary(true)","media","comments","summary","total_count"));
            result.add(field(platform,id,token,"shares","shares","media","shares","count"));
            // Do not send post metrics to a photo/video ID or substitute removed video/impression metrics.
            result.add(facebookObjectOnly?TkSocialStatsMetric.missing("views",null,"media","UNKNOWN"):
                    insight(platform,id,token,"views","post_media_view"));
            result.add(TkSocialStatsMetric.missing("reach",null,"media","UNSUPPORTED"));
            result.add(TkSocialStatsMetric.missing("saves",null,"media","UNSUPPORTED"));
        }
        return result;
    }

    private List<TkSocialStatsMetric> facebookVideo(TkSocialPublishDetailDO detail,String token) {
        String video=detail.getExternalMediaId();
        if (blank(video)) return unavailableMedia("UNKNOWN","PUBLISHED_ID_MISSING",false);
        List<TkSocialStatsMetric> result=new ArrayList<>();
        result.add(field("FACEBOOK_PAGE",video,token,"likes","likes.limit(0).summary(true)","video","likes","summary","total_count"));
        result.add(field("FACEBOOK_PAGE",video,token,"comments","comments.limit(0).summary(true)","video","comments","summary","total_count"));
        result.add(read("views","blue_reels_play_count","video",()->insightValue(
                client.statisticsVideoInsights(video,token,"blue_reels_play_count"),"blue_reels_play_count")));
        result.add(read("reach","total_video_impressions_unique","video",()->insightValue(
                client.statisticsVideoInsights(video,token,"total_video_impressions_unique"),"total_video_impressions_unique")));
        // The publisher historically stores video_id in external_post_id for Reels. Resolve the real post.
        TkSocialStatsMetric shares=read("shares","shares","post",()->{
            String post=client.statisticsFields("FACEBOOK_PAGE",video,token,"post_id").path("post_id").asText(null);
            if (!blank(post) && !post.equals(video)) return new Sample(client.statisticsFields("FACEBOOK_PAGE",post,token,"shares").path("shares").path("count"),"current");
            return new Sample(com.fasterxml.jackson.databind.node.NullNode.getInstance(),"current");
        });
        result.add(shares); result.add(TkSocialStatsMetric.missing("saves",null,"video","UNSUPPORTED"));
        return result;
    }

    private TkSocialStatsMetric field(String platform,String id,String token,String key,String source,String scope,String... path) {
        return read(key,source,scope,()->{
            JsonNode node=client.statisticsFields(platform,id,token,source);
            for(String p:path) node=node.path(p);
            return new Sample(node,"current");
        });
    }

    private TkSocialStatsMetric insight(String platform,String id,String token,String key,String source) {
        return read(key,source,"media",()->insightValue(client.statisticsInsights(platform,id,token,source),source));
    }

    private Sample insightValue(JsonNode result,String source) {
            for(JsonNode item:result.path("data")) {
                if (!source.equals(item.path("name").asText())) continue;
                String period=item.path("period").asText(null);
                if (item.path("total_value").has("value")) return new Sample(item.path("total_value").path("value"),period);
                JsonNode values=item.path("values");
                // Lifetime object insights have one value. Never sum daily windows into lifetime.
                if (values.isArray() && values.size()==1) return new Sample(values.get(0).path("value"),period);
            }
            return new Sample(com.fasterxml.jackson.databind.node.NullNode.getInstance(),null);
    }

    private TkSocialStatsMetric read(String key,String source,String scope,Supplier<Sample> fetch) {
        TkSocialStatsMetric cached=previous.get(key);
        if(cached!=null && cached.getNextAttemptTime()!=null && cached.getNextAttemptTime().isAfter(LocalDateTime.now())) {
            TkSocialStatsMetric copy=new TkSocialStatsMetric(); org.springframework.beans.BeanUtils.copyProperties(cached,copy); return copy;
        }
        TkSocialStatsMetric m=TkSocialStatsMetric.missing(key,source,scope,"UNKNOWN");
        m.setLastAttemptTime(LocalDateTime.now());
        try {
            Sample sample=fetch.get(); JsonNode value=sample.value; m.setPeriod(sample.period);
            if (value!=null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue()>=0) {
                m.setValue(value.longValue()); m.setAvailability("AVAILABLE"); m.setFetchedAt(LocalDateTime.now());
            }
        } catch(TkSocialPlatformException e) {
            m.setAvailability(availability(e)); m.setErrorCode(errorCode(e)); m.setRetryable(e.isRetryable());
            if(!e.isRetryable()) m.setNextAttemptTime(LocalDateTime.now().plusHours(6));
        }
        return m;
    }

    private static final class Sample {
        private final JsonNode value;
        private final String period;
        private Sample(JsonNode value,String period) { this.value=value; this.period=period; }
    }

    public static String availability(TkSocialPlatformException e) {
        if (Arrays.asList("META_10","META_200","PERMISSIONS_MISSING").contains(e.getCode())) return "PERMISSION_REQUIRED";
        if (e.isReauthRequired()) return "AUTH_REQUIRED";
        if ("META_100".equals(e.getCode()) && e.getSubcode()==33) return "OBJECT_UNAVAILABLE";
        if ("META_100".equals(e.getCode())) return "UNSUPPORTED";
        return "ERROR";
    }

    public static String errorCode(TkSocialPlatformException e) { return e.getCode()+(e.getSubcode()==0?"":"_"+e.getSubcode()); }

    public static List<TkSocialStatsMetric> unavailableMedia(String availability,String code,boolean retryable) {
        return unavailable(Arrays.asList("views","likes","comments","shares","saves","reach"),"media",availability,code,retryable);
    }

    public static List<TkSocialStatsMetric> unavailable(List<String> keys,String scope,String availability,String code,boolean retryable) {
        List<TkSocialStatsMetric> result=new ArrayList<>();
        for(String key:keys) {
            TkSocialStatsMetric m=TkSocialStatsMetric.missing(key,null,scope,availability);
            m.setErrorCode(code); m.setRetryable(retryable); result.add(m);
        }
        return result;
    }
    private static void requirePlatform(String platform) {
        if (!Arrays.asList("INSTAGRAM","FACEBOOK_PAGE").contains(platform)) throw new IllegalArgumentException("不支持的平台");
    }
    private static boolean blank(String value) { return value==null || value.trim().isEmpty(); }
}
