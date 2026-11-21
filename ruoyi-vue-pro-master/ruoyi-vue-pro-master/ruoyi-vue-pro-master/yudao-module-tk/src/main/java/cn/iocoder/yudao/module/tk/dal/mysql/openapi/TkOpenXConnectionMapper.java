package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXConnectionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Comparator;
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
        // JSqlParser moves FOR UPDATE before ORDER BY/LIMIT, which MySQL rejects.
        return selectListByClientAndExternalAccountIdForUpdate(clientId, externalAccountId).stream()
                .max(Comparator.comparing(TkOpenXConnectionDO::getId))
                .orElse(null);
    }

    default List<TkOpenXConnectionDO> selectListByClientAndExternalAccountIdForUpdate(String clientId, String externalAccountId) {
        return selectList(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getExternalAccountId, externalAccountId)
                .last("FOR UPDATE"));
    }

    default TkOpenXConnectionDO selectBySourceSocialSessionId(String clientId, Long sourceSocialSessionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXConnectionDO>()
                .eq(TkOpenXConnectionDO::getClientId, clientId)
                .eq(TkOpenXConnectionDO::getSourceSocialSessionId, sourceSocialSessionId)
                .orderByDesc(TkOpenXConnectionDO::getId)
                .last("LIMIT 1"));
    }

    default List<TkOpenXConnectionDO> selectListByClientAndWorkspace(String clientId, String workspaceId) {
        LambdaQueryWrapperX<TkOpenXConnectionDO> query = new LambdaQueryWrapperX<>();
        query.eq(TkOpenXConnectionDO::getClientId, clientId);
        if (workspaceId == null) query.isNull(TkOpenXConnectionDO::getWorkspaceId);
        else query.eq(TkOpenXConnectionDO::getWorkspaceId, workspaceId);
        return selectList(query.orderByDesc(TkOpenXConnectionDO::getId));
    }
}
