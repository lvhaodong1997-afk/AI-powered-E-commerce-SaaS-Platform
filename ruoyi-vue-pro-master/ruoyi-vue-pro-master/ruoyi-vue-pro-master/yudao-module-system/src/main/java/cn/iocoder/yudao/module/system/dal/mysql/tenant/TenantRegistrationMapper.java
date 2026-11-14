package cn.iocoder.yudao.module.system.dal.mysql.tenant;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantRegistrationDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TenantRegistrationMapper extends BaseMapperX<TenantRegistrationDO> {
    default TenantRegistrationDO selectByRequestId(String requestId) { return selectOne(TenantRegistrationDO::getRequestId, requestId); }
    default TenantRegistrationDO selectByMobile(String mobile) { return selectOne(TenantRegistrationDO::getMobile, mobile); }
}
