package cn.iocoder.yudao.module.system.dal.mysql.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.role.RolePageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapperX<RoleDO> {

    default PageResult<RoleDO> selectPage(RolePageReqVO reqVO) {
        return selectPageByTenant(reqVO, null);
    }

    default PageResult<RoleDO> selectPageByTenant(RolePageReqVO reqVO, Long tenantId) {
        LambdaQueryWrapperX<RoleDO> query = new LambdaQueryWrapperX<RoleDO>()
                .likeIfPresent(RoleDO::getName, reqVO.getName())
                .likeIfPresent(RoleDO::getCode, reqVO.getCode())
                .eqIfPresent(RoleDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RoleDO::getTenantId, tenantId)
                .betweenIfPresent(BaseDO::getCreateTime, reqVO.getCreateTime());
        if (Boolean.FALSE.equals(reqVO.getIncludeTenantAdmin())) {
            query.ne(RoleDO::getCode, RoleCodeEnum.TENANT_ADMIN.getCode());
        }
        query.orderByAsc(RoleDO::getSort);
        return selectPage(reqVO, query);
    }

    default RoleDO selectByName(String name) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return selectOne(RoleDO::getName, name);
        }
        return selectOne(RoleDO::getName, name, RoleDO::getTenantId, tenantId);
    }

    default RoleDO selectByCode(String code) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return selectOne(RoleDO::getCode, code);
        }
        return selectOne(RoleDO::getCode, code, RoleDO::getTenantId, tenantId);
    }

    default RoleDO selectByCodeAndTenantId(String code, Long tenantId) {
        return selectOne(RoleDO::getCode, code, RoleDO::getTenantId, tenantId);
    }

    default List<RoleDO> selectListByStatus(@Nullable Collection<Integer> statuses) {
        return selectListByStatus(statuses, null);
    }

    default List<RoleDO> selectListByStatus(@Nullable Collection<Integer> statuses, Long tenantId) {
        return selectList(new LambdaQueryWrapperX<RoleDO>()
                .inIfPresent(RoleDO::getStatus, statuses)
                .eqIfPresent(RoleDO::getTenantId, tenantId));
    }

}
