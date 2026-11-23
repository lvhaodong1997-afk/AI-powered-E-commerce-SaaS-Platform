package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerShopDO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TkTiktokShopSellerReadService {
    public enum View {
        PRODUCTS("/product/202502/products/search", "products", "id,title,status,create_time>time"),
        ORDERS("/order/202309/orders/search", "orders", "id,status,create_time>time,payment/total_amount>amount,payment/currency>currency"),
        PACKAGES("/fulfillment/202309/packages/search", "packages", "id,status,create_time>time,shipping_provider_name>carrier,tracking_number>tracking"),
        RETURNS("/return_refund/202309/returns/search", "return_orders", "return_id>id,order_id>orderId,return_status>status,create_time>time,refund_amount/refund_total>amount,refund_amount/currency>currency"),
        STATEMENTS("/finance/202309/statements", "statements", "id,payment_status>status,statement_time>time,settlement_amount>amount,currency"),
        PROMOTIONS("/promotion/202309/activities/search", "activities", "id,title,status,activity_type>type,begin_time>time,end_time>endTime"),
        VIDEOS("/analytics/202605/shop_videos/performance", "videos", "id,title,video_post_time>time,views,product_impressions>impressions,product_clicks>clicks,items_sold>sold,gmv/amount>amount,gmv/currency>currency"),
        LIVES("/analytics/202609/shop_lives/performance", "live_stream_sessions", "id,title,start_time>time,end_time>endTime,traffic/views>views,traffic/product_impressions>impressions,traffic/product_clicks>clicks,sales_performance/live_attributed_items_sold>sold,sales_performance/live_attributed_gmv/amount>amount,sales_performance/live_attributed_gmv/currency>currency");

        final String path, array, fields;
        View(String path, String array, String fields) { this.path=path; this.array=array; this.fields=fields; }
        public boolean isAnalytics() { return this==VIDEOS || this==LIVES; }
    }

    private final TkTiktokShopSellerApiClient api;
    private final TkTiktokShopSellerAuthService auth;
    private final TkTiktokShopSellerTokenService tokens;

    public TkTiktokShopSellerReadService(TkTiktokShopSellerApiClient api, TkTiktokShopSellerAuthService auth, TkTiktokShopSellerTokenService tokens) {
        this.api=api; this.auth=auth; this.tokens=tokens;
    }

    public Page query(Long shopId, View view, String cursor, LocalDate start, LocalDate end, String liveAccountType) {
        TkTiktokShopSellerShopDO shop=auth.requireReadableShop(shopId);
        TkTiktokShopSellerTokenService.AccessToken token;
        try {
            token=tokens.getAccessToken(shop.getTenantId(),shop.getCompanyId(),shop.getAccountId());
        } catch(TkTiktokShopApiException e) {
            throw sellerAuthorizationError(e);
        }
        try {return read(view,token.getValue(),shop.getShopCipher(),cursor,start,end,liveAccountType);}
        catch(TkTiktokShopApiException e){
            if(e.isPermissionDenied()) {
                throw new TkTiktokShopApiException(view.isAnalytics() ? TkTiktokShopApiException.SELLER_ANALYTICS_ACCESS_DENIED
                        : TkTiktokShopApiException.SELLER_FEATURE_ACCESS_DENIED, e.getRequestId());
            }
            tokens.reportRejectedToken(shop.getTenantId(),shop.getCompanyId(),shop.getAccountId(),token.getVersion(),e);
            throw sellerAuthorizationError(e);
        }
    }

    private static TkTiktokShopApiException sellerAuthorizationError(TkTiktokShopApiException error) {
        return error.isReauthorizationRequired()
                ? new TkTiktokShopApiException(TkTiktokShopApiException.SELLER_REAUTHORIZE,error.getRequestId()) : error;
    }

    Page read(View view, String token, String cipher, String cursor, LocalDate start, LocalDate end, String liveAccountType) {
        if(cursor!=null && (cursor.length()>2048 || cursor.chars().anyMatch(Character::isISOControl))) throw new IllegalArgumentException("Invalid Shop page token");
        Map<String,String> query=new LinkedHashMap<>(); query.put("shop_cipher",cipher);
        Map<String,Object> body=new LinkedHashMap<>();
        if(view==View.PROMOTIONS) {
            body.put("page_size",20); body.put("page_token",cursor==null?"":cursor);
        } else {
            query.put("page_size","20"); if(cursor!=null && !cursor.isEmpty())query.put("page_token",cursor);
        }
        if(view.isAnalytics()) {
            if(start==null || end==null || start.getYear()<2000 || end.getYear()>2100 || !start.isBefore(end) || ChronoUnit.DAYS.between(start,end)>31)
                throw new IllegalArgumentException("Choose a Shop analytics range of 1 to 31 days");
            query.put("start_date_ge",start.toString()); query.put("end_date_lt",end.toString());
            if(view==View.LIVES) {
                String type=liveAccountType==null?"OFFICIAL_ACCOUNTS":liveAccountType;
                if(!Set.of("OFFICIAL_ACCOUNTS","MARKETING_ACCOUNTS","AFFILIATE_ACCOUNTS").contains(type))throw new IllegalArgumentException("Invalid LIVE account type");
                query.put("account_type",type);
            }
        }
        if(view==View.STATEMENTS){query.put("sort_field","statement_time");query.put("sort_order","DESC");}
        TkTiktokShopApiClient.Result result=view.isAnalytics() || view==View.STATEMENTS ? api.get(view.path,token,query): api.post(view.path,token,query,body);
        List<Map<String,String>> data=new ArrayList<>();
        for(JsonNode item:rows(result,view.array)){
            Map<String,String> row=new LinkedHashMap<>();
            for(String field:view.fields.split(",")){
                String[] alias=field.split(">"); JsonNode value=item;
                for(String key:alias[0].split("/"))value=value.path(key);
                String text=scalar(value);
                if(text!=null)row.put(alias[alias.length-1],text);
            }
            data.add(row);
        }
        return new Page(data, scalar(result.getData().path("next_page_token")), scalar(result.getData().path("latest_available_date")));
    }

    private static JsonNode rows(TkTiktokShopApiClient.Result result,String name) {
        JsonNode rows=result.getData().path(name);
        if(rows.isMissingNode() || rows.isNull())return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        if(!rows.isArray())throw new TkTiktokShopApiException("INVALID_RESPONSE",result.getRequestId());
        return rows;
    }
    private static String scalar(JsonNode node) {
        return (node.isTextual() || node.isNumber()) && !node.asText().trim().isEmpty()?node.asText():null;
    }
    @Getter
    public static final class Page {
        private final List<Map<String,String>> rows;
        private final String nextPageToken,latestAvailableDate;
        Page(List<Map<String,String>> rows,String nextPageToken,String latestAvailableDate){this.rows=rows;this.nextPageToken=nextPageToken;this.latestAvailableDate=latestAvailableDate;}
    }
}
