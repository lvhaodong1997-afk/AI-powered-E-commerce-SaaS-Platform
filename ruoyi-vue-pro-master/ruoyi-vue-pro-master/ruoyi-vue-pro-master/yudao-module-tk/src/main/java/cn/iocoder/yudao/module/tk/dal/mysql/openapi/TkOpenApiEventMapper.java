package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiGovernanceVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TkOpenApiEventMapper extends BaseMapperX<TkOpenApiEventDO> {
    default TkOpenApiEventDO selectByEventId(String eventId) {
        return selectOne(TkOpenApiEventDO::getEventId, eventId);
    }

    default List<TkOpenApiEventDO> selectRetryable(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenApiEventDO>()
                .in(TkOpenApiEventDO::getStatus, java.util.Arrays.asList("PENDING", "RETRYING"))
                .and(wrapper -> wrapper.isNull(TkOpenApiEventDO::getNextRetryTime)
                        .or().le(TkOpenApiEventDO::getNextRetryTime, now))
                .orderByAsc(TkOpenApiEventDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default int recoverStaleDelivering(LocalDateTime now) {
        return update(null, Wrappers.lambdaUpdate(TkOpenApiEventDO.class)
                .eq(TkOpenApiEventDO::getStatus, "DELIVERING")
                .isNotNull(TkOpenApiEventDO::getNextRetryTime)
                .le(TkOpenApiEventDO::getNextRetryTime, now)
                .set(TkOpenApiEventDO::getStatus, "RETRYING")
                .set(TkOpenApiEventDO::getNextRetryTime, now)
                .set(TkOpenApiEventDO::getLastError, "callback delivery was interrupted and has been requeued"));
    }

    default List<TkOpenApiEventDO> selectListByClient(String clientId, int limit) {
        return selectList(new LambdaQueryWrapperX<TkOpenApiEventDO>()
                .eqIfPresent(TkOpenApiEventDO::getClientId, clientId)
                .orderByDesc(TkOpenApiEventDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }

    default PageResult<TkOpenApiEventDO> selectPage(TkOpenApiGovernanceVO.EventPageReq reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkOpenApiEventDO>()
                .eqIfPresent(TkOpenApiEventDO::getClientId, reqVO.getClientId())
                .eqIfPresent(TkOpenApiEventDO::getEventType, reqVO.getEventType())
                .eqIfPresent(TkOpenApiEventDO::getStatus, reqVO.getStatus())
                .geIfPresent(TkOpenApiEventDO::getCreateTime, reqVO.getCreateTimeStart())
                .ltIfPresent(TkOpenApiEventDO::getCreateTime, reqVO.getCreateTimeEnd())
                .orderByDesc(TkOpenApiEventDO::getId));
    }
}
