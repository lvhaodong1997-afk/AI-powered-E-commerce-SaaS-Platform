package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAccountMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class TkTiktokShopTokenService {
    private final TkTiktokShopProperties properties;private final TkTiktokShopAccountMapper accounts;
    private final TkTiktokShopApiClient api;private final TkTiktokShopTokenCipher cipher;private final RedissonClient redis;private final Clock clock;
    @Autowired public TkTiktokShopTokenService(TkTiktokShopProperties p,TkTiktokShopAccountMapper accounts,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,RedissonClient redis){this(p,accounts,api,cipher,redis,Clock.systemUTC());}
    TkTiktokShopTokenService(TkTiktokShopProperties p,TkTiktokShopAccountMapper accounts,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,RedissonClient redis,Clock clock){properties=p;this.accounts=accounts;this.api=api;this.cipher=cipher;this.redis=redis;this.clock=clock;}
    /** Worker must first enter the known task tenant with TenantUtils.execute. No request SecurityContext. */
    public AccessToken getAccessToken(Long tenant,Long company,Long id) {
        properties.requireConfigured();TkTiktokShopAccountDO account=require(tenant,company,id);
        if(reusable(account))return access(account);
        RLock lock=redis.getLock("tk:tiktok-shop:refresh:"+tenant+":"+id);boolean held=false;
        try {
            // Watchdog renewal, rather than a lease expiring while a rotating request is still in flight.
            held=lock.tryLock(0,TimeUnit.SECONDS);if(!held)throw new IllegalStateException("Shop token refresh in progress");
            account=require(tenant,company,id);if(reusable(account))return access(account);
            long version=account.getTokenVersion();
            if(account.getRefreshTokenExpireTime()==null || !account.getRefreshTokenExpireTime().isAfter(LocalDateTime.now(clock)))throw new TkTiktokShopApiException("INVALID_TOKEN",null);
            TkTiktokShopApiClient.TokenBundle token=api.refreshToken(cipher.decrypt(account.getRefreshTokenCipher(),tenant,account.getAppKey(),account.getOpenId()));
            if(!account.getOpenId().equals(token.getOpenId()))throw new TkTiktokShopApiException("INVALID_IDENTITY",null);
            account.setAccessTokenCipher(cipher.encrypt(token.getAccessToken(),tenant,account.getAppKey(),account.getOpenId()));account.setRefreshTokenCipher(cipher.encrypt(token.getRefreshToken(),tenant,account.getAppKey(),account.getOpenId()));account.setAccessTokenExpireTime(token.getAccessExpireTime());account.setRefreshTokenExpireTime(token.getRefreshExpireTime());account.setGrantedScopes(token.getGrantedScopes());
            if(accounts.replaceTokens(account,version)!=1)throw new IllegalStateException("Shop account authorization changed");
            return new AccessToken(token.getAccessToken(),version+1);
        }catch(TkTiktokShopApiException e){if(e.isReauthorizationRequired())accounts.markReauthorization(id,tenant,account.getTokenVersion());throw e;}
        catch(InterruptedException ignored){Thread.currentThread().interrupt();throw new IllegalStateException("Shop token refresh interrupted");}
        finally{if(held && lock.isHeldByCurrentThread())lock.unlock();}
    }
    /** A business API rejection invalidates only the exact authorization version used. Never retries publishing. */
    public void reportRejectedToken(Long tenant,Long company,Long accountId,long usedVersion,TkTiktokShopApiException error) {
        require(tenant,company,accountId);if(error.isReauthorizationRequired())accounts.markReauthorization(accountId,tenant,usedVersion);
    }
    private TkTiktokShopAccountDO require(Long tenant,Long company,Long id) {
        if(tenant==null || company==null || id==null || !Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw new IllegalStateException("Shop token tenant context mismatch");
        TkTiktokShopAccountDO a=accounts.selectById(id);
        if(a==null || !Objects.equals(a.getTenantId(),tenant) || !Objects.equals(a.getCompanyId(),company) || !Objects.equals(a.getAppKey(),properties.getAppKey()) || !"AUTHORIZED".equals(a.getAuthStatus()) || !Integer.valueOf(1).equals(a.getUserType()))throw new IllegalStateException("Shop authorization unavailable");
        try {if(!TkTiktokShopApiClient.TokenBundle.hasScope(new ObjectMapper().readTree(a.getGrantedScopes())))throw new IllegalArgumentException();}
        catch(Exception ignored){throw new IllegalStateException("Shop authorization requires creator.video.write");}
        return a;
    }
    private boolean reusable(TkTiktokShopAccountDO a){return a.getAccessTokenExpireTime()!=null && a.getAccessTokenExpireTime().isAfter(LocalDateTime.now(clock).plusMinutes(5));}
    private AccessToken access(TkTiktokShopAccountDO a){return new AccessToken(cipher.decrypt(a.getAccessTokenCipher(),a.getTenantId(),a.getAppKey(),a.getOpenId()),a.getTokenVersion());}
    public static final class AccessToken {
        private final String value;private final long version;
        public AccessToken(String value,long version){this.value=value;this.version=version;}
        public String getValue(){return value;}public long getVersion(){return version;}
    }
}
