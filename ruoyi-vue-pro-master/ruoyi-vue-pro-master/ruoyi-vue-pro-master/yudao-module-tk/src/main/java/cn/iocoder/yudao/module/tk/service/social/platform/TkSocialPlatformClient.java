package cn.iocoder.yudao.module.tk.service.social.platform;

import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

@Component
public class TkSocialPlatformClient {
    public interface Transport {
        JsonNode request(String method, String url, Map<String,String> params, String token, boolean mutation);
    }
    private final TkSocialProperties properties;
    private final Transport transport;
    @Autowired public TkSocialPlatformClient(TkSocialProperties properties) { this(properties, new TkSocialHttpTransport(properties)); }
    public TkSocialPlatformClient(TkSocialProperties properties, Transport transport) {
        this.properties=properties; this.transport=transport;
    }

    public String authorizeUrl(String platform, String state) {
        TkSocialProperties.Credentials c=properties.credentials(platform);
        UriComponentsBuilder u=UriComponentsBuilder.fromHttpUrl("INSTAGRAM".equals(platform)
                ? "https://www.instagram.com/oauth/authorize" : "https://www.facebook.com/"+properties.version()+"/dialog/oauth");
        u.queryParam("client_id",c.getAppId()).queryParam("redirect_uri",c.getRedirectUri())
                .queryParam("response_type","code").queryParam("state",state);
        if ("INSTAGRAM".equals(platform)) u.queryParam("scope","instagram_business_basic,instagram_business_content_publish")
                .queryParam("enable_fb_login","0").queryParam("force_authentication","1");
        else u.queryParam("config_id",c.getConfigId()).queryParam("override_default_response_type","true");
        return u.build().encode().toUriString();
    }

    public Authorization authorizeInstagram(String code, String redirectUri) {
        TkSocialProperties.Credentials c=properties.getInstagram();
        final JsonNode shortToken=instagramStage("INSTAGRAM_SHORT_TOKEN",()->{
            JsonNode result=call("POST","https://api.instagram.com/oauth/access_token",
                    map("client_id",c.getAppId(),"client_secret",c.getAppSecret(),"grant_type","authorization_code",
                            "redirect_uri",redirectUri,"code",code),null,false);
            // Defensive compatibility only: preserve the same identity/permission checks for either response shape.
            if (result.has("data")) {
                JsonNode data=result.get("data");
                if (!data.isArray() || data.size()!=1 || !data.get(0).isObject())
                    throw rejected("IG_TOKEN_RESPONSE_INVALID","Instagram 令牌响应格式无效");
                result=data.get(0);
            }
            required(result,"access_token",false);
            return result;
        });
        final String shortAccessToken=shortToken.path("access_token").asText();
        final Authorization a=instagramStage("INSTAGRAM_LONG_TOKEN",()->token(call("GET",
                "https://graph.instagram.com/access_token",
                map("grant_type","ig_exchange_token","client_secret",c.getAppSecret(),
                        "access_token",shortAccessToken),null,false)));
        instagramStage("INSTAGRAM_PROFILE",()->{
            JsonNode profile=call("GET",ig("me"),map("fields","id,user_id,username,account_type"),a.accessToken,false);
            a.externalId=profile.path("user_id").asText(profile.path("id").asText());
            id(a.externalId);
            a.providerUserId=profile.path("id").asText(a.externalId);
            id(a.providerUserId);
            a.accountName=profile.path("username").asText(); a.username=a.accountName;
            a.accountType=profile.path("account_type").asText();
            if (!Arrays.asList("BUSINESS","MEDIA_CREATOR","CREATOR").contains(a.accountType))
                throw rejected("IG_PROFESSIONAL_REQUIRED","需要 Instagram 专业账号");
            String authorizedId=shortToken.path("user_id").asText();
            if (!authorizedId.isEmpty() && !authorizedId.equals(a.providerUserId) && !authorizedId.equals(a.externalId))
                throw rejected("IG_IDENTITY_MISMATCH","Instagram 授权身份不一致");
            return a;
        });
        instagramStage("INSTAGRAM_PERMISSIONS",()->{
            // Token exchange responses may omit permissions. Verify granted permissions using the token.
            JsonNode granted=shortToken.get("permissions");
            if (granted == null) granted=call("GET",ig("me/permissions"),map(),a.accessToken,false).path("data");
            requirePermissions(granted,Arrays.asList("instagram_business_basic","instagram_business_content_publish"));
            return null;
        });
        a.scopes="instagram_business_basic,instagram_business_content_publish";
        return a;
    }

    public Authorization refreshInstagram(String accessToken) {
        return token(call("GET","https://graph.instagram.com/refresh_access_token",
                map("grant_type","ig_refresh_token","access_token",accessToken),null,false));
    }

    public Authorization authorizeFacebook(String code, String redirectUri) {
        TkSocialProperties.Credentials c=properties.getFacebook();
        JsonNode shortToken=call("GET",fb("oauth/access_token"),
                map("client_id",c.getAppId(),"client_secret",c.getAppSecret(),"redirect_uri",redirectUri,"code",code),null,false);
        JsonNode longToken=call("GET",fb("oauth/access_token"),map("grant_type","fb_exchange_token",
                "client_id",c.getAppId(),"client_secret",c.getAppSecret(),"fb_exchange_token",required(shortToken,"access_token",false)),null,false);
        Authorization a=token(longToken);
        JsonNode profile=call("GET",fb("me"),map("fields","id,name"),a.accessToken,false);
        a.externalId=required(profile,"id",false); a.accountName=profile.path("name").asText();
        requirePermissions(call("GET",fb("me/permissions"),map(),a.accessToken,false).path("data"),
                Arrays.asList("pages_show_list","pages_read_engagement","pages_manage_posts"));
        a.pages=facebookPages(a.accessToken);
        a.scopes="pages_show_list,pages_read_engagement,pages_manage_posts";
        return a;
    }

    public List<PageCandidate> facebookPages(String userToken) {
        List<PageCandidate> pages=new ArrayList<>();
        Set<String> visited=new HashSet<>();
        String cursor=null;
        for (int page=0;page<100;page++) {
            Map<String,String> params=map("fields","id,name,access_token,tasks","limit","100");
            if (cursor!=null) params.put("after",cursor);
            JsonNode result=call("GET",fb("me/accounts"),params,userToken,false);
            for (JsonNode entry:result.path("data")) {
                List<String> tasks=new ArrayList<>(); entry.path("tasks").forEach(x->tasks.add(x.asText()));
                if (!canPublish(tasks) || entry.path("access_token").asText().isEmpty()) continue;
                PageCandidate candidate=new PageCandidate();
                candidate.id=required(entry,"id",false); id(candidate.id);
                candidate.name=entry.path("name").asText(); candidate.tasks=tasks;
                candidate.accessToken=entry.path("access_token").asText(); pages.add(candidate);
            }
            if (!result.path("paging").hasNonNull("next")) return pages;
            cursor=result.path("paging").path("cursors").path("after").asText();
            if (cursor.isEmpty() || !visited.add(cursor)) throw rejected("PAGINATION_INVALID","Meta 分页游标无效");
        }
        throw rejected("PAGE_LIMIT","Meta Page 数量超出本次授权上限");
    }

    public static boolean canPublish(List<String> tasks) {
        return tasks!=null && (tasks.contains("CREATE_CONTENT") || tasks.contains("PROFILE_PLUS_CREATE_CONTENT"));
    }

    public LocalDateTime validateToken(String platform, String externalId, String accessToken) {
        id(externalId);
        if ("INSTAGRAM".equals(platform)) {
            JsonNode profile=call("GET",ig("me"),map("fields","user_id,username,account_type"),accessToken,false);
            if (!externalId.equals(profile.path("user_id").asText(profile.path("id").asText())))
                throw rejected("IDENTITY_MISMATCH","Meta 账号身份不一致");
            requirePermissions(call("GET",ig("me/permissions"),map(),accessToken,false).path("data"),
                    Arrays.asList("instagram_business_basic","instagram_business_content_publish"));
            return null;
        }
        if (!"FACEBOOK_PAGE".equals(platform)) throw rejected("PLATFORM_INVALID","不支持的平台");
        TkSocialProperties.Credentials c=properties.getFacebook();
        JsonNode debug=call("GET",fb("debug_token"),map("input_token",accessToken),c.getAppId()+"|"+c.getAppSecret(),false).path("data");
        if (!debug.path("is_valid").asBoolean() || !c.getAppId().equals(debug.path("app_id").asText()))
            throw new TkSocialPlatformException("TOKEN_INVALID","Meta 授权失效，请重新授权",false,true,false);
        requirePermissions(debug.path("scopes"),Arrays.asList("pages_read_engagement","pages_manage_posts"));
        JsonNode profile=call("GET",fb("me"),map("fields","id"),accessToken,false);
        if (!externalId.equals(profile.path("id").asText())) throw rejected("IDENTITY_MISMATCH","Meta Page 身份不一致");
        long expires=debug.path("expires_at").asLong();
        long dataExpires=debug.path("data_access_expires_at").asLong();
        if (dataExpires>0 && (expires==0 || dataExpires<expires)) expires=dataExpires;
        return expires>0 ? LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(expires),java.time.ZoneId.systemDefault()) : null;
    }

    public PublishResult advance(String platform,String externalAccountId,String accessToken,String mediaType,
                                 String text,String mediaUrl,String containerId,String platformStatus) {
        properties.requireEnabled(); id(externalAccountId);
        if (!Arrays.asList("IMAGE","VIDEO","TEXT").contains(mediaType)) throw rejected("MEDIA_TYPE","不支持的媒体类型");
        if (!"TEXT".equals(mediaType)) mediaUrl(mediaUrl);
        if ("INSTAGRAM".equals(platform)) {
            if ("TEXT".equals(mediaType)) throw rejected("IG_MEDIA_REQUIRED","Instagram 发布需要图片或视频");
            if (blank(containerId)) {
                Map<String,String> params="VIDEO".equals(mediaType)
                        ? map("media_type","REELS","video_url",mediaUrl,"caption",safe(text))
                        : map("image_url",mediaUrl,"caption",safe(text));
                String created=required(call("POST",ig(externalAccountId+"/media"),params,accessToken,true),"id",true);
                return waiting(created,"IN_PROGRESS");
            }
            id(containerId);
            String status=call("GET",ig(containerId),map("fields","status_code"),accessToken,false).path("status_code").asText();
            if ("ERROR".equals(status) || "EXPIRED".equals(status)) throw rejected("IG_CONTAINER_"+status,"Instagram 媒体处理失败或容器过期");
            if ("PUBLISHED".equals(status)) throw new TkSocialPlatformException("IG_ALREADY_PUBLISHED",
                    "容器已发布，请核验平台帖子，禁止重复提交",false,false,true);
            if (!"FINISHED".equals(status)) return waiting(containerId,status.isEmpty()?"IN_PROGRESS":status);
            String mediaId=required(call("POST",ig(externalAccountId+"/media_publish"),
                    map("creation_id",containerId),accessToken,true),"id",true);
            PublishResult result=success(containerId,mediaId,null,null,"PUBLISHED");
            // A permalink lookup failure must never hide a confirmed mutation success.
            try { result.publishUrl=call("GET",ig(mediaId),map("fields","permalink"),accessToken,false).path("permalink").asText(null); }
            catch (TkSocialPlatformException ignored) { }
            return result;
        }
        if (!"FACEBOOK_PAGE".equals(platform)) throw rejected("PLATFORM_INVALID","不支持的平台");
        if ("VIDEO".equals(mediaType)) return facebookVideo(externalAccountId,accessToken,text,mediaUrl,containerId,platformStatus);
        if ("TEXT".equals(mediaType) && blank(text)) throw rejected("TEXT_REQUIRED","Facebook 文字不能为空");
        JsonNode response=call("POST",fb(externalAccountId+("IMAGE".equals(mediaType)?"/photos":"/feed")),
                "IMAGE".equals(mediaType)?map("url",mediaUrl,"caption",safe(text),"published","true"):map("message",text),accessToken,true);
        String remoteId=required(response,"id",true);
        String postId="IMAGE".equals(mediaType)?response.path("post_id").asText(null):remoteId;
        return success(null,"IMAGE".equals(mediaType)?remoteId:null,postId,
                postId==null?null:"https://www.facebook.com/"+postId,"PUBLISHED");
    }

    /** Read-only recovery of an existing remote object; never creates, uploads or publishes. */
    public PublishResult reconcile(String platform,String externalAccountId,String accessToken,String containerId) {
        properties.requireEnabled(); id(externalAccountId); id(containerId);
        if ("FACEBOOK_PAGE".equals(platform))
            return facebookVideoStatus(accessToken,containerId,"FB_RECONCILING");
        if (!"INSTAGRAM".equals(platform)) throw rejected("PLATFORM_INVALID","不支持的平台");
        String status=call("GET",ig(containerId),map("fields","status_code"),accessToken,false).path("status_code").asText();
        if ("ERROR".equals(status) || "EXPIRED".equals(status))
            throw rejected("IG_CONTAINER_"+status,"Instagram 媒体处理失败或容器过期");
        if ("PUBLISHED".equals(status)) return success(containerId,null,null,null,"PUBLISHED");
        // FINISHED means ready to publish, not published. Recovery must not issue media_publish.
        return waiting(containerId,status.isEmpty()?"IN_PROGRESS":status);
    }

    private PublishResult facebookVideo(String page,String token,String text,String url,String videoId,String stage) {
        if (blank(videoId)) {
            if (!blank(stage)) throw rejected("FB_STAGE_INVALID","Facebook 视频阶段与标识不一致");
            JsonNode start=call("POST",fb(page+"/video_reels"),map("upload_phase","start"),token,true);
            String created=required(start,"video_id",true); id(created);
            return waiting(created,"FB_STARTED");
        }
        id(videoId);
        if ("FB_STARTED".equals(stage)) {
            JsonNode upload=call("UPLOAD","https://rupload.facebook.com/video-upload/"+properties.version()+"/"+videoId,
                    map("file_url",url),token,true);
            requireSuccess(upload); return waiting(videoId,"FB_UPLOADED");
        }
        if ("FB_UPLOADED".equals(stage)) {
            JsonNode finish=call("POST",fb(page+"/video_reels"),map("upload_phase","finish","video_id",videoId,
                    "video_state","PUBLISHED","description",safe(text)),token,true);
            requireSuccess(finish); return waiting(videoId,"FB_FINISH_SUBMITTED");
        }
        if (!"FB_FINISH_SUBMITTED".equals(stage)) throw rejected("FB_STAGE_INVALID","Facebook 视频恢复阶段无效");
        return facebookVideoStatus(token,videoId,"FB_FINISH_SUBMITTED");
    }

    private PublishResult facebookVideoStatus(String token,String videoId,String waitingStage) {
        JsonNode status=call("GET",fb(videoId),map("fields","status"),token,false).path("status");
        for (String phase:Arrays.asList("uploading_phase","processing_phase","publishing_phase")) {
            String value=status.path(phase).path("status").asText();
            if ("error".equals(value) || "failed".equals(value))
                throw rejected("FB_VIDEO_PROCESSING_FAILED","Facebook 视频处理或发布失败");
        }
        if ("error".equals(status.path("video_status").asText())) throw rejected("FB_VIDEO_ERROR","Facebook 视频处理失败");
        if ("ready".equals(status.path("video_status").asText())
                && "complete".equals(status.path("publishing_phase").path("status").asText()))
            return success(videoId,videoId,videoId,"https://www.facebook.com/reel/"+videoId,"PUBLISHED");
        return waiting(videoId,waitingStage);
    }

    private void requireSuccess(JsonNode response) {
        if (!response.path("success").asBoolean()) throw new TkSocialPlatformException("MUTATION_UNCONFIRMED",
                "Meta 未确认请求结果，请人工核验",false,false,true);
    }
    private JsonNode call(String method,String url,Map<String,String> params,String token,boolean mutation) {
        properties.requireEnabled(); return transport.request(method,url,params,token,mutation);
    }
    private static <T> T instagramStage(String stage, Supplier<T> action) {
        try { return action.get(); }
        catch (TkSocialPlatformException e) { throw e.withStage(stage); }
    }
    private String ig(String path) { return "https://graph.instagram.com/"+properties.version()+"/"+path; }
    private String fb(String path) { return "https://graph.facebook.com/"+properties.version()+"/"+path; }
    private static Authorization token(JsonNode node) {
        Authorization a=new Authorization(); a.accessToken=required(node,"access_token",false);
        long ttl=node.path("expires_in").asLong();
        if (ttl<=0) throw rejected("TOKEN_EXPIRY_MISSING","Meta 未返回有效令牌期限");
        a.expiresAt=LocalDateTime.now().plusSeconds(ttl); return a;
    }
    private static void requirePermissions(JsonNode node,List<String> required) {
        Set<String> granted=new HashSet<>();
        if (node!=null && node.isTextual()) granted.addAll(Arrays.asList(node.asText().split(",")));
        else if (node!=null) for (JsonNode entry:node) {
            if (entry.isTextual()) granted.add(entry.asText());
            else if ("granted".equals(entry.path("status").asText())) granted.add(entry.path("permission").asText());
        }
        if (!granted.containsAll(required)) throw new TkSocialPlatformException("PERMISSIONS_MISSING",
                "Meta 必需发布权限未授予，请重新授权",false,true,false);
    }
    private static String required(JsonNode node,String field,boolean mutation) {
        String value=node.path(field).asText();
        if (value.isEmpty()) throw new TkSocialPlatformException("RESPONSE_INCOMPLETE","Meta 返回结果不完整",false,false,mutation);
        return value;
    }
    private static void id(String value) {
        if (value==null || !value.matches("[0-9]{1,100}")) throw rejected("ID_INVALID","Meta 账号或媒体标识无效");
    }
    private static void mediaUrl(String value) {
        try {
            java.net.URI uri=java.net.URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || value.contains("\r") || value.contains("\n")) throw new IllegalArgumentException();
        } catch (Exception e) { throw rejected("MEDIA_URL_INVALID","媒体必须使用受控 HTTPS 公网地址"); }
    }
    private static String safe(String value) { return value==null?"":value; }
    private static boolean blank(String value) { return value==null || value.trim().isEmpty(); }
    public static Map<String,String> map(String... pairs) {
        Map<String,String> values=new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) values.put(pairs[i],pairs[i+1]);
        return values;
    }
    private static TkSocialPlatformException rejected(String code,String message) {
        return new TkSocialPlatformException(code,message,false,false,false);
    }
    private static PublishResult waiting(String id,String stage) {
        PublishResult r=new PublishResult(); r.status="WAITING"; r.containerId=id; r.platformStatus=stage; return r;
    }
    private static PublishResult success(String container,String media,String post,String url,String stage) {
        PublishResult r=new PublishResult(); r.status="SUCCESS"; r.containerId=container; r.mediaId=media;
        r.postId=post; r.publishUrl=url; r.platformStatus=stage; return r;
    }
    @Data public static class PublishResult {
        private String status,containerId,mediaId,postId,publishUrl,platformStatus;
    }
    @Data public static class PageCandidate {
        private String id,name;
        private List<String> tasks;
        @ToString.Exclude private String accessToken;
    }
    @Data public static class Authorization {
        @ToString.Exclude private String accessToken;
        private String externalId,providerUserId,accountName,username,accountType,scopes;
        private LocalDateTime expiresAt;
        @ToString.Exclude private List<PageCandidate> pages;
    }
}
