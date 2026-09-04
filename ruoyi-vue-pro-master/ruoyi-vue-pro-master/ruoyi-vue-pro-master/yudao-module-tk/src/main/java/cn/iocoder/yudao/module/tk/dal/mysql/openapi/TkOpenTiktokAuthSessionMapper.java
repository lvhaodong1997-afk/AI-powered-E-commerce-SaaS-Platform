package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokAuthSessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenTiktokAuthSessionMapper extends BaseMapperX<TkOpenTiktokAuthSessionDO> {
    default TkOpenTiktokAuthSessionDO selectByClientAndSessionId(String clientId, String authSessionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokAuthSessionDO>()
                .eq(TkOpenTiktokAuthSessionDO::getClientId, clientId)
                .eq(TkOpenTiktokAuthSessionDO::getAuthSessionId, authSessionId));
    }

    default TkOpenTiktokAuthSessionDO selectByClientAndSessionIdForUpdate(String clientId, String authSessionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokAuthSessionDO>()
                .eq(TkOpenTiktokAuthSessionDO::getClientId, clientId)
                .eq(TkOpenTiktokAuthSessionDO::getAuthSessionId, authSessionId)
                .last("FOR UPDATE"));
    }

    default TkOpenTiktokAuthSessionDO selectByOauthState(String oauthState) {
        return selectOne(TkOpenTiktokAuthSessionDO::getOauthState, oauthState);
    }

    default TkOpenTiktokAuthSessionDO selectByOauthStateForUpdate(String oauthState) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokAuthSessionDO>()
                .eq(TkOpenTiktokAuthSessionDO::getOauthState, oauthState)
                .last("FOR UPDATE"));
    }

    default TkOpenTiktokAuthSessionDO selectByClientTicket(String clientTicket) {
        return selectOne(TkOpenTiktokAuthSessionDO::getClientTicket, clientTicket);
    }
}
