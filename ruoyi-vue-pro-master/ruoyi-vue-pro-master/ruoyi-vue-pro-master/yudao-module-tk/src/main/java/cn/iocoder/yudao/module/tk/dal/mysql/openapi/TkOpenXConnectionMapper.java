package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkOpenXConnectionMapper extends BaseMapperX<TkOpenXConnectionDO> {
    default TkOpenXConnectionDO selectByClientAndConnectionId(String clientId, String connectionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getConnectionId, connectionId));
    }

    default TkOpenXConnectionDO selectByClientAndConnectionIdForUpdate(String clientId, String connectionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getConnectionId, connectionId)
                .last("FOR UPDATE"));
    }

    default TkOpenXConnectionDO selectByClientAndExternalAccountId(String clientId, String externalAccountId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getExternalAccountId, externalAccountId)
                .orderByDesc(TkOpenXConnectionDO::getId)
                .last("LIMIT 1"));
    }

    default TkOpenXConnectionDO selectByClientAndExternalAccountIdForUpdate(String clientId, String externalAccountId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getExternalAccountId, externalAccountId)
                .orderByDesc(TkOpenXConnectionDO::getId)
                .last("LIMIT 1 FOR UPDATE"));
    }

    default List<TkOpenXConnectionDO> selectListByClient(String clientId) {
        return selectList(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .orderByDesc(TkOpenXConnectionDO::getId));
    }
}