package cn.iocoder.yudao.module.system.dal.mysql.tenant;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantReferralDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TenantReferralMapper extends BaseMapperX<TenantReferralDO> {
    default PageResult<TenantReferralDO> selectByInviter(Long tenantId, PageParam page) {
        return selectPage(page, new LambdaQueryWrapperX<TenantReferralDO>().eq(TenantReferralDO::getInviterTenantId, tenantId).orderByDesc(TenantReferralDO::getId));
    }
    default Long countByInviter(Long tenantId) { return selectCount(TenantReferralDO::getInviterTenantId, tenantId); }
}
