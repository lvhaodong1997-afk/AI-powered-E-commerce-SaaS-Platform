package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiClientAdminVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkOpenApiClientMapper extends BaseMapperX<TkOpenApiClientDO> {
    default TkOpenApiClientDO selectByClientId(String clientId) {
        return selectOne(TkOpenApiClientDO::getClientId, clientId);
    }

    default List<TkOpenApiClientDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<TkOpenApiClientDO>().orderByDesc(TkOpenApiClientDO::getId));
    }

    default PageResult<TkOpenApiClientDO> selectPage(TkOpenApiClientAdminVO.PageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkOpenApiClientDO>()
                .eqIfPresent(TkOpenApiClientDO::getClientId, reqVO.getClientId())
                .likeIfPresent(TkOpenApiClientDO::getClientName, reqVO.getClientName())
                .eqIfPresent(TkOpenApiClientDO::getStatus, reqVO.getStatus())
                .orderByDesc(TkOpenApiClientDO::getId));
    }
}
