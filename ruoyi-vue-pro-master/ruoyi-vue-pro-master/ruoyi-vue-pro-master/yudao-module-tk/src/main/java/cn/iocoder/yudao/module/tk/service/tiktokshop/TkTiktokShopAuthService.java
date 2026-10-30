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
    private final SecureRandom random=new SecureRandom();
    @Autowired
    public TkTiktokShopAuthService(TkTiktokShopProperties p,TkTiktokShopAuthSessionMapper sessions,TkTiktokShopAccountMapper accounts,TkDataScopeService scope,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,ObjectMapper json,PlatformTransactionManager tx) {
        this(p,sessions,accounts,scope,api,cipher,json,tx,Clock.systemUTC());
    }
    TkTiktokShopAuthService(TkTiktokShopProperties p,TkTiktokShopAuthSessionMapper sessions,TkTiktokShopAccountMapper accounts,TkDataScopeService scope,TkTiktokShopApiClient api,TkTiktokShopTokenCipher cipher,ObjectMapper json,PlatformTransactionManager tx,Clock clock) {
        properties=p;this.sessions=sessions;this.accounts=accounts;this.scope=scope;this.api=api;this.cipher=cipher;this.transaction=new TransactionTemplate(tx);this.clock=clock;
    }
    public SessionResponse createSession(Long requestedCompany) {
        properties.requireConfigured();TkUserScope current=current();Long company=scope.getWritableCompanyId(requestedCompany);
        if(company==null || (requestedCompany!=null && !requestedCompany.equals(company)))throw denied();
        scope.validateWritable(current.getTenantId(),company);
        byte[] bytes=new byte[32];random.nextBytes(bytes);String state=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TkTiktokShopAuthSessionDO session=new TkTiktokShopAuthSessionDO();session.setTenantId(current.getTenantId());session.setCompanyId(company);session.setUserId(current.getUserId());session.setCreator(current.getUserIdString());session.setUpdater(current.getUserIdString());
        session.setStateHash(hash(state));session.setStatus("WAITING");session.setExpireTime(LocalDateTime.now(clock).plusMinutes(10));sessions.insert(session);
        String url=HttpUrl.parse("https://shop.tiktok.com/alliance/creator/auth").newBuilder().addQueryParameter("app_key",properties.getAppKey()).addQueryParameter("state",state).build().toString();
        return new SessionResponse(session.getId(),url,session.getExpireTime());
    }
    public CallbackResult callback(String code,String state,String error) {
        if(!properties.isConfigured() || state==null || !state.matches("[A-Za-z0-9_-]{43}"))return new CallbackResult(null,false);
        // The only global read/write is a random state hash claim. No caller-supplied tenant/account key bypass.
        TkTiktokShopAuthSessionDO session;
        try {session=TenantUtils.executeIgnore(()->{String digest=hash(state);if(sessions.claim(digest,LocalDateTime.now(clock))!=1)return null;return sessions.findClaimed(digest);});}
        catch(RuntimeException ignored){return new CallbackResult(null,false);}
        if(session==null || session.getTenantId()==null || session.getTenantId()<=0)return new CallbackResult(null,false);
        return TenantUtils.execute(session.getTenantId(),()->{
            try {
                if(error!=null || code==null || code.trim().isEmpty() || "null".equals(code))throw new IllegalArgumentException();
                // Remote calls complete before beginning the local transaction.
                TkTiktokShopApiClient.TokenBundle token=api.exchangeCode(code);
                TkTiktokShopApiClient.CreatorProfile profile=api.getCreatorProfile(token.getAccessToken());
                if(!"US".equals(profile.getSelectionRegion()))throw new IllegalArgumentException();
                transaction.execute(tx->{bind(session,token,profile);if(sessions.finish(session.getId(),session.getTenantId(),"SUCCESS",LocalDateTime.now(clock))!=1)throw new IllegalStateException();return null;});
                return new CallbackResult(session.getId(),true);
            }catch(RuntimeException ignored){
                try {sessions.finish(session.getId(),session.getTenantId(),"FAILED",LocalDateTime.now(clock));}catch(RuntimeException ignoredAgain){/* Single-use PROCESSING remains closed on storage failure. */}
                return new CallbackResult(session.getId(),false);
            }
        });
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
