package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
public class TkTiktokShopSellerCatalogService {
    private final TkTiktokShopSellerAuthService sellerAuth;
    private final TkTiktokShopSellerTokenService tokens;
    private final TkTiktokShopSellerApiClient api;
    public TkTiktokShopSellerCatalogService(TkTiktokShopSellerAuthService sellerAuth,TkTiktokShopSellerTokenService tokens,TkTiktokShopSellerApiClient api) {
        this.sellerAuth=sellerAuth;this.tokens=tokens;this.api=api;
    }
    public SellerProductPage listProducts(Long shopId,Long companyId,Integer pageSize,String pageToken) {
        Prepared prepared=prepare(shopId,companyId,pageSize,pageToken,TkTiktokShopSellerApiClient.PRODUCT_SCOPE,false);
        try {
            TkTiktokShopSellerApiClient.CatalogPage<TkTiktokShopSellerApiClient.ProductSummary> page=api.searchProducts(prepared.token.getValue(),prepared.cipher,prepared.pageSize,prepared.pageToken);
            return new SellerProductPage(page);
        }catch(TkTiktokShopApiException e){throw catalogError(prepared,e);}
    }
    public SellerOrderPage listOrders(Long shopId,Long companyId,Integer pageSize,String pageToken) {
        Prepared prepared=prepare(shopId,companyId,pageSize,pageToken,TkTiktokShopSellerApiClient.ORDER_SCOPE,true);
        try {
            TkTiktokShopSellerApiClient.CatalogPage<TkTiktokShopSellerApiClient.OrderSummary> page=api.searchOrders(prepared.token.getValue(),prepared.cipher,prepared.pageSize,prepared.pageToken);
            return new SellerOrderPage(page);
        }catch(TkTiktokShopApiException e){throw catalogError(prepared,e);}
    }
    private TkTiktokShopApiException catalogError(Prepared prepared,TkTiktokShopApiException error) {
        if(error.isPermissionDenied()) {
            return new TkTiktokShopApiException(TkTiktokShopApiException.SELLER_FEATURE_ACCESS_DENIED,error.getRequestId());
        }
        tokens.reportRejectedToken(prepared.tenant,prepared.company,prepared.accountId,prepared.token.getVersion(),error);
        return error;
    }
    private Prepared prepare(Long shopId,Long companyId,Integer pageSize,String pageToken,String requiredScope,boolean orders) {
        TkTiktokShopSellerShopDO shop=sellerAuth.requireReadableShop(shopId);
        if(companyId!=null && !Objects.equals(companyId,shop.getCompanyId()))throw new IllegalArgumentException("Seller account or shop unavailable or access denied");
        TkTiktokShopSellerAccountDO account=sellerAuth.requireReadable(shop.getAccountId());
        if(orders){if(!TkTiktokShopSellerApiClient.hasOrderScope(account.getGrantedScopes()))throw new TkTiktokShopApiException("INSUFFICIENT_SCOPE",null);}
        else if(!TkTiktokShopSellerApiClient.hasGrantedScope(account.getGrantedScopes(),requiredScope))throw new TkTiktokShopApiException("INSUFFICIENT_SCOPE",null);
        int size=TkTiktokShopSellerApiClient.normalizePageSize(pageSize);String cursor=TkTiktokShopSellerApiClient.normalizePageToken(pageToken);
        TkTiktokShopSellerTokenService.AccessToken token=tokens.getAccessToken(shop.getTenantId(),shop.getCompanyId(),shop.getAccountId());
        return new Prepared(shop.getTenantId(),shop.getCompanyId(),shop.getAccountId(),shop.getShopCipher(),size,cursor,token);
    }
    private static final class Prepared {
        private final Long tenant,company,accountId;private final String cipher;private final int pageSize;private final String pageToken;private final TkTiktokShopSellerTokenService.AccessToken token;
        private Prepared(Long tenant,Long company,Long accountId,String cipher,int pageSize,String pageToken,TkTiktokShopSellerTokenService.AccessToken token){this.tenant=tenant;this.company=company;this.accountId=accountId;this.cipher=cipher;this.pageSize=pageSize;this.pageToken=pageToken;this.token=token;}
    }
}
