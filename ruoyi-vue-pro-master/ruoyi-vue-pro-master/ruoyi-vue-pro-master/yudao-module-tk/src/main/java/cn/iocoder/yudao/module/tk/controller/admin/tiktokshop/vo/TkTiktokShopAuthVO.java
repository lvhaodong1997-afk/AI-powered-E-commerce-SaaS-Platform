package cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** Explicit safe API contracts; never serialize configuration or account entities. */
public final class TkTiktokShopAuthVO {
    private TkTiktokShopAuthVO() {}
    @Getter @Setter public static class SessionRequest { private Long companyId; }
    @Getter public static class SessionResponse {
        private final Long sessionId; private final String authorizationUrl; private final LocalDateTime expireTime;
        public SessionResponse(Long id,String url,LocalDateTime expiry){sessionId=id;authorizationUrl=url;expireTime=expiry;}
    }
    @Getter public static class CallbackResult {
        private final Long sessionId; private final String status;
        public CallbackResult(Long id,boolean success){sessionId=id;status=success?"SUCCESS":"FAILED";}
    }
    @Getter public static class Capabilities {
        private final boolean enabled,configured,supportsProducts=true,supportsPrecheck=true;
        private final String market="US",requiredScope="creator.video.write",callbackOrigin;
        private final long maxVideoBytes=524288000L;
        public Capabilities(TkTiktokShopProperties p){enabled=p.isEnabled();configured=p.isConfigured();callbackOrigin=p.callbackOrigin();}
    }
    @Getter public static class AccountResponse {
        private final Long id,companyId; private final String displayName,username,market,authStatus,grantedScopes;
        private final LocalDateTime accessTokenExpireTime,refreshTokenExpireTime;
        public AccountResponse(TkTiktokShopAccountDO a){id=a.getId();companyId=a.getCompanyId();displayName=a.getDisplayName();username=a.getUsername();market=a.getMarket();authStatus=a.getAuthStatus();grantedScopes=a.getGrantedScopes();accessTokenExpireTime=a.getAccessTokenExpireTime();refreshTokenExpireTime=a.getRefreshTokenExpireTime();}
    }
}
