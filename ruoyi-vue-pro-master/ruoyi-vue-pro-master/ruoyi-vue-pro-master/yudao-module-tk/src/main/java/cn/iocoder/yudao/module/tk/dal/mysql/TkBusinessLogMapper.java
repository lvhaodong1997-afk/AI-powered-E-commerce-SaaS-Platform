package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.log.vo.TkBusinessLogPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface TkBusinessLogMapper extends BaseMapperX<TkBusinessLogDO> {

    default int deleteExpired(LocalDateTime deadline, int limit) {
        return delete(new LambdaQueryWrapperX<TkBusinessLogDO>()
                .lt(TkBusinessLogDO::getCreateTime, deadline)
                .last("LIMIT " + Math.max(1, limit)));
    }

    default PageResult<TkBusinessLogDO> selectPage(TkBusinessLogPageReqVO reqVO, TkUserScope scope) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TkBusinessLogDO>()
                .eqIfPresent(TkBusinessLogDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkBusinessLogDO::getOperatorId, scope.canReadAllTenantRecords() ? reqVO.getOperatorId() : scope.getUserId())
                .eqIfPresent(TkBusinessLogDO::getBusinessTraceId, reqVO.getBusinessTraceId())
                .eqIfPresent(TkBusinessLogDO::getBizType, reqVO.getBizType())
                .eqIfPresent(TkBusinessLogDO::getBizId, reqVO.getBizId())
                .eqIfPresent(TkBusinessLogDO::getLevel, reqVO.getLevel())
                .eqIfPresent(TkBusinessLogDO::getAction, reqVO.getAction())
                .eqIfPresent(TkBusinessLogDO::getStatus, reqVO.getStatus())
                .orderByDesc(TkBusinessLogDO::getId));
    }

}

