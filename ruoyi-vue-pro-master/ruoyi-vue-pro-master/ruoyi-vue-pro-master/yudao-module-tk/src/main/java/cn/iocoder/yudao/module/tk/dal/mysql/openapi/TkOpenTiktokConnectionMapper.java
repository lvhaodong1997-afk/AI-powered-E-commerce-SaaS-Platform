package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TkOpenTiktokConnectionMapper extends BaseMapperX<TkOpenTiktokConnectionDO> {
    default TkOpenTiktokConnectionDO selectByClientAndConnectionId(String clientId, String connectionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokConnectionDO>()
                .eq(TkOpenTiktokConnectionDO::getClientId, clientId)
                .eq(TkOpenTiktokConnectionDO::getConnectionId, connectionId));
    }

    default TkOpenTiktokConnectionDO selectByClientAndExternalAccountId(String clientId, String externalAccountId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokConnectionDO>()
                .eq(TkOpenTiktokConnectionDO::getClientId, clientId)
                .eq(TkOpenTiktokConnectionDO::getExternalAccountId, externalAccountId));
    }

    default List<TkOpenTiktokConnectionDO> selectListByClient(String clientId, String externalAccountId,
                                                               String authStatus) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokConnectionDO>()
                .eq(TkOpenTiktokConnectionDO::getClientId, clientId)
                .eqIfPresent(TkOpenTiktokConnectionDO::getExternalAccountId, externalAccountId)
                .eqIfPresent(TkOpenTiktokConnectionDO::getAuthStatus, authStatus)
                .orderByDesc(TkOpenTiktokConnectionDO::getId));
    }

    default List<TkOpenTiktokConnectionDO> selectListByClientAndIds(String clientId, Collection<String> connectionIds) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokConnectionDO>()
                .eq(TkOpenTiktokConnectionDO::getClientId, clientId)
                .inIfPresent(TkOpenTiktokConnectionDO::getConnectionId, connectionIds));
    }
}
