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
import java.util.function.Function;

@Service
public class TkTiktokShopTokenService {
    private final TkTiktokShopProperties properties;private final TkTiktokShopAccountMapper accounts;
    private final TkTiktokShopApiClient api;private final TkTiktokShopTokenCipher cipher;private final RedissonClient redis;private final Clock clock;
    @Autowired public TkTiktokShopTokenService(TkTiktokShopProperties p,TkTiktokShopAccountMapper accounts,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,RedissonClient redis){this(p,accounts,api,cipher,redis,Clock.systemUTC());}
    TkTiktokShopTokenService(TkTiktokShopProperties p,TkTiktokShopAccountMapper accounts,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,RedissonClient redis,Clock clock){properties=p;this.accounts=accounts;this.api=api;this.cipher=cipher;this.redis=redis;this.clock=clock;}
    /** Worker must first enter the known task tenant with TenantUtils.execute. No request SecurityContext. */
    public AccessToken getAccessToken(Long tenant,Long company,Long id) {
        return refresh(tenant,company,id,5,true);
    }
    public void refreshInBackground(Long tenant,Long company,Long id,int leadMinutes) {
        refresh(tenant,company,id,leadMinutes,false);
    }
    private AccessToken refresh(Long tenant,Long company,Long id,int leadMinutes,boolean allowCurrent) {
        properties.requireConfigured();TkTiktokShopAccountDO account=require(tenant,company,id);
        if(reusable(account,leadMinutes))return access(account);
        RLock lock=redis.getLock("tk:tiktok-shop:refresh:"+tenant+":"+id);boolean held=false;
        try {
            // Watchdog renewal, rather than a lease expiring while a rotating request is still in flight.
            held=lock.tryLock(0,TimeUnit.SECONDS);
            if(!held){if(allowCurrent && reusable(account,0))return access(account);throw pending(null);}
            account=require(tenant,company,id);if(reusable(account,leadMinutes))return access(account);
            long version=account.getTokenVersion();
            LocalDateTime now=LocalDateTime.now(clock);
            if(account.getRefreshTokenExpireTime()==null || !account.getRefreshTokenExpireTime().isAfter(now) || account.getRefreshTokenCipher()==null) {
                throw reauthorize(account,null);
            }
            if(account.getRefreshRetryAfter()!=null && account.getRefreshRetryAfter().isAfter(now)) {
                if(allowCurrent && reusable(account,0))return access(account);
                throw pending(null);
            }
            TkTiktokShopApiClient.TokenBundle token;
            try {
                token=api.refreshToken(cipher.decrypt(account.getRefreshTokenCipher(),tenant,account.getAppKey(),account.getOpenId()));
                if(!account.getOpenId().equals(token.getOpenId()))throw pending(null);
            } catch(TkTiktokShopApiException e) {
                if("105001".equals(e.getCode()))throw reauthorize(account,e.getRequestId());
                accounts.deferRefresh(id,tenant,version,LocalDateTime.now(clock).plusMinutes(2));
                if(allowCurrent && reusable(account,0))return access(account);
                throw pending(e.getRequestId());
            }
            account.setAccessTokenCipher(cipher.encrypt(token.getAccessToken(),tenant,account.getAppKey(),account.getOpenId()));account.setRefreshTokenCipher(cipher.encrypt(token.getRefreshToken(),tenant,account.getAppKey(),account.getOpenId()));account.setAccessTokenExpireTime(token.getAccessExpireTime());account.setRefreshTokenExpireTime(token.getRefreshExpireTime());account.setGrantedScopes(token.getGrantedScopes());
            if(accounts.replaceTokens(account,version)!=1)throw pending(null);
            return new AccessToken(token.getAccessToken(),version+1,account.getAuthorizationVersion());
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();throw pending(null);}
        finally{if(held && lock.isHeldByCurrentThread())lock.unlock();}
    }
    /** Expire only the rejected access token. A write is never replayed. */
    public void reportRejectedToken(Long tenant,Long company,Long accountId,long usedVersion,TkTiktokShopApiException error) {
        if(!error.isAccessTokenRejected())return;
        TkTiktokShopAccountDO current=require(tenant,company,accountId);
        if(Objects.equals(current.getTokenVersion(),usedVersion))accounts.expireAccess(accountId,tenant,usedVersion,LocalDateTime.now(clock));
    }
    public <T> T read(TkTiktokShopAccountDO account,Function<String,T> query) {
        Long tenant=account.getTenantId(), company=account.getCompanyId(), id=account.getId();
        AccessToken token=getAccessToken(tenant,company,id);
        requireSameAuthorization(account,token);
        try {return query.apply(token.getValue());}
        catch(TkTiktokShopApiException e) {
            if(!e.isAccessTokenRejected())throw e.creatorOperationError();
            reportRejectedToken(tenant,company,id,token.getVersion(),e);
        }
        AccessToken next=getAccessToken(tenant,company,id);
        requireSameAuthorization(account,next);
        try {return query.apply(next.getValue());}
        catch(TkTiktokShopApiException e) {
            reportRejectedToken(tenant,company,id,next.getVersion(),e);
            throw e.creatorOperationError();
        }
    }
    private static void requireSameAuthorization(TkTiktokShopAccountDO account,AccessToken token) {
        if(!Objects.equals(account.getAuthorizationVersion(),token.getAuthorizationVersion()))throw pending(null);
    }
    private TkTiktokShopApiException reauthorize(TkTiktokShopAccountDO a,String requestId) {
        int changed=accounts.markReauthorization(a.getId(),a.getTenantId(),a.getTokenVersion());
        return changed==1?new TkTiktokShopApiException(TkTiktokShopApiException.CREATOR_REAUTHORIZE,requestId):pending(requestId);
    }
    private static TkTiktokShopApiException pending(String requestId){return new TkTiktokShopApiException(TkTiktokShopApiException.CREATOR_REFRESH_PENDING,requestId);}
    private TkTiktokShopAccountDO require(Long tenant,Long company,Long id) {
        if(tenant==null || company==null || id==null || !Objects.equals(tenant,TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw new IllegalStateException("Shop token tenant context mismatch");
        TkTiktokShopAccountDO a=accounts.selectById(id);
        if(a==null || !Objects.equals(a.getTenantId(),tenant) || !Objects.equals(a.getCompanyId(),company) || !Objects.equals(a.getAppKey(),properties.getAppKey()) || !"AUTHORIZED".equals(a.getAuthStatus()) || !Integer.valueOf(1).equals(a.getUserType()))throw new IllegalStateException("Shop authorization unavailable");
        try {if(!TkTiktokShopApiClient.TokenBundle.hasScope(new ObjectMapper().readTree(a.getGrantedScopes())))throw new IllegalArgumentException();}
        catch(Exception ignored){throw new IllegalStateException("Shop authorization requires creator.video.write");}
        return a;
    }
    private boolean reusable(TkTiktokShopAccountDO a,int leadMinutes){return a.getAccessTokenExpireTime()!=null && a.getAccessTokenExpireTime().isAfter(LocalDateTime.now(clock).plusMinutes(leadMinutes));}
    private AccessToken access(TkTiktokShopAccountDO a){return new AccessToken(cipher.decrypt(a.getAccessTokenCipher(),a.getTenantId(),a.getAppKey(),a.getOpenId()),a.getTokenVersion(),a.getAuthorizationVersion());}
    public static final class AccessToken {
        private final String value;private final long version;private final Long authorizationVersion;
        public AccessToken(String value,long version,Long authorizationVersion){this.value=value;this.version=version;this.authorizationVersion=authorizationVersion;}
        public String getValue(){return value;}public long getVersion(){return version;}public Long getAuthorizationVersion(){return authorizationVersion;}
    }
}
