package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAuthSessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkTiktokAuthSessionMapper extends BaseMapperX<TkTiktokAuthSessionDO> {

    default TkTiktokAuthSessionDO selectByState(String state) {
        return selectOne(TkTiktokAuthSessionDO::getState, state);
    }

    default TkTiktokAuthSessionDO selectByClientTicket(String clientTicket) {
        return selectOne(TkTiktokAuthSessionDO::getClientTicket, clientTicket);
    }

}
