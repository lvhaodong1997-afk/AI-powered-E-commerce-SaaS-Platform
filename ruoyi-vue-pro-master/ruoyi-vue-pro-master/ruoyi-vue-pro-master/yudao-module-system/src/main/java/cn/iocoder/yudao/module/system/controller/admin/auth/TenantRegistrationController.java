package cn.iocoder.yudao.module.system.controller.admin.auth;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.service.tenant.TenantRegistrationService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Slf4j
@RequestMapping("/system/auth/tenant-register")
public class TenantRegistrationController {
    @Resource private TenantRegistrationService registrationService;

    @GetMapping("/config") @PermitAll @TenantIgnore
    public CommonResult<TenantRegistrationConfigRespVO> config() { return success(registrationService.config()); }

    @PostMapping("/sms-code") @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<TenantRegistrationSmsRespVO> send(@Valid @RequestBody TenantRegistrationSmsReqVO req, HttpServletRequest request) {
        return success(registrationService.send(req, registrationService.clientIp(request)));
    }

    @PostMapping @PermitAll @TenantIgnore
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<AuthLoginRespVO> register(@Valid @RequestBody TenantRegistrationReqVO req, HttpServletRequest request) {
        return success(registrationService.register(req, registrationService.clientIp(request)));
    }

    /** The shared error logger persists raw request bodies; keep registration secrets out of that path. */
    @ExceptionHandler(Exception.class)
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<?> registrationError(Exception error) {
        Throwable business = error instanceof ServiceException ? error : error.getCause();
        if (business instanceof ServiceException) {
            ServiceException service = (ServiceException) business;
            return CommonResult.error(service.getCode(), service.getMessage());
        }
        if (error instanceof MethodArgumentNotValidException || error instanceof HttpMessageNotReadableException
                || error instanceof javax.validation.ValidationException) {
            return CommonResult.error(400, "注册信息格式不正确，请检查后重试");
        }
        log.warn("[tenantRegistration][request failed: {}]", error.getClass().getSimpleName());
        return CommonResult.error(500, "注册暂时失败，请使用原注册请求重试");
    }
}
