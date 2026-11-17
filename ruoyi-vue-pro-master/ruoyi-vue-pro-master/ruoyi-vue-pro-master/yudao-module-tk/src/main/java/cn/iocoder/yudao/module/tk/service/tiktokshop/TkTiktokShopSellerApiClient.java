package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.*;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

/** Seller credentials and token identity remain separate from creator publishing. */
@Component
public class TkTiktokShopSellerApiClient {
    public static final String REQUIRED_SCOPE="seller.authorization.info";
    private final TkTiktokShopProperties properties;
    private final ObjectMapper json;
    private final OkHttpClient supplied;
    private final Clock clock;
    private final String apiBase,authBase;
    @org.springframework.beans.factory.annotation.Autowired
    public TkTiktokShopSellerApiClient(TkTiktokShopProperties p,ObjectMapper json) {
        this(p,json,new OkHttpClient(),Clock.systemUTC(),"https://open-api.tiktokglobalshop.com","https://auth.tiktok-shops.com");
    }
    TkTiktokShopSellerApiClient(TkTiktokShopProperties p,ObjectMapper json,OkHttpClient supplied,Clock clock,String apiBase,String authBase) {
        this.properties=p;this.json=json;this.supplied=supplied;this.clock=clock;this.apiBase=apiBase;this.authBase=authBase;
    }
    private TkTiktokShopApiClient client(){return new TkTiktokShopApiClient(properties.sellerClientProperties(),json,supplied,clock,apiBase,authBase,0,REQUIRED_SCOPE);}
    public TkTiktokShopApiClient.TokenBundle exchangeCode(String code){return client().exchangeCode(code);}
    public TkTiktokShopApiClient.TokenBundle refreshToken(String value){return client().refreshToken(value);}
    public TkTiktokShopApiClient.Result get(String path,String token,Map<String,String> query){return client().get(path,token,query);}
    public TkTiktokShopApiClient.Result post(String path,String token,Map<String,String> query,Object body){return client().post(path,token,query,body);}
    public List<AuthorizedShop> getAuthorizedShops(String token) {
        JsonNode shops=get("/authorization/202309/shops",token,Collections.emptyMap()).getData().path("shops");
        if(!shops.isArray())throw invalid();
        List<AuthorizedShop> result=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(JsonNode shop:shops){
            String id=text(shop,"id"),name=text(shop,"name"),region=text(shop,"region"),cipher=text(shop,"cipher");
            if(id==null || id.length()>128 || name==null || name.length()>255 || region==null || region.length()>16 || cipher==null || cipher.length()>2048 || !ids.add(id))throw invalid();
            result.add(new AuthorizedShop(id,name,region,cipher,text(shop,"seller_type"),text(shop,"code")));
        }
        return result;
    }
    private static String text(JsonNode n,String key){JsonNode v=n.path(key);return v.isTextual()&&!v.asText().trim().isEmpty()?v.asText():null;}
    private static TkTiktokShopApiException invalid(){return new TkTiktokShopApiException("INVALID_IDENTITY",null);}
    public static final class AuthorizedShop {
        private final String id,name,region,cipher,sellerType,code;
        public AuthorizedShop(String id,String name,String region,String cipher,String sellerType,String code){this.id=id;this.name=name;this.region=region;this.cipher=cipher;this.sellerType=sellerType;this.code=code;}
        public String getId(){return id;}public String getName(){return name;}public String getRegion(){return region;}public String getCipher(){return cipher;}public String getSellerType(){return sellerType;}public String getCode(){return code;}
    }
}
