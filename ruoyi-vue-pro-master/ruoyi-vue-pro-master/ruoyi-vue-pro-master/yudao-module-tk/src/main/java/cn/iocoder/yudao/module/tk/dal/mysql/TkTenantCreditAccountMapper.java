package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTenantCreditAccountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface TkTenantCreditAccountMapper extends BaseMapperX<TkTenantCreditAccountDO> {

    default TkTenantCreditAccountDO selectByTenantId(Long tenantId) {
        return selectOne(new LambdaQueryWrapperX<TkTenantCreditAccountDO>()
                .eq(TkTenantCreditAccountDO::getTenantId, tenantId));
    }

    /** Current read: ignore SESSION snapshots across JDBC ledger mutations and repeatable-read waits. */
    @Select("SELECT * FROM tk_tenant_credit_account WHERE tenant_id=#{tenantId} AND deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    TkTenantCreditAccountDO selectCurrentByTenantId(@Param("tenantId") Long tenantId);

    @Update("UPDATE tk_tenant_credit_account SET remaining_credits = remaining_credits - #{credits}, "
            + "frozen_credits = frozen_credits + #{credits}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND remaining_credits >= #{credits}")
    int freezeCredits(@Param("tenantId") Long tenantId, @Param("credits") Long credits);

    @Update("UPDATE tk_tenant_credit_account SET frozen_credits = frozen_credits - #{credits}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND frozen_credits >= #{credits}")
    int settleFrozenCredits(@Param("tenantId") Long tenantId, @Param("credits") Long credits);

    @Update("UPDATE tk_tenant_credit_account SET remaining_credits = remaining_credits + #{credits}, "
            + "frozen_credits = frozen_credits - #{credits}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND frozen_credits >= #{credits}")
    int refundFrozenCredits(@Param("tenantId") Long tenantId, @Param("credits") Long credits);

    @Update("UPDATE tk_tenant_credit_account SET remaining_credits = remaining_credits + #{restored}, "
            + "frozen_credits = frozen_credits - #{credits}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND frozen_credits >= #{credits}")
    int releaseFrozenCredits(@Param("tenantId") Long tenantId, @Param("credits") Long credits,
                             @Param("restored") Long restored);

    @Update("UPDATE tk_tenant_credit_account SET total_credits = total_credits + #{credits}, "
            + "remaining_credits = remaining_credits + #{credits}, update_time = NOW() "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND total_credits <= #{maxTotal} - #{credits}")
    int rechargeCredits(@Param("tenantId") Long tenantId, @Param("credits") Long credits,
                        @Param("maxTotal") Long maxTotal);

}
