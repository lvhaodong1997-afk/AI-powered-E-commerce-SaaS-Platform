package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class TkTiktokShopApiClient {
    public static final String REQUIRED_SCOPE = "creator.video.write";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final TkTiktokShopProperties properties;
    private final ObjectMapper json;
    private final OkHttpClient client;
    private final Clock clock;
    private final String apiBase, authBase;

    @Autowired
    public TkTiktokShopApiClient(TkTiktokShopProperties properties, ObjectMapper json) {
        this(properties,json,new OkHttpClient(),Clock.systemUTC(),"https://open-api.tiktokglobalshop.com","https://auth.tiktok-shops.com");
    }
    /** Package-only transport seam. Runtime configuration cannot change the official hosts. */
    TkTiktokShopApiClient(TkTiktokShopProperties properties,ObjectMapper json,OkHttpClient supplied,Clock clock,String apiBase,String authBase) {
        this.properties=properties;this.json=json;this.clock=clock;this.apiBase=apiBase;this.authBase=authBase;
        OkHttpClient.Builder builder=supplied.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS);
        // A shared application's interceptors/authenticators must never observe Shop secrets.
        builder.interceptors().clear();builder.networkInterceptors().clear();
        builder.authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE);
        this.client=builder.build();
    }
    public Result get(String path,String token,Map<String,String> query) { return signed("GET",path,token,query,null); }
    public Result post(String path,String token,Object body) {
        byte[] exact;
        try { exact=json.writeValueAsBytes(body); } catch (Exception ignored) { throw new IllegalArgumentException("Invalid Shop request body"); }
        return signed("POST",path,token,Collections.emptyMap(),exact);
    }
    private Result signed(String method,String path,String token,Map<String,String> query,byte[] exact) {
        properties.requireConfigured();
        if (path==null || !path.matches("/(?!/)[A-Za-z0-9_/-]+") || token==null || token.isEmpty()
                || token.length()>16384 || !token.chars().allMatch(c->c>=32 && c<=126))
            throw new IllegalArgumentException("Invalid Shop request");
        Map<String,String> params=new TreeMap<>();
        if (query!=null) params.putAll(query);
        if (params.containsKey("access_token") || params.containsKey("sign") || params.containsKey("app_secret")) throw new IllegalArgumentException("Invalid Shop query");
        params.put("app_key",properties.getAppKey());params.put("timestamp",Long.toString(clock.instant().getEpochSecond()));
        params.put("sign",TkTiktokShopRequestSigner.sign(path,params,exact,"application/json",properties.getAppSecret()));
        HttpUrl.Builder url=HttpUrl.parse(apiBase+path).newBuilder(); params.forEach(url::addQueryParameter);
        Request.Builder request=new Request.Builder().url(url.build()).header("x-tts-access-token",token).header("Content-Type","application/json");
        request.method(method,exact==null?null:RequestBody.create(JSON,exact));
        return execute(request.build());
    }
    public TokenBundle exchangeCode(String code) {return token("get","auth_code",code,"authorized_code");}
    public TokenBundle refreshToken(String refresh) {return token("refresh","refresh_token",refresh,"refresh_token");}
    private TokenBundle token(String endpoint,String parameter,String value,String grant) {
        properties.requireConfigured();
        if(value==null || value.trim().isEmpty())throw new TkTiktokShopApiException("INVALID_TOKEN",null);
        HttpUrl url=HttpUrl.parse(authBase+"/api/v2/token/"+endpoint).newBuilder()
                .addQueryParameter("app_key",properties.getAppKey()).addQueryParameter("app_secret",properties.getAppSecret())
                .addQueryParameter(parameter,value).addQueryParameter("grant_type",grant).build();
        Result result=execute(new Request.Builder().url(url).get().build());
        try {return TokenBundle.parse(result.getData(),clock);}catch(TkTiktokShopApiException ignored){throw new TkTiktokShopApiException("INVALID_TOKEN",result.getRequestId());}
    }
    public CreatorProfile getCreatorProfile(String token) {
        Result result=get("/affiliate_creator/202508/profiles",token,Collections.emptyMap());JsonNode data=result.getData();
        String username=text(data,"username"),market=text(data,"selection_region"),open=text(data,"creator_user_open_id");
        if(username==null || market==null || open==null)throw new TkTiktokShopApiException("INVALID_IDENTITY",result.getRequestId());
        // Profile user_type is an account-category STRING, not the token's creator identity integer.
        return new CreatorProfile(username,market,open);
    }
    private Result execute(Request request) {
        try(Response response=client.newCall(request).execute()) {
            if(response.body()==null)throw new TkTiktokShopApiException("INVALID_RESPONSE",null);
            if(response.body().contentLength()>MAX_RESPONSE_BYTES)throw new TkTiktokShopApiException("RESPONSE_TOO_LARGE",null);
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            InputStream input=response.body().byteStream();
            while((count=input.read(buffer))!=-1){if(out.size()+count>MAX_RESPONSE_BYTES)throw new TkTiktokShopApiException("RESPONSE_TOO_LARGE",null);out.write(buffer,0,count);}
            JsonNode root=json.readTree(out.toByteArray());
            if(root==null || !root.isObject())throw new TkTiktokShopApiException("INVALID_RESPONSE",null);
            String requestId=text(root,"request_id");
            if(!root.path("code").isIntegralNumber())throw new TkTiktokShopApiException("INVALID_RESPONSE",requestId);
            long code=root.path("code").asLong();
            if(code!=0 || !response.isSuccessful())throw new TkTiktokShopApiException(code==0?"HTTP_ERROR":Long.toString(code),requestId);
            if(!root.path("data").isObject())throw new TkTiktokShopApiException("INVALID_RESPONSE",requestId);
            return new Result(root.path("data"),new TkTiktokShopApiException("0",requestId).getRequestId());
        }catch(TkTiktokShopApiException e){throw e;}catch(Exception ignored){throw new TkTiktokShopApiException("TRANSPORT_ERROR",null);}
    }
    private static String text(JsonNode node,String key) {JsonNode value=node.path(key);return value.isTextual() && !value.asText().trim().isEmpty()?value.asText():null;}
    public static final class Result {
        private final JsonNode data;private final String requestId;
        Result(JsonNode data,String requestId){this.data=data;this.requestId=requestId;}
        public JsonNode getData(){return data;}public String getRequestId(){return requestId;}
    }
    public static final class CreatorProfile {
        private final String username,selectionRegion,creatorUserOpenId;
        public CreatorProfile(String username,String selectionRegion,String creatorUserOpenId){this.username=username;this.selectionRegion=selectionRegion;this.creatorUserOpenId=creatorUserOpenId;}
        public String getUsername(){return username;}public String getSelectionRegion(){return selectionRegion;}public String getCreatorUserOpenId(){return creatorUserOpenId;}
    }
    /** Internal service value, never a controller response; Object.toString never prints secrets. */
    public static final class TokenBundle {
        private final String accessToken,refreshToken,openId,grantedScopes;
        private final LocalDateTime accessExpireTime,refreshExpireTime;
        private TokenBundle(String a,String r,String open,String scopes,LocalDateTime ae,LocalDateTime re){accessToken=a;refreshToken=r;openId=open;grantedScopes=scopes;accessExpireTime=ae;refreshExpireTime=re;}
        static TokenBundle parse(JsonNode data,Clock clock) {
            try {
                String a=text(data,"access_token"),r=text(data,"refresh_token"),open=text(data,"open_id");
                if(a==null || r==null || open==null || !data.path("user_type").isIntegralNumber() || data.path("user_type").asInt()!=1 || !hasScope(data.path("granted_scopes")))throw new IllegalArgumentException();
                if(!data.path("access_token_expire_in").isIntegralNumber() || !data.path("refresh_token_expire_in").isIntegralNumber())throw new IllegalArgumentException();
                long ae=data.path("access_token_expire_in").asLong(),re=data.path("refresh_token_expire_in").asLong();
                if(ae<=clock.instant().getEpochSecond() || re<=clock.instant().getEpochSecond())throw new IllegalArgumentException();
                return new TokenBundle(a,r,open,data.path("granted_scopes").toString(),LocalDateTime.ofInstant(Instant.ofEpochSecond(ae),ZoneOffset.UTC),LocalDateTime.ofInstant(Instant.ofEpochSecond(re),ZoneOffset.UTC));
            }catch(Exception ignored){throw new TkTiktokShopApiException("INVALID_TOKEN",null);}
        }
        public static boolean hasScope(JsonNode scopes){if(!scopes.isArray())return false;for(JsonNode scope:scopes)if(scope.isTextual() && REQUIRED_SCOPE.equals(scope.asText()))return true;return false;}
        public String getAccessToken(){return accessToken;}public String getRefreshToken(){return refreshToken;}public String getOpenId(){return openId;}public String getGrantedScopes(){return grantedScopes;}
        public LocalDateTime getAccessExpireTime(){return accessExpireTime;}public LocalDateTime getRefreshExpireTime(){return refreshExpireTime;}
    }
}
