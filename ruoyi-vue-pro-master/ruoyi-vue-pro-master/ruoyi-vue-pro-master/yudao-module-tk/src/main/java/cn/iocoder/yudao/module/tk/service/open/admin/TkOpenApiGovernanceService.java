package cn.iocoder.yudao.module.tk.service.open.admin;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiGovernanceVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiRequestLogMapper;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class TkOpenApiGovernanceService {

    private final TkOpenApiEventMapper eventMapper;
    private final TkOpenApiRequestLogMapper requestLogMapper;
    private final TkOpenApiCallbackOperations callbackOperations;

    public TkOpenApiGovernanceService(TkOpenApiEventMapper eventMapper,
                                      TkOpenApiRequestLogMapper requestLogMapper,
                                      TkOpenApiCallbackOperations callbackOperations) {
        this.eventMapper = eventMapper;
        this.requestLogMapper = requestLogMapper;
        this.callbackOperations = callbackOperations;
    }

    public List<TkOpenApiGovernanceVO.UsageResp> getUsage(String clientId, LocalDate startDate, LocalDate endDate) {
        LocalDate resolvedEnd = endDate == null ? LocalDate.now() : endDate;
        LocalDate resolvedStart = startDate == null ? resolvedEnd.minusDays(6) : startDate;
        if (resolvedStart.isAfter(resolvedEnd) || resolvedStart.isBefore(resolvedEnd.minusDays(366))) {
            throw ServiceExceptionUtil.invalidParamException("统计时间范围必须在 367 天内且开始日期不能晚于结束日期");
        }
        return requestLogMapper.selectDailyUsage(clientId, resolvedStart, resolvedEnd);
    }

    public PageResult<TkOpenApiGovernanceVO.EventResp> getEventPage(TkOpenApiGovernanceVO.EventPageReq request) {
        PageResult<TkOpenApiEventDO> page = eventMapper.selectPage(request);
        List<TkOpenApiGovernanceVO.EventResp> result = new ArrayList<>();
        for (TkOpenApiEventDO event : page.getList()) result.add(toEventResp(event));
        return new PageResult<>(result, page.getTotal());
    }

    public TkOpenApiGovernanceVO.EventResp getEvent(String eventId) {
        return toEventResp(requireEvent(eventId));
    }

    public void replay(String eventId) {
        TkOpenApiEventDO event = requireEvent(eventId);
        if (StrUtil.isBlank(event.getCallbackUrl())) {
            throw ServiceExceptionUtil.invalidParamException("回调事件没有可用的回调地址，无法重放");
        }
        if (java.util.Arrays.asList("PENDING", "DELIVERING", "RETRYING").contains(event.getStatus())) {
            throw ServiceExceptionUtil.invalidParamException("回调事件正在投递中，不能重复重放");
        }
        callbackOperations.replay(eventId);
    }

    private TkOpenApiEventDO requireEvent(String eventId) {
        TkOpenApiEventDO event = eventMapper.selectByEventId(eventId);
        if (event == null) {
            throw new ServiceException(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "回调事件不存在");
        }
        return event;
    }

    private TkOpenApiGovernanceVO.EventResp toEventResp(TkOpenApiEventDO event) {
        TkOpenApiGovernanceVO.EventResp response = new TkOpenApiGovernanceVO.EventResp();
        response.setEventId(event.getEventId());
        response.setClientId(event.getClientId());
        response.setEventType(event.getEventType());
        response.setResourceType(event.getResourceType());
        response.setResourceId(event.getResourceId());
        response.setCallbackUrl(event.getCallbackUrl());
        response.setPayloadJson(event.getPayloadJson());
        response.setStatus(event.getStatus());
        response.setAttemptCount(event.getAttemptCount());
        response.setNextRetryTime(event.getNextRetryTime());
        response.setLastHttpStatus(event.getLastHttpStatus());
        response.setLastError(event.getLastError());
        response.setDeliveredTime(event.getDeliveredTime());
        response.setCreateTime(event.getCreateTime());
        response.setUpdateTime(event.getUpdateTime());
        return response;
    }
}
