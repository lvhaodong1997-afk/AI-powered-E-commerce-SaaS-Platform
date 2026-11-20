package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXReturnTicketDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkOpenXReturnTicketMapper extends BaseMapperX<TkOpenXReturnTicketDO> {
    default TkOpenXReturnTicketDO selectByTicketHashForUpdate(String ticketHash) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXReturnTicketDO>()
                .eq(TkOpenXReturnTicketDO::getTicketHash, ticketHash)
                .last("FOR UPDATE"));
    }

    default java.util.List<TkOpenXReturnTicketDO> selectListBySessionForUpdate(String clientId, String authSessionId, String purpose) {
        return selectList(new LambdaQueryWrapperX<TkOpenXReturnTicketDO>()
                .eq(TkOpenXReturnTicketDO::getClientId, clientId)
                .eq(TkOpenXReturnTicketDO::getAuthSessionId, authSessionId)
                .eq(TkOpenXReturnTicketDO::getPurpose, purpose)
                .last("FOR UPDATE"));
    }

    default TkOpenXReturnTicketDO selectIssuedBySession(String clientId, String authSessionId, String purpose) {
        return selectOne(new LambdaQueryWrapperX<TkOpenXReturnTicketDO>()
                .eq(TkOpenXReturnTicketDO::getClientId, clientId)
                .eq(TkOpenXReturnTicketDO::getAuthSessionId, authSessionId)
                .eq(TkOpenXReturnTicketDO::getPurpose, purpose)
                .eq(TkOpenXReturnTicketDO::getStatus, "ISSUED")
                .orderByDesc(TkOpenXReturnTicketDO::getId)
                .last("LIMIT 1"));
    }
}