package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXAuthSessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenXAuthSessionMapper extends BaseMapperX<TkOpenXAuthSessionDO> {
    default TkOpenXAuthSessionDO selectByAuthSessionId(String authSessionId) {
        return selectOne(TkOpenXAuthSessionDO::getAuthSessionId, authSessionId);
    }

    default TkOpenXAuthSessionDO selectByClientAndSessionId(String clientId, String authSessionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXAuthSessionDO>()
                .eq(TkOpenXAuthSessionDO::getClientId, clientId)
                .eq(TkOpenXAuthSessionDO::getAuthSessionId, authSessionId));
    }

    default TkOpenXAuthSessionDO selectByClientAndSessionIdForUpdate(String clientId, String authSessionId) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXAuthSessionDO>()
                .eq(TkOpenXAuthSessionDO::getClientId, clientId)
                .eq(TkOpenXAuthSessionDO::getAuthSessionId, authSessionId)
                .last("FOR UPDATE"));
    }

    default TkOpenXAuthSessionDO selectByOauthStateHash(String oauthStateHash) {
        return selectOne(TkOpenXAuthSessionDO::getOauthStateHash, oauthStateHash);
    }

    default TkOpenXAuthSessionDO selectByOauthStateHashForUpdate(String oauthStateHash) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXAuthSessionDO>()
                .eq(TkOpenXAuthSessionDO::getOauthStateHash, oauthStateHash)
                .last("FOR UPDATE"));
    }
}