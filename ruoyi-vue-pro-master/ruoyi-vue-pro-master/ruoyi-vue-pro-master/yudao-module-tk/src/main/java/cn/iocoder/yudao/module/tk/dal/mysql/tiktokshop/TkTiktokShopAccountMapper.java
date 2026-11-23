package cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkTiktokShopAccountMapper extends BaseMapperX<TkTiktokShopAccountDO> {
    @Select("SELECT * FROM tk_tiktok_shop_account WHERE tenant_id=#{tenant} AND app_key=#{app} AND open_id=#{open} AND deleted=0")
    TkTiktokShopAccountDO findIdentity(@Param("tenant") Long tenant,@Param("app") String app,@Param("open") String open);

    @Update("UPDATE tk_tiktok_shop_account SET access_token_cipher=#{a.accessTokenCipher}, refresh_token_cipher=#{a.refreshTokenCipher}, "
            + "access_token_expire_time=#{a.accessTokenExpireTime}, refresh_token_expire_time=#{a.refreshTokenExpireTime}, "
            + "granted_scopes=#{a.grantedScopes}, token_version=token_version+1, refresh_retry_after=NULL "
            + "WHERE id=#{a.id} AND tenant_id=#{a.tenantId} AND company_id=#{a.companyId} AND app_key=#{a.appKey} "
            + "AND open_id=#{a.openId} AND token_version=#{version} AND auth_status='AUTHORIZED' AND deleted=0")
    int replaceTokens(@Param("a") TkTiktokShopAccountDO account,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_account SET access_token_cipher=#{a.accessTokenCipher}, refresh_token_cipher=#{a.refreshTokenCipher}, "
            + "access_token_expire_time=#{a.accessTokenExpireTime}, refresh_token_expire_time=#{a.refreshTokenExpireTime}, "
            + "granted_scopes=#{a.grantedScopes}, username=#{a.username}, display_name=#{a.displayName}, market=#{a.market}, "
            + "user_type=1, auth_status='AUTHORIZED', token_version=token_version+1, authorization_version=authorization_version+1, refresh_retry_after=NULL "
            + "WHERE id=#{a.id} AND tenant_id=#{a.tenantId} AND company_id=#{a.companyId} AND creator=#{a.creator} "
            + "AND app_key=#{a.appKey} AND open_id=#{a.openId} AND token_version=#{version} AND deleted=0")
    int reauthorize(@Param("a") TkTiktokShopAccountDO account,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_account SET auth_status='REAUTH_REQUIRED', access_token_cipher=NULL, refresh_token_cipher=NULL, "
            + "token_version=token_version+1, authorization_version=authorization_version+1, refresh_retry_after=NULL "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND token_version=#{version} "
            + "AND auth_status='AUTHORIZED' AND deleted=0")
    int markReauthorization(@Param("id") Long id,@Param("tenant") Long tenant,@Param("version") long version);

    @Update("UPDATE tk_tiktok_shop_account SET auth_status='DISCONNECTED', access_token_cipher=NULL, refresh_token_cipher=NULL, "
            + "access_token_expire_time=NULL, refresh_token_expire_time=NULL, token_version=token_version+1, "
            + "authorization_version=authorization_version+1, refresh_retry_after=NULL "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND deleted=0")
    int disconnect(@Param("id") Long id,@Param("tenant") Long tenant);

    @Update("UPDATE tk_tiktok_shop_account SET access_token_expire_time=#{now} "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND token_version=#{version} AND auth_status='AUTHORIZED' AND deleted=0 "
            + "AND (access_token_expire_time IS NULL OR access_token_expire_time > #{now})")
    int expireAccess(@Param("id") Long id,@Param("tenant") Long tenant,@Param("version") long version,@Param("now") LocalDateTime now);

    @Update("UPDATE tk_tiktok_shop_account SET refresh_retry_after=#{after} "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND token_version=#{version} AND auth_status='AUTHORIZED' AND deleted=0")
    int deferRefresh(@Param("id") Long id,@Param("tenant") Long tenant,@Param("version") long version,@Param("after") LocalDateTime after);

    @Select("SELECT id, tenant_id, company_id FROM tk_tiktok_shop_account WHERE app_key=#{app} AND user_type=1 "
            + "AND auth_status='AUTHORIZED' AND deleted=0 AND (access_token_expire_time IS NULL OR access_token_expire_time <= #{due}) "
            + "AND (refresh_retry_after IS NULL OR refresh_retry_after <= #{now}) ORDER BY access_token_expire_time, id LIMIT 20")
    List<TkTiktokShopAccountDO> dueForRefresh(@Param("app") String app,@Param("now") LocalDateTime now,@Param("due") LocalDateTime due);
}
