package cn.iocoder.yudao.module.tk.service.log;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkBusinessLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TkBusinessLogServiceImpl implements TkBusinessLogService {

    @Resource
    private TkBusinessLogMapper businessLogMapper;

    @Override
    public void info(String bizType, Long bizId, String action, String status, String message, Object detail) {
        info(null, bizType, bizId, action, status, message, detail);
    }

    @Override
    public void info(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail) {
        write(businessTraceId, LEVEL_INFO, bizType, bizId, action, status, message, detail);
    }

    @Override
    public void warn(String bizType, Long bizId, String action, String status, String message, Object detail) {
        warn(null, bizType, bizId, action, status, message, detail);
    }

    @Override
    public void warn(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail) {
        write(businessTraceId, LEVEL_WARN, bizType, bizId, action, status, message, detail);
    }

    @Override
    public void error(String bizType, Long bizId, String action, String status, String message, Object detail) {
        error(null, bizType, bizId, action, status, message, detail);
    }

    @Override
    public void error(String businessTraceId, String bizType, Long bizId, String action, String status, String message, Object detail) {
        write(businessTraceId, LEVEL_ERROR, bizType, bizId, action, status, message, detail);
    }

    private void write(String businessTraceId, String level, String bizType, Long bizId, String action, String status, String message, Object detail) {
        try {
            TkBusinessLogDO businessLog = TkBusinessLogDO.builder()
                    .businessTraceId(StrUtil.maxLength(businessTraceId, 64))
                    .bizType(StrUtil.maxLength(bizType, 64))
                    .bizId(bizId)
                    .level(level)
                    .action(StrUtil.maxLength(action, 64))
                    .status(StrUtil.maxLength(status, 32))
                    .message(StrUtil.maxLength(message, 512))
                    .detailJson(detail == null ? null : JsonUtils.toJsonString(detail))
                    .operatorId(getOperatorId())
                    .build();
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                businessLog.setTenantId(tenantId);
            }
            businessLogMapper.insert(businessLog);
        } catch (Exception ex) {
            log.warn("[write][bizType({}) bizId({}) action({}) 业务日志写入失败]", bizType, bizId, action, ex);
        }
    }

    private Long getOperatorId() {
        try {
            LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
            return loginUser == null ? null : loginUser.getId();
        } catch (Exception ignored) {
            return null;
        }
    }

}
