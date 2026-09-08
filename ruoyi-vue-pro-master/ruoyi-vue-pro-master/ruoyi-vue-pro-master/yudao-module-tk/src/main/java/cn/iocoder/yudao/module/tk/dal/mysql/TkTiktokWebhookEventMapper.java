package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokWebhookEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkTiktokWebhookEventMapper extends BaseMapperX<TkTiktokWebhookEventDO> {

    default TkTiktokWebhookEventDO selectByEventId(String eventId) {
        return selectOne(TkTiktokWebhookEventDO::getEventId, eventId);
    }

}
