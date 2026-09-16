package cn.iocoder.yudao.module.tk.dal.mysql.social;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkSocialAccountMapper extends BaseMapperX<TkSocialAccountDO> {
    default TkSocialAccountDO findExternal(Long tenant,String platform,String externalId) {
        return selectOne(new LambdaQueryWrapperX<TkSocialAccountDO>()
                .eq(TkSocialAccountDO::getTenantId,tenant).eq(TkSocialAccountDO::getPlatform,platform)
                .eq(TkSocialAccountDO::getExternalAccountId,externalId));
    }
    default PageResult<TkSocialAccountDO> page(PageParam page,String platform,TkUserScope scope) {
        return selectPage(page,new LambdaQueryWrapperX<TkSocialAccountDO>()
                .eqIfPresent(TkSocialAccountDO::getTenantId,scope.isGlobalPlatformView()?null:scope.getTenantId())
                .eqIfPresent(TkSocialAccountDO::getCreator,scope.canReadAllTenantRecords()?null:scope.getUserIdString())
                .eqIfPresent(TkSocialAccountDO::getPlatform,platform)
                .ne(TkSocialAccountDO::getStatus,"DELETED")
                .orderByDesc(TkSocialAccountDO::getId));
    }

    @Update("UPDATE tk_social_account SET access_token_ciphertext=#{replacement}, token_expires_at=#{expires}, "
            + "last_validated_at=#{now}, last_auth_time=CASE WHEN platform='INSTAGRAM' THEN #{now} ELSE last_auth_time END, "
            + "update_time=#{now} WHERE id=#{id} AND tenant_id=#{tenant} "
            + "AND access_token_ciphertext=#{previous} AND status='AUTHORIZED' AND deleted=0")
    int replaceToken(@Param("id") Long id,@Param("tenant") Long tenant,@Param("previous") String previous,
                     @Param("replacement") String replacement,@Param("expires") LocalDateTime expires,@Param("now") LocalDateTime now);

    @Update("UPDATE tk_social_account SET status='REAUTH_REQUIRED',fail_reason=#{reason},update_time=#{now} "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND access_token_ciphertext=#{previous} AND status='AUTHORIZED' AND deleted=0")
    int markReauth(@Param("id") Long id,@Param("tenant") Long tenant,@Param("previous") String previous,
                   @Param("reason") String reason,@Param("now") LocalDateTime now);

    @Update("UPDATE tk_social_account SET last_validated_at=#{now},update_time=#{now} "
            + "WHERE id=#{id} AND tenant_id=#{tenant} AND access_token_ciphertext=#{previous} AND status='AUTHORIZED' AND deleted=0")
    int markValidated(@Param("id") Long id,@Param("tenant") Long tenant,@Param("previous") String previous,@Param("now") LocalDateTime now);

    @Select("SELECT * FROM tk_social_account WHERE platform='INSTAGRAM' AND status='AUTHORIZED' AND deleted=0 "
            + "AND token_expires_at > #{now} AND token_expires_at < #{until} ORDER BY token_expires_at LIMIT 100")
    @com.baomidou.mybatisplus.annotation.InterceptorIgnore(tenantLine="true")
    List<TkSocialAccountDO> refreshCandidates(@Param("now") LocalDateTime now,@Param("until") LocalDateTime until);
}
