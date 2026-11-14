package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskMediaDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TkOpenTiktokPublishTaskMediaMapper extends BaseMapperX<TkOpenTiktokPublishTaskMediaDO> {
    default List<TkOpenTiktokPublishTaskMediaDO> selectByClientAndTaskId(String clientId, String taskId) {
        return selectList(new LambdaQueryWrapperX<TkOpenTiktokPublishTaskMediaDO>()
                .eq(TkOpenTiktokPublishTaskMediaDO::getClientId, clientId)
                .eq(TkOpenTiktokPublishTaskMediaDO::getTaskId, taskId)
                .orderByAsc(TkOpenTiktokPublishTaskMediaDO::getSortIndex));
    }
}
