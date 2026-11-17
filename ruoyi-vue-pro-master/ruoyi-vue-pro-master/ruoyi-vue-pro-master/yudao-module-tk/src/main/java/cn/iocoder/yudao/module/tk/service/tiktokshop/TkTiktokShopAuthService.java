package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkTiktokShopAuthService {
    private final TkTiktokShopProperties properties;
    private final TkTiktokShopAuthSessionMapper sessions;
    private final TkTiktokShopAccountMapper accounts;
    private final TkDataScopeService scope;
    private final TkTiktokShopApiClient api;
    private final TkTiktokShopTokenCipher cipher;
    private final TransactionTemplate transaction;
    private final Clock clock;
    @Autowired private TkTiktokShopSellerAuthService sellerAuth;
    private final SecureRandom random=new SecureRandom();
    @Autowired
    public TkTiktokShopAuthService(TkTiktokShopProperties p,TkTiktokShopAuthSessionMapper sessions,TkTiktokShopAccountMapper accounts,TkDataScopeService scope,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,ObjectMapper json,PlatformTransactionManager tx) {
        this(p,sessions,accounts,scope,api,cipher,json,tx,Clock.systemUTC());
    }
    TkTiktokShopAuthService(TkTiktokShopProperties p,TkTiktokShopAuthSessionMapper sessions,TkTiktokShopAccountMapper accounts,TkDataScopeService scope,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,ObjectMapper json,PlatformTransactionManager tx,Clock clock) {
        properties=p;this.sessions=sessions;this.accounts=accounts;this.scope=scope;this.api=api;this.cipher=cipher;this.transaction=new TransactionTemplate(tx);this.clock=clock;
    }
    public SessionResponse createSession(Long requestedCompany) {return createSession(requestedCompany,null);}
    public SessionResponse createSession(Long requestedCompany,String requestedType) {
        String type=requestedType==null?"CREATOR":requestedType;
        if(!"CREATOR".equals(type) && !"SELLER".equals(type))throw new IllegalArgumentException("Unsupported authorization type");
        boolean seller="SELLER".equals(type);if(seller)properties.requireSellerConfigured();else properties.requireConfigured();TkUserScope current=current();Long company=scope.getWritableCompanyId(requestedCompany);
        if(company==null || (requestedCompany!=null && !requestedCompany.equals(company)))throw denied();
        scope.validateWritable(current.getTenantId(),company);
        byte[] bytes=new byte[32];random.nextBytes(bytes);String state=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TkTiktokShopAuthSessionDO session=new TkTiktokShopAuthSessionDO();session.setTenantId(current.getTenantId());session.setCompanyId(company);session.setUserId(current.getUserId());session.setCreator(current.getUserIdString());session.setUpdater(current.getUserIdString());
        session.setAuthType(type);session.setAppKey(seller?properties.getSeller().getAppKey():properties.getAppKey());session.setEnvironment(seller?properties.getSeller().getEnvironment():properties.getEnvironment());session.setConfigurationHash(configurationHash(seller));
        session.setStateHash(hash(state));session.setStatus("WAITING");session.setExpireTime(LocalDateTime.now(clock).plusMinutes(10));sessions.insert(session);
        HttpUrl.Builder builder=HttpUrl.parse(seller?properties.getSeller().getAuthorizationUrl():"https://shop.tiktok.com/alliance/creator/auth").newBuilder();
        if(!seller)builder.addQueryParameter("app_key",properties.getAppKey());
        String url=builder.addQueryParameter("state",state).build().toString();
        return new SessionResponse(session.getId(),url,session.getExpireTime(),Math.max(0,java.time.Duration.between(LocalDateTime.now(clock),session.getExpireTime()).getSeconds()));
    }
    public CallbackResult callback(String code,String state,String error) {
        if(state==null || !state.matches("[A-Za-z0-9_-]{43}"))return new CallbackResult(null,false);
        // The only global read/write is a random state hash claim. No caller-supplied tenant/account key bypass.
        TkTiktokShopAuthSessionDO session;
        try {session=TenantUtils.executeIgnore(()->{String digest=hash(state);if(sessions.claim(digest,LocalDateTime.now(clock))!=1)return null;return sessions.findClaimed(digest);});}
        catch(RuntimeException ignored){return new CallbackResult(null,false);}
        if(session==null || session.getTenantId()==null || session.getTenantId()<=0)return new CallbackResult(null,false);
        return TenantUtils.execute(session.getTenantId(),()->{
            String type=session.getAuthType()==null?"CREATOR":session.getAuthType();String errorCode="AUTHORIZATION_FAILED";String requestId=null;
            try {
                boolean seller="SELLER".equals(type);
                if(!seller && !"CREATOR".equals(type))throw new IllegalStateException();
                if(seller)properties.requireSellerConfigured();else properties.requireConfigured();
                if((session.getConfigurationHash()==null && (seller || session.getAppKey()!=null)) || (session.getConfigurationHash()!=null && (!Objects.equals(session.getConfigurationHash(),configurationHash(seller)) || !Objects.equals(session.getAppKey(),seller?properties.getSeller().getAppKey():properties.getAppKey()) || !Objects.equals(session.getEnvironment(),seller?properties.getSeller().getEnvironment():properties.getEnvironment())))) {
                    errorCode="CONFIGURATION_CHANGED";throw new IllegalStateException();
                }
                if(error!=null || code==null || code.trim().isEmpty() || "null".equals(code))throw new IllegalArgumentException();
                if(seller){
                    TkTiktokShopApiClient.TokenBundle sellerToken=sellerAuth.exchangeCode(code);
                    List<TkTiktokShopSellerApiClient.AuthorizedShop> shops=sellerAuth.fetchShops(sellerToken.getAccessToken());
                    transaction.execute(tx->{sellerAuth.bind(session,sellerToken,shops);if(sessions.finish(session.getId(),session.getTenantId(),"SUCCESS",LocalDateTime.now(clock))!=1)throw new IllegalStateException();return null;});
                    return new CallbackResult(session.getId(),true,type,null);
                }
                // Remote calls complete before beginning the local transaction.
                TkTiktokShopApiClient.TokenBundle token=api.exchangeCode(code);
                TkTiktokShopApiClient.CreatorProfile profile=api.getCreatorProfile(token.getAccessToken());
                if(!"US".equals(profile.getSelectionRegion()))throw new IllegalArgumentException();
                transaction.execute(tx->{bind(session,token,profile);if(sessions.finish(session.getId(),session.getTenantId(),"SUCCESS",LocalDateTime.now(clock))!=1)throw new IllegalStateException();return null;});
                return new CallbackResult(session.getId(),true,type,null);
            }catch(RuntimeException failure){
                if(!"CONFIGURATION_CHANGED".equals(errorCode) && failure instanceof TkTiktokShopApiException){
                    TkTiktokShopApiException upstream=(TkTiktokShopApiException)failure;
                    errorCode="WRONG_ACCOUNT_TYPE".equals(upstream.getCode())?"WRONG_ACCOUNT_TYPE":"INSUFFICIENT_SCOPE".equals(upstream.getCode())?"INSUFFICIENT_SCOPE":"PROVIDER_ERROR";
                    requestId=upstream.getRequestId();
                }
                String safeFailure=requestId==null?errorCode:errorCode+":"+requestId;
                try {sessions.recordFailure(session.getId(),session.getTenantId(),safeFailure);sessions.finish(session.getId(),session.getTenantId(),"FAILED",LocalDateTime.now(clock));}catch(RuntimeException ignoredAgain){/* Single-use PROCESSING remains closed on storage failure. */}
                return new CallbackResult(session.getId(),false,type,errorCode,requestId);
            }
        });
    }
    private String configurationHash(boolean seller) {
        String app=seller?properties.getSeller().getAppKey():properties.getAppKey();String secret=seller?properties.getSeller().getAppSecret():properties.getAppSecret();
        String env=seller?properties.getSeller().getEnvironment():properties.getEnvironment();String url=seller?properties.getSeller().getAuthorizationUrl():"CREATOR";
        return hash(java.util.Arrays.asList(seller?"SELLER":"CREATOR",app,secret,env,url,properties.getRedirectUri(),properties.getFrontendOrigin(),properties.getTokenEncryptionKey()).stream().map(v->v==null?"-1:":v.length()+":"+v).collect(Collectors.joining()));
    }
    private void bind(TkTiktokShopAuthSessionDO session,TkTiktokShopApiClient.TokenBundle token,TkTiktokShopApiClient.CreatorProfile profile) {
        TkTiktokShopAccountDO existing=accounts.findIdentity(session.getTenantId(),properties.getAppKey(),token.getOpenId());
        if(existing!=null && (!Objects.equals(existing.getTenantId(),session.getTenantId()) || !Objects.equals(existing.getCompanyId(),session.getCompanyId()) || !Objects.equals(existing.getCreator(),String.valueOf(session.getUserId()))))throw denied();
        TkTiktokShopAccountDO a=existing==null?new TkTiktokShopAccountDO():existing;long version=existing==null?0:existing.getTokenVersion();
        a.setTenantId(session.getTenantId());a.setCompanyId(session.getCompanyId());a.setCreator(String.valueOf(session.getUserId()));a.setUpdater(String.valueOf(session.getUserId()));a.setAppKey(properties.getAppKey());a.setOpenId(token.getOpenId());a.setUsername(profile.getUsername());a.setDisplayName(profile.getUsername());a.setMarket("US");a.setUserType(1);a.setGrantedScopes(token.getGrantedScopes());a.setAuthStatus("AUTHORIZED");
        a.setAccessTokenCipher(cipher.encrypt(token.getAccessToken(),a.getTenantId(),a.getAppKey(),a.getOpenId()));a.setRefreshTokenCipher(cipher.encrypt(token.getRefreshToken(),a.getTenantId(),a.getAppKey(),a.getOpenId()));a.setAccessTokenExpireTime(token.getAccessExpireTime());a.setRefreshTokenExpireTime(token.getRefreshExpireTime());
        if(existing==null){a.setTokenVersion(1L);accounts.insert(a);}else if(accounts.reauthorize(a,version)!=1)throw new IllegalStateException("Shop account authorization changed");
    }
    public List<AccountResponse> listAccounts() {
        TkUserScope current=current();Long company=scope.getWritableCompanyId(null);
        LambdaQueryWrapperX<TkTiktokShopAccountDO> query=new LambdaQueryWrapperX<TkTiktokShopAccountDO>().eq(TkTiktokShopAccountDO::getTenantId,current.getTenantId()).eq(TkTiktokShopAccountDO::getAppKey,properties.getAppKey());
        if(!current.isPlatformAdmin())query.eq(TkTiktokShopAccountDO::getCompanyId,company);
        if(!current.canReadAllTenantRecords())query.eq(TkTiktokShopAccountDO::getCreator,current.getUserIdString());
        return accounts.selectList(query).stream().map(a->{validate(a,current);return new AccountResponse(a);}).collect(Collectors.toList());
    }
    public TkTiktokShopAccountDO requireReadable(Long id) {
        TkUserScope current=current();TkTiktokShopAccountDO a=accounts.selectById(id);validate(a,current);return a;
    }
    public void disconnect(Long id) {TkTiktokShopAccountDO a=requireReadable(id);scope.validateWritable(a.getTenantId(),a.getCompanyId());accounts.disconnect(a.getId(),a.getTenantId());}
    private TkUserScope current() {
        TkUserScope current=scope.getCurrentScope();
        if(current==null || !current.hasTenantScope() || current.getUserId()==null || !Objects.equals(current.getTenantId(),TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw denied();
        return current;
    }
    private void validate(TkTiktokShopAccountDO a,TkUserScope current) {
        if(a==null || !Objects.equals(a.getTenantId(),current.getTenantId()) || !Objects.equals(a.getAppKey(),properties.getAppKey())
                || (!current.isPlatformAdmin() && !Objects.equals(a.getCompanyId(),scope.getWritableCompanyId(null)))
                || (!current.canReadAllTenantRecords() && !Objects.equals(a.getCreator(),current.getUserIdString())))throw denied();
        scope.validateReadable(a.getTenantId(),a.getCompanyId(),a.getCreator());
    }
    private static IllegalArgumentException denied(){return new IllegalArgumentException("Shop account unavailable or access denied");}
    public static String hash(String state) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format("%02x",b&255));return hex.toString();}
        catch(NoSuchAlgorithmException ignored){throw new IllegalStateException("State hashing unavailable");}
    }
}
