package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.*;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** One official page per request. Signed selections are scoped to account, auth version and user. */
@Service
public class TkTiktokShopProductService {
    private final TkTiktokShopAuthService auth; private final TkTiktokShopTokenService tokens;
    private final TkTiktokShopApiClient api; private final TkTiktokShopProperties properties;
    private final ObjectMapper json; private final Clock clock;
    @Autowired public TkTiktokShopProductService(TkTiktokShopAuthService auth,TkTiktokShopTokenService tokens,TkTiktokShopApiClient api,TkTiktokShopProperties p,ObjectMapper json){this(auth,tokens,api,p,json,Clock.systemUTC());}
    TkTiktokShopProductService(TkTiktokShopAuthService auth,TkTiktokShopTokenService tokens,TkTiktokShopApiClient api,TkTiktokShopProperties p,ObjectMapper json,Clock clock){this.auth=auth;this.tokens=tokens;this.api=api;properties=p;this.json=json;this.clock=clock;}

    public ProductPage list(Long id,String source,int size,String pageToken,String keyword,String user) {
        TkTiktokShopAccountDO a=auth.requireReadable(id);
        ProductPage page=fetch(a,source,size,pageToken,keyword);
        for(Product p:page.products)if(p.available) {
            Selection selection=new Selection();selection.id=p.id;selection.source=source;selection.product=p;
            selection.accountId=a.getId();selection.tenantId=a.getTenantId();selection.user=user;selection.authVersion=a.getTokenVersion();
            selection.pageToken=pageToken;selection.pageSize=size;selection.keyword=keyword;selection.expires=clock.instant().getEpochSecond()+600;
            p.selectionProof=encode(selection);
        }
        return page;
    }
    public Selection verifySelection(TkTiktokShopAccountDO a,String user,String product,String source,String proof) {
        try {
            if(proof==null || proof.length()>16000)throw new IllegalArgumentException();
            String[] parts=proof.split("\\.",-1);if(parts.length!=2)throw new IllegalArgumentException();
            byte[] payload=Base64.getUrlDecoder().decode(parts[0]);
            if(!MessageDigest.isEqual(mac(payload),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();
            Selection s=json.readValue(payload,Selection.class);
            if(s.expires<=clock.instant().getEpochSecond() || !Objects.equals(a.getId(),s.accountId) || !Objects.equals(a.getTenantId(),s.tenantId)
                    || !Objects.equals(a.getTokenVersion(),s.authVersion) || !Objects.equals(user,s.user) || !Objects.equals(product,s.id) || !Objects.equals(source,s.source) || !s.product.available)throw new IllegalArgumentException();
            return s;
        }catch(Exception ignored){throw new IllegalArgumentException("Product selection expired or invalid; select it again");}
    }
    public Product revalidate(TkTiktokShopAccountDO a,Selection s) {
        if(!Objects.equals(a.getId(),s.accountId) || !Objects.equals(a.getTenantId(),s.tenantId) || !Objects.equals(a.getTokenVersion(),s.authVersion))throw new IllegalArgumentException("Product account authorization changed");
        for(Product p:fetch(a,s.source,s.pageSize,s.pageToken,s.keyword).products)if(Objects.equals(p.id,s.id)&&p.available)return p;
        throw new IllegalArgumentException("Product is no longer available; select it again");
    }
    private ProductPage fetch(TkTiktokShopAccountDO a,String source,int size,String cursor,String keyword) {
        boolean shop="SHOP".equals(source);
        if((!shop&&!"SHOWCASE".equals(source)) || size<1 || size>(shop?100:20) || (cursor!=null&&cursor.length()>4096) || (keyword!=null&&keyword.length()>200))throw new IllegalArgumentException("Invalid product page");
        Map<String,String> q=new LinkedHashMap<>();q.put("page_size",Integer.toString(size));if(cursor!=null&&!cursor.isEmpty())q.put("page_token",cursor);
        if(shop){q.put("sort_field","PRODUCT_ID");q.put("sort_order","DESC");if(keyword!=null&&!keyword.isEmpty())q.put("title_keyword",keyword);}else q.put("origin","SHOWCASE");
        TkTiktokShopTokenService.AccessToken token=tokens.getAccessToken(a.getTenantId(),a.getCompanyId(),a.getId());
        try {
            JsonNode data=api.get(shop?"/affiliate_creator/202509/shop_products":"/affiliate_creator/202405/showcases/products",token.getValue(),q).getData();
            a.setTokenVersion(token.getVersion());
            if(!data.path("products").isArray() || data.path("products").size()>size)throw new TkTiktokShopApiException("INVALID_RESPONSE",null);
            List<Product> rows=new ArrayList<>();for(JsonNode node:data.path("products"))rows.add(normalize(node,shop));
            return new ProductPage(rows,text(data,"next_page_token"));
        }catch(TkTiktokShopApiException e){tokens.reportRejectedToken(a.getTenantId(),a.getCompanyId(),a.getId(),token.getVersion(),e);throw e;}
    }
    static Product normalize(JsonNode node,boolean shop) {
        Product p=new Product();p.id=text(node,"id");p.title=text(node,"title");
        JsonNode price=shop?node.path("price"):node.path("price").path("original_price");p.price=text(price,shop?"amount":"minimum_amount");p.maximumPrice=text(price,"maximum_amount");p.currency=text(price,"currency");
        if(p.currency==null)p.currency=text(node.path("price"),"currency");
        JsonNode images=shop?node.path("images"):node.path("addition").path("customized_main_images");
        if(!images.isArray()||images.isEmpty())images=node.path("main_images");
        String image=images.isArray()&&!images.isEmpty()?text(images.get(0),"url"):null;
        if(image!=null && image.length()<4096 && image.startsWith("https://"))p.imageUrl=image;
        JsonNode status=node.path("status");
        p.available=p.id!=null&&p.id.matches("[A-Za-z0-9_-]{1,128}") && (shop?"ADDABLE".equals(trim(text(node,"added_status"))):
                "APPROVED".equals(text(status,"review_status"))&&"IN_STOCK".equals(text(status,"inventory_status"))&&status.path("is_hidden").isBoolean()&&!status.path("is_hidden").asBoolean()&&"ADDED".equals(text(status,"added_status")));
        JsonNode regions=node.path("sale_regions");if(!regions.isMissingNode()&&!regions.isNull()){boolean us=false;if(regions.isArray())for(JsonNode region:regions)if("US".equals(region.asText()))us=true;p.available&=us;}
        p.availability=p.available?"AVAILABLE":"UNAVAILABLE";return p;
    }
    private String encode(Selection s){try{byte[] b=json.writeValueAsBytes(s);return Base64.getUrlEncoder().withoutPadding().encodeToString(b)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac(b));}catch(Exception ignored){throw new IllegalStateException("Product selection signing unavailable");}}
    private byte[] mac(byte[] bytes)throws Exception{if(!properties.hasValidSelectionProofKey())throw new IllegalStateException();Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Base64.getDecoder().decode(properties.getSelectionProofKey()),"HmacSHA256"));return mac.doFinal(bytes);}
    private static String text(JsonNode n,String key){JsonNode v=n.path(key);return v.isTextual()?v.asText():null;}
    private static String trim(String s){return s==null?null:s.trim();}
    @Getter @AllArgsConstructor public static class ProductPage {private final List<Product> products;private final String nextPageToken;}
    @Getter @Setter public static class Product {private String id,title,imageUrl,price,maximumPrice,currency,availability,selectionProof;private boolean available;}
    @Getter @Setter public static class Selection {private String id,source,user,pageToken,keyword;private Long accountId,tenantId,authVersion;private long expires;private int pageSize;private Product product;}
}
