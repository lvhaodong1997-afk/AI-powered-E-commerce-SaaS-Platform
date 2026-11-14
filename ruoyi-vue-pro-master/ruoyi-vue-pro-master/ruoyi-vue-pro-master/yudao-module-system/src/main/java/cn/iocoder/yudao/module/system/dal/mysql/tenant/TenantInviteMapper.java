package cn.iocoder.yudao.module.system.dal.mysql.tenant;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantInviteDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TenantInviteMapper extends BaseMapperX<TenantInviteDO> {
    default TenantInviteDO selectByCode(String code) { return selectOne(TenantInviteDO::getInviteCode, code); }
    default TenantInviteDO selectByTenantId(Long tenantId) { return selectOne(TenantInviteDO::getTenantId, tenantId); }
}
