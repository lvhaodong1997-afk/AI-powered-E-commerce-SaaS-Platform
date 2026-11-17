package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkTiktokShopSellerAuthService {
    private final TkTiktokShopProperties properties;
    private final TkTiktokShopSellerAccountMapper accounts;
    private final TkTiktokShopSellerShopMapper shops;
    private final TkDataScopeService scope;
    private final TkTiktokShopSellerApiClient api;
    private final TkTiktokShopTokenCipher cipher;
    public TkTiktokShopSellerAuthService(TkTiktokShopProperties properties,TkTiktokShopSellerAccountMapper accounts,TkTiktokShopSellerShopMapper shops,TkDataScopeService scope,TkTiktokShopSellerApiClient api,TkTiktokShopTokenCipher cipher) {
        this.properties=properties;this.accounts=accounts;this.shops=shops;this.scope=scope;this.api=api;this.cipher=cipher;
    }
    public TkTiktokShopApiClient.TokenBundle exchangeCode(String code){return api.exchangeCode(code);}
    public List<TkTiktokShopSellerApiClient.AuthorizedShop> fetchShops(String token){return api.getAuthorizedShops(token);}
    /** Caller includes this write and session finish in the same transaction. */
    public void bind(TkTiktokShopAuthSessionDO session,TkTiktokShopApiClient.TokenBundle token,List<TkTiktokShopSellerApiClient.AuthorizedShop> authorized) {
        String app=properties.getSeller().getAppKey(),env=properties.getSeller().getEnvironment();
        TkTiktokShopSellerAccountDO a=accounts.findIdentity(session.getTenantId(),app,env,token.getOpenId());
        if(a!=null && (!Objects.equals(a.getTenantId(),session.getTenantId()) || !Objects.equals(a.getCompanyId(),session.getCompanyId()) || !Objects.equals(a.getCreator(),String.valueOf(session.getUserId()))))throw denied();
        boolean fresh=a==null;long version=fresh?0:a.getTokenVersion();if(fresh)a=new TkTiktokShopSellerAccountDO();
        a.setTenantId(session.getTenantId());a.setCompanyId(session.getCompanyId());a.setCreator(String.valueOf(session.getUserId()));a.setUpdater(String.valueOf(session.getUserId()));
        a.setAppKey(app);a.setEnvironment(env);a.setOpenId(token.getOpenId());a.setUserType(0);a.setAuthStatus("AUTHORIZED");a.setGrantedScopes(token.getGrantedScopes());
        a.setDisplayName(authorized.isEmpty()?"TikTok Shop Seller":authorized.get(0).getName());a.setMarket(authorized.isEmpty()?null:authorized.get(0).getRegion());
        a.setAccessTokenCipher(cipher.encryptSeller(token.getAccessToken(),a.getTenantId(),app,env,a.getOpenId()));a.setRefreshTokenCipher(cipher.encryptSeller(token.getRefreshToken(),a.getTenantId(),app,env,a.getOpenId()));
        a.setAccessTokenExpireTime(token.getAccessExpireTime());a.setRefreshTokenExpireTime(token.getRefreshExpireTime());
        if(fresh){a.setTokenVersion(1L);accounts.insert(a);}else if(accounts.reauthorize(a,version)!=1)throw new IllegalStateException("Seller authorization changed");
        List<TkTiktokShopSellerShopDO> previous=shops.findByAccount(a.getTenantId(),a.getId());
        Map<String,TkTiktokShopSellerShopDO> byId=new HashMap<>();if(previous!=null)for(TkTiktokShopSellerShopDO old:previous)byId.put(old.getShopId(),old);
        shops.invalidate(a.getTenantId(),a.getId());
        for(TkTiktokShopSellerApiClient.AuthorizedShop remote:authorized){
            TkTiktokShopSellerShopDO shop=byId.get(remote.getId());boolean newShop=shop==null;if(newShop)shop=new TkTiktokShopSellerShopDO();
            if(!newShop && (!Objects.equals(shop.getTenantId(),a.getTenantId()) || !Objects.equals(shop.getAccountId(),a.getId()) || !Objects.equals(shop.getCompanyId(),a.getCompanyId())))throw denied();
            shop.setTenantId(a.getTenantId());shop.setCompanyId(a.getCompanyId());shop.setAccountId(a.getId());shop.setCreator(a.getCreator());shop.setUpdater(a.getCreator());shop.setEnvironment(env);
            shop.setShopId(remote.getId());shop.setName(remote.getName());shop.setRegion(remote.getRegion());shop.setShopCipher(remote.getCipher());shop.setSellerType(remote.getSellerType());shop.setCode(remote.getCode());shop.setStatus("AUTHORIZED");
            if(newShop)shops.insert(shop);else shops.updateById(shop);
        }
    }
    public List<SellerAccountResponse> listAccounts(Long requestedCompany) {
        TkUserScope current=current();Long company=company(current,requestedCompany);
        LambdaQueryWrapperX<TkTiktokShopSellerAccountDO> q=new LambdaQueryWrapperX<TkTiktokShopSellerAccountDO>().eq(TkTiktokShopSellerAccountDO::getTenantId,current.getTenantId()).eq(TkTiktokShopSellerAccountDO::getAppKey,properties.getSeller().getAppKey()).eq(TkTiktokShopSellerAccountDO::getEnvironment,properties.getSeller().getEnvironment());
        if(company!=null)q.eq(TkTiktokShopSellerAccountDO::getCompanyId,company);
        if(!current.canReadAllTenantRecords())q.eq(TkTiktokShopSellerAccountDO::getCreator,current.getUserIdString());
        return accounts.selectList(q).stream().map(a->{validate(a,current);return new SellerAccountResponse(a);}).collect(Collectors.toList());
    }
    public List<SellerShopResponse> listShops(Long requestedCompany) {
        List<SellerShopResponse> result=new ArrayList<>();
        for(SellerAccountResponse a:listAccounts(requestedCompany)){
            List<TkTiktokShopSellerShopDO> found=shops.findByAccount(TenantContextHolder.getTenantId(),a.getId());
            if(!found.isEmpty()){
                TkTiktokShopSellerAccountDO parent=requireReadable(a.getId());
                for(TkTiktokShopSellerShopDO shop:found){validateShop(shop,parent);result.add(new SellerShopResponse(shop));}
            }
        }
        return result;
    }
    public TkTiktokShopSellerAccountDO requireReadable(Long id){TkUserScope current=current();TkTiktokShopSellerAccountDO a=accounts.selectById(id);validate(a,current);return a;}
    public TkTiktokShopSellerShopDO requireReadableShop(Long id){
        TkTiktokShopSellerShopDO shop=shops.selectById(id);if(shop==null)throw denied();TkTiktokShopSellerAccountDO a=requireReadable(shop.getAccountId());validateShop(shop,a);
        if(!"AUTHORIZED".equals(shop.getStatus()) || !"AUTHORIZED".equals(a.getAuthStatus()) || shop.getShopCipher()==null)throw denied();return shop;
    }
    @Transactional
    public void disconnect(Long id){TkTiktokShopSellerAccountDO a=requireReadable(id);scope.validateWritable(a.getTenantId(),a.getCompanyId());accounts.disconnect(id,a.getTenantId());shops.invalidate(a.getTenantId(),id);}
    private void validateShop(TkTiktokShopSellerShopDO shop,TkTiktokShopSellerAccountDO a){if(!Objects.equals(shop.getTenantId(),a.getTenantId()) || !Objects.equals(shop.getCompanyId(),a.getCompanyId()) || !Objects.equals(shop.getAccountId(),a.getId()) || !Objects.equals(shop.getEnvironment(),a.getEnvironment()))throw denied();}
    private TkUserScope current(){TkUserScope current=scope.getCurrentScope();if(current==null || !current.hasTenantScope() || current.getUserId()==null || !Objects.equals(current.getTenantId(),TenantContextHolder.getTenantId()) || TenantContextHolder.isIgnore())throw denied();return current;}
    private Long company(TkUserScope current,Long requested){
        Long allowed=scope.getWritableCompanyId(requested);if(!current.isPlatformAdmin() && (allowed==null || (requested!=null && !Objects.equals(requested,allowed))))throw denied();
        if(requested!=null)scope.validateReadable(current.getTenantId(),requested,current.getUserIdString());return current.isPlatformAdmin()?requested:allowed;
    }
    private void validate(TkTiktokShopSellerAccountDO a,TkUserScope current){
        if(a==null || !Objects.equals(a.getTenantId(),current.getTenantId()) || !Objects.equals(a.getAppKey(),properties.getSeller().getAppKey()) || !Objects.equals(a.getEnvironment(),properties.getSeller().getEnvironment())
            || (!current.isPlatformAdmin() && !Objects.equals(a.getCompanyId(),scope.getWritableCompanyId(null))) || (!current.canReadAllTenantRecords() && !Objects.equals(a.getCreator(),current.getUserIdString())))throw denied();scope.validateReadable(a.getTenantId(),a.getCompanyId(),a.getCreator());
    }
    private static IllegalArgumentException denied(){return new IllegalArgumentException("Seller account or shop unavailable or access denied");}
}
