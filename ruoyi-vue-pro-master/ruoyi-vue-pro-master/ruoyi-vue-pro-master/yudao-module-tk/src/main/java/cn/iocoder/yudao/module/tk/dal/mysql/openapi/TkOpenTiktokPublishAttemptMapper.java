package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishAttemptDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TkOpenTiktokPublishAttemptMapper extends BaseMapperX<TkOpenTiktokPublishAttemptDO> {
    default TkOpenTiktokPublishAttemptDO selectCurrent(String detailId, int attemptNo) {
        return selectOne(new LambdaQueryWrapperX<TkOpenTiktokPublishAttemptDO>()
                .eq(TkOpenTiktokPublishAttemptDO::getDetailId, detailId)
                .eq(TkOpenTiktokPublishAttemptDO::getAttemptNo, attemptNo));
    }
}
