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
    public static final String PRODUCTION_API_BASE="https://open-api.tiktokglobalshop.com";
    public static final String PRODUCTION_AUTH_BASE="https://auth.tiktok-shops.com";
    public static final String REQUIRED_SCOPE="seller.authorization.info";
    public static final String PRODUCT_SCOPE="seller.product.basic";
    public static final String ORDER_SCOPE="seller.order.info";
    public static final String ORDER_FS_SCOPE="seller.fs.order&fufillment.management";
    public static final int DEFAULT_PAGE_SIZE=20;
    public static final int MAX_PAGE_SIZE=100;
    public static final int MAX_CURSOR_LENGTH=4096;
    private final TkTiktokShopProperties properties;
    private final ObjectMapper json;
    private final OkHttpClient supplied;
    private final Clock clock;
    private final String apiBase,authBase;
    @org.springframework.beans.factory.annotation.Autowired
    public TkTiktokShopSellerApiClient(TkTiktokShopProperties p,ObjectMapper json) {
        this(p,json,new OkHttpClient(),Clock.systemUTC(),apiBaseFor(p.getSeller()==null?null:p.getSeller().getEnvironment()),authBaseFor(p.getSeller()==null?null:p.getSeller().getEnvironment()));
    }
    TkTiktokShopSellerApiClient(TkTiktokShopProperties p,ObjectMapper json,OkHttpClient supplied,Clock clock,String apiBase,String authBase) {
        this.properties=p;this.json=json;this.supplied=supplied;this.clock=clock;this.apiBase=apiBase;this.authBase=authBase;
    }
    /** Official Methods and Endpoints currently document a single production API domain. Development Shop calls use the same official hosts; do not invent a sandbox host. */
    public static String apiBaseFor(String environment){return PRODUCTION_API_BASE;}
    public static String authBaseFor(String environment){return PRODUCTION_AUTH_BASE;}
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
    public CatalogPage<ProductSummary> searchProducts(String token,String shopCipher,int pageSize,String pageToken) {
        TkTiktokShopApiClient.Result result=post("/product/202502/products/search",token,query(shopCipher,pageSize,pageToken,false),Collections.emptyMap());
        JsonNode data=result.getData();JsonNode rows=data.path("products");
        if(!rows.isArray())throw invalid(result);
        List<ProductSummary> products=new ArrayList<>();
        for(JsonNode row:rows){
            String id=asString(row,"id"),title=text(row,"title"),status=text(row,"status");
            if(id==null || id.length()>128 || title==null || title.length()>512 || status==null || status.length()>64)throw invalid(result);
            products.add(new ProductSummary(id,title,status));
        }
        return new CatalogPage<>(products,total(data),nextToken(data));
    }
    public CatalogPage<OrderSummary> searchOrders(String token,String shopCipher,int pageSize,String pageToken) {
        TkTiktokShopApiClient.Result result=post("/order/202309/orders/search",token,query(shopCipher,pageSize,pageToken,true),Collections.emptyMap());
        JsonNode data=result.getData();JsonNode rows=data.path("orders");
        // TikTok omits the orders array when an empty search has no next page.
        if(rows.isMissingNode() && Long.valueOf(0).equals(total(data))
                && emptyPageToken(data.path("next_page_token")) && emptyPageToken(data.path("page_token")))
            return new CatalogPage<>(Collections.emptyList(),0L,null);
        if(!rows.isArray())throw invalid(result);
        List<OrderSummary> orders=new ArrayList<>();
        for(JsonNode row:rows){
            String id=asString(row,"id"),status=text(row,"status");
            Long createTime=epoch(row,"create_time");
            JsonNode payment=row.path("payment");
            String currency=text(payment,"currency"),amount=asString(payment,"total_amount");
            if(id==null || id.length()>128 || status==null || status.length()>64 || createTime==null || currency==null || currency.length()>16 || amount==null || amount.length()>64)throw invalid(result);
            orders.add(new OrderSummary(id,status,createTime,amount,currency));
        }
        return new CatalogPage<>(orders,total(data),nextToken(data));
    }
    public static int normalizePageSize(Integer pageSize) {
        int size=pageSize==null?DEFAULT_PAGE_SIZE:pageSize;
        if(size<1 || size>MAX_PAGE_SIZE)throw new IllegalArgumentException("Seller catalog page size is invalid");
        return size;
    }
    public static String normalizePageToken(String pageToken) {
        if(pageToken==null || pageToken.isEmpty())return null;
        if(pageToken.length()>MAX_CURSOR_LENGTH || !pageToken.chars().allMatch(c->c>=32 && c<=126))throw new IllegalArgumentException("Seller catalog page token is invalid");
        return pageToken;
    }
    public static boolean hasOrderScope(String raw) {
        return hasGrantedScope(raw,ORDER_SCOPE) || hasGrantedScope(raw,ORDER_FS_SCOPE);
    }
    public static boolean hasGrantedScope(String raw,String scope) {
        try {return TkTiktokShopApiClient.TokenBundle.hasScope(new ObjectMapper().readTree(raw),scope);}catch(Exception ignored){return false;}
    }
    private Map<String,String> query(String shopCipher,int pageSize,String pageToken,boolean orders) {
        if(shopCipher==null || shopCipher.isEmpty() || shopCipher.length()>2048)throw new IllegalArgumentException("Seller shop is unavailable");
        Map<String,String> query=new LinkedHashMap<>();
        query.put("page_size",Integer.toString(pageSize));
        query.put("shop_cipher",shopCipher);
        if(pageToken!=null)query.put("page_token",pageToken);
        if(orders){query.put("sort_field","create_time");query.put("sort_order","DESC");}
        return query;
    }
    private static Long total(JsonNode data) {
        JsonNode total=data.path("total_count");
        return total.isIntegralNumber() && total.canConvertToLong() && total.longValue()>=0?total.longValue():null;
    }
    private static boolean emptyPageToken(JsonNode token) {return token.isMissingNode() || token.isTextual() && token.textValue().isEmpty();}
    private static String nextToken(JsonNode data) {
        String token=text(data,"next_page_token");
        if(token==null)token=text(data,"page_token");
        return token!=null && token.length()<=MAX_CURSOR_LENGTH && token.chars().allMatch(c->c>=32 && c<=126)?token:null;
    }
    private static String text(JsonNode n,String key){JsonNode v=n.path(key);return v.isTextual()&&!v.asText().trim().isEmpty()?v.asText():null;}
    private static String asString(JsonNode n,String key){JsonNode v=n.path(key);if(v.isTextual()&&!v.asText().trim().isEmpty())return v.asText();if(v.isNumber())return v.asText();return null;}
    private static Long epoch(JsonNode n,String key){JsonNode v=n.path(key);return v.isIntegralNumber()&&v.canConvertToLong()?v.longValue():null;}
    private static TkTiktokShopApiException invalid(){return new TkTiktokShopApiException("INVALID_IDENTITY",null);}
    private static TkTiktokShopApiException invalid(TkTiktokShopApiClient.Result result){return new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());}
    public static final class AuthorizedShop {
        private final String id,name,region,cipher,sellerType,code;
        public AuthorizedShop(String id,String name,String region,String cipher,String sellerType,String code){this.id=id;this.name=name;this.region=region;this.cipher=cipher;this.sellerType=sellerType;this.code=code;}
        public String getId(){return id;}public String getName(){return name;}public String getRegion(){return region;}public String getCipher(){return cipher;}public String getSellerType(){return sellerType;}public String getCode(){return code;}
    }
    public static final class CatalogPage<T> {
        private final List<T> items;private final Long total;private final String nextPageToken;
        public CatalogPage(List<T> items,Long total,String nextPageToken){this.items=Collections.unmodifiableList(items);this.total=total;this.nextPageToken=nextPageToken;}
        public List<T> getItems(){return items;}public Long getTotal(){return total;}public String getNextPageToken(){return nextPageToken;}
    }
    public static final class ProductSummary {
        private final String id,title,status;
        public ProductSummary(String id,String title,String status){this.id=id;this.title=title;this.status=status;}
        public String getId(){return id;}public String getTitle(){return title;}public String getStatus(){return status;}
    }
    public static final class OrderSummary {
        private final String id,status,totalAmount,currency;private final long createTime;
        public OrderSummary(String id,String status,long createTime,String totalAmount,String currency){this.id=id;this.status=status;this.createTime=createTime;this.totalAmount=totalAmount;this.currency=currency;}
        public String getId(){return id;}public String getStatus(){return status;}public long getCreateTime(){return createTime;}public String getTotalAmount(){return totalAmount;}public String getCurrency(){return currency;}
    }
}
