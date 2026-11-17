package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopSellerAccountDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TkTiktokShopSellerAccountMapper extends BaseMapperX<TkTiktokShopSellerAccountDO> {
    @Select("SELECT * FROM tk_tiktok_shop_seller_account WHERE tenant_id=#{tenant} AND app_key=#{app} AND environment=#{environment} AND open_id=#{open} AND deleted=0")
    TkTiktokShopSellerAccountDO findIdentity(@Param("tenant") Long tenant,@Param("app") String app,@Param("environment") String environment,@Param("open") String open);

    @Update("UPDATE tk_tiktok_shop_seller_account SET access_token_cipher=#{a.accessTokenCipher}, refresh_token_cipher=#{a.refreshTokenCipher}, "
            + "access_token_expire_time=#{a.accessTokenExpireTime}, refresh_token_expire_time=#{a.refreshTokenExpireTime}, "
            + "granted_scopes=#{a.grantedScopes}, token_version=token_version+1 "
            + "WHERE id=#{a.id} AND tenant_id=#{a.tenantId} AND company_id=#{a.companyId} AND app_key=#{a.appKey} "
            + "AND environment=#{a.environment} AND open_id=#{a.openId} AND token_version=#{version} AND auth_status='AUTHORIZED' AND deleted=0")
    int replaceTokens(@Param("a") TkTiktokShopSellerAccountDO account,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_seller_account SET access_token_cipher=#{a.accessTokenCipher}, refresh_token_cipher=#{a.refreshTokenCipher}, "
            + "access_token_expire_time=#{a.accessTokenExpireTime}, refresh_token_expire_time=#{a.refreshTokenExpireTime}, "
            + "granted_scopes=#{a.grantedScopes}, username=#{a.username}, display_name=#{a.displayName}, market=#{a.market}, "
            + "user_type=0, auth_status='AUTHORIZED', token_version=token_version+1 "
            + "WHERE id=#{a.id} AND tenant_id=#{a.tenantId} AND company_id=#{a.companyId} AND creator=#{a.creator} "
            + "AND app_key=#{a.appKey} AND environment=#{a.environment} AND open_id=#{a.openId} AND token_version=#{version} AND deleted=0")
    int reauthorize(@Param("a") TkTiktokShopSellerAccountDO account,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_seller_account SET auth_status='REAUTH_REQUIRED', access_token_cipher=NULL, refresh_token_cipher=NULL, "
            + "token_version=token_version+1 WHERE id=#{id} AND tenant_id=#{tenant} AND token_version=#{version} "
            + "AND auth_status='AUTHORIZED' AND deleted=0")
    int markReauthorization(@Param("id") Long id,@Param("tenant") Long tenant,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_seller_account SET auth_status='DISCONNECTED', access_token_cipher=NULL, refresh_token_cipher=NULL, "
            + "access_token_expire_time=NULL, refresh_token_expire_time=NULL, token_version=token_version+1 "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND deleted=0")
    int disconnect(@Param("id") Long id,@Param("tenant") Long tenant);
}
