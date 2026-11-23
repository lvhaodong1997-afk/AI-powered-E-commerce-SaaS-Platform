package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Explicit safe API contracts; never serialize configuration or account entities. */
public final class TkTiktokShopAuthVO {
    private TkTiktokShopAuthVO() {}
    @Getter @Setter public static class SessionRequest { private Long companyId; private String authType; }
    @Getter public static class SessionResponse {
        private final Long sessionId; private final String authorizationUrl; private final LocalDateTime expireTime;
        private final long expiresInSeconds;
        public SessionResponse(Long id,String url,LocalDateTime expiry){this(id,url,expiry,0);}
        public SessionResponse(Long id,String url,LocalDateTime expiry,long seconds){sessionId=id;authorizationUrl=url;expireTime=expiry;expiresInSeconds=Math.max(0,Math.min(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopAuthService.SESSION_TTL_SECONDS,seconds));}
    }
    @Getter public static class CallbackResult {
        private final Long sessionId; private final String status,authType,errorCode,message,requestId;
        public CallbackResult(Long id,boolean success){this(id,success,"CREATOR",success?null:"INVALID_STATE",null);}
        public CallbackResult(Long id,boolean success,String type,String error){this(id,success,type,error,null);}
        public CallbackResult(Long id,boolean success,String type,String error,String requestId){
            sessionId=id;status=success?"SUCCESS":"FAILED";authType="SELLER".equals(type)?"SELLER":"CREATOR";
            errorCode=success?null:safeCode(error,id);
            this.requestId=success||id==null?null:requestId!=null&&requestId.matches("[A-Za-z0-9_:-]{1,128}")?requestId:null;
            message=success?"授权完成，请返回授权页面。":messageFor(errorCode);
        }
        private static String safeCode(String code,Long id){
            if(id==null)return "INVALID_STATE";
            if("CONFIGURATION_CHANGED".equals(code)||"WRONG_ACCOUNT_TYPE".equals(code)||"INSUFFICIENT_SCOPE".equals(code)||"PROVIDER_ERROR".equals(code))return code;
            return "AUTHORIZATION_FAILED";
        }
        public static CallbackResult fromStoredFailure(Long id,String type,String reason){
            if(reason==null || reason.isEmpty())return new CallbackResult(id,false,type,"AUTHORIZATION_FAILED",null);
            String[] parts=reason.split(":",3);
            String code=parts[0];
            String requestId=null;
            if("PROVIDER_ERROR".equals(code)){
                if(parts.length==3)requestId=parts[2];
                else if(parts.length==2)requestId=parts[1];
            }
            return new CallbackResult(id,false,type,code,requestId);
        }
        private static String messageFor(String code){
            switch(code){
                case "INVALID_STATE":return "授权会话无效、已过期或已使用，请重新发起授权。";
                case "CONFIGURATION_CHANGED":return "授权配置已变更，请重新发起授权。";
                case "WRONG_ACCOUNT_TYPE":return "账号类型不匹配，请选择正确的创作者或卖家账号重新授权。";
                case "INSUFFICIENT_SCOPE":return "账号授权权限不足，请检查应用权限并重新授权。";
                case "PROVIDER_ERROR":return "平台授权暂未完成，请稍后重试，必要时联系管理员并提供请求 ID。";
                default:return "授权未完成，请确认账号、权限和应用配置后重试。";
            }
        }
    }
    @Getter public static class Capabilities {
        private final boolean enabled,configured,publishingConfigured,supportsProducts,supportsPrecheck;
        private final java.util.List<String> supportedVideoFormats=java.util.Arrays.asList("MP4","MOV","WEBM");
        private final String market="US",requiredScope="creator.video.write",callbackOrigin;
        private final long maxVideoBytes=524288000L;
        private final boolean sellerEnabled,sellerConfigured;
        private final String sellerEnvironment,creatorEnvironment,sellerConfigurationMessage;
        private final java.util.List<String> sellerRequiredScopes=java.util.Collections.singletonList("seller.authorization.info");
        public Capabilities(TkTiktokShopProperties p){sellerEnabled=p.getSeller()!=null&&p.getSeller().isEnabled();sellerConfigured=p.isSellerConfigured();sellerEnvironment=p.getSeller()==null?"PRODUCTION":p.getSeller().getEnvironment();creatorEnvironment=p.getEnvironment();sellerConfigurationMessage=sellerConfigured?"卖家授权已配置":sellerEnabled?"卖家授权配置不完整，请联系管理员检查卖家应用和官方授权链接":"卖家授权尚未启用，请联系管理员配置卖家应用、官方授权链接和卖家权限";enabled=p.isEnabled();configured=p.isConfigured();publishingConfigured=p.isPublishingConfigured();supportsProducts=publishingConfigured;supportsPrecheck=publishingConfigured;callbackOrigin=p.callbackOrigin();}
    }
    @Getter public static class AccountResponse {
        private final Long id,companyId; private final String displayName,username,market,authStatus,grantedScopes;
        private final LocalDateTime accessTokenExpireTime,refreshTokenExpireTime;
        private final Long refreshRetryAfter;
        public AccountResponse(TkTiktokShopAccountDO a){id=a.getId();companyId=a.getCompanyId();displayName=a.getDisplayName();username=a.getUsername();market=a.getMarket();authStatus=a.getAuthStatus();grantedScopes=a.getGrantedScopes();accessTokenExpireTime=a.getAccessTokenExpireTime();refreshTokenExpireTime=a.getRefreshTokenExpireTime();refreshRetryAfter=a.getRefreshRetryAfter()==null?null:a.getRefreshRetryAfter().atOffset(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();}
    }
    @Getter public static class SellerAccountResponse {
        private final Long id,companyId;
        private final String displayName,market,authStatus,environment,grantedScopes;
        private final boolean canReadProducts,canReadOrders;
        private final LocalDateTime accessTokenExpireTime,refreshTokenExpireTime;
        public SellerAccountResponse(TkTiktokShopSellerAccountDO a){id=a.getId();companyId=a.getCompanyId();displayName=a.getDisplayName();market=a.getMarket();authStatus=a.getAuthStatus();environment=a.getEnvironment();grantedScopes=a.getGrantedScopes();accessTokenExpireTime=a.getAccessTokenExpireTime();refreshTokenExpireTime=a.getRefreshTokenExpireTime();canReadProducts="AUTHORIZED".equals(authStatus)&&cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.hasGrantedScope(grantedScopes,"seller.product.basic");canReadOrders="AUTHORIZED".equals(authStatus)&&cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.hasOrderScope(grantedScopes);}
        private static boolean hasScope(String raw,String scope){try{return cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopApiClient.TokenBundle.hasScope(new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw),scope);}catch(Exception ignored){return false;}}
    }
    @Getter public static class SellerShopResponse {
        private final Long id,accountId,companyId;
        private final String shopId,name,region,status,environment;
        public SellerShopResponse(TkTiktokShopSellerShopDO s){id=s.getId();accountId=s.getAccountId();companyId=s.getCompanyId();shopId=s.getShopId();name=s.getName();region=s.getRegion();status=s.getStatus();environment=s.getEnvironment();}
    }

    @Getter public static class SellerProductPage {
        private final java.util.List<SellerProduct> products; private final Long total; private final String nextPageToken;
        public SellerProductPage(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.CatalogPage<cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.ProductSummary> page){
            java.util.List<SellerProduct> rows=new java.util.ArrayList<>();
            for(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.ProductSummary item:page.getItems())rows.add(new SellerProduct(item));
            products=java.util.Collections.unmodifiableList(rows);total=page.getTotal();nextPageToken=page.getNextPageToken();
        }
    }
    @Getter public static class SellerProduct {
        private final String id,title,status;
        public SellerProduct(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.ProductSummary item){id=item.getId();title=item.getTitle();status=item.getStatus();}
    }
    @Getter public static class SellerOrderPage {
        private final java.util.List<SellerOrder> orders; private final Long total; private final String nextPageToken;
        public SellerOrderPage(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.CatalogPage<cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.OrderSummary> page){
            java.util.List<SellerOrder> rows=new java.util.ArrayList<>();
            for(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.OrderSummary item:page.getItems())rows.add(new SellerOrder(item));
            orders=java.util.Collections.unmodifiableList(rows);total=page.getTotal();nextPageToken=page.getNextPageToken();
        }
    }
    @Getter public static class SellerOrder {
        private final String id,status,totalAmount,currency; private final long createTime;
        public SellerOrder(cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopSellerApiClient.OrderSummary item){id=item.getId();status=item.getStatus();createTime=item.getCreateTime();totalAmount=item.getTotalAmount();currency=item.getCurrency();}
    }
}
