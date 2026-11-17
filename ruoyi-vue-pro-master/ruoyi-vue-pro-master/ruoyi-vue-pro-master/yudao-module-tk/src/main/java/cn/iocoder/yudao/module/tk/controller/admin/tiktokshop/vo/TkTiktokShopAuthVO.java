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
        public SessionResponse(Long id,String url,LocalDateTime expiry){sessionId=id;authorizationUrl=url;expireTime=expiry;}
    }
    @Getter public static class CallbackResult {
        private final Long sessionId; private final String status,authType,errorCode,message;
        public CallbackResult(Long id,boolean success){this(id,success,"CREATOR",success?null:"INVALID_STATE");}
        public CallbackResult(Long id,boolean success,String type,String error){sessionId=id;status=success?"SUCCESS":"FAILED";authType="SELLER".equals(type)?"SELLER":"CREATOR";errorCode=error;message=success?"授权完成，请返回授权页面。":"CONFIGURATION_CHANGED".equals(error)?"授权配置已变更，请重新发起授权。":id==null?"授权会话无效、已过期或已使用，请重新发起授权。":"授权未完成，请确认账号、权限和应用配置后重试。";}
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
        public AccountResponse(TkTiktokShopAccountDO a){id=a.getId();companyId=a.getCompanyId();displayName=a.getDisplayName();username=a.getUsername();market=a.getMarket();authStatus=a.getAuthStatus();grantedScopes=a.getGrantedScopes();accessTokenExpireTime=a.getAccessTokenExpireTime();refreshTokenExpireTime=a.getRefreshTokenExpireTime();}
    }
    @Getter public static class SellerAccountResponse {
        private final Long id,companyId;
        private final String displayName,market,authStatus,environment,grantedScopes;
        private final boolean canReadProducts,canReadOrders;
        private final LocalDateTime accessTokenExpireTime,refreshTokenExpireTime;
        public SellerAccountResponse(TkTiktokShopSellerAccountDO a){id=a.getId();companyId=a.getCompanyId();displayName=a.getDisplayName();market=a.getMarket();authStatus=a.getAuthStatus();environment=a.getEnvironment();grantedScopes=a.getGrantedScopes();accessTokenExpireTime=a.getAccessTokenExpireTime();refreshTokenExpireTime=a.getRefreshTokenExpireTime();canReadProducts="AUTHORIZED".equals(authStatus)&&hasScope(grantedScopes,"seller.product.basic");canReadOrders="AUTHORIZED".equals(authStatus)&&hasScope(grantedScopes,"seller.order.info");}
        private static boolean hasScope(String raw,String scope){try{return cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopApiClient.TokenBundle.hasScope(new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw),scope);}catch(Exception ignored){return false;}}
    }
    @Getter public static class SellerShopResponse {
        private final Long id,accountId,companyId;
        private final String shopId,name,region,status,environment;
        public SellerShopResponse(TkTiktokShopSellerShopDO s){id=s.getId();accountId=s.getAccountId();companyId=s.getCompanyId();shopId=s.getShopId();name=s.getName();region=s.getRegion();status=s.getStatus();environment=s.getEnvironment();}
    }

}
