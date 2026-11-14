package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantRegistrationDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.model.common.ResponseModel;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import com.anji.captcha.service.CaptchaService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.Arrays;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class TenantRegistrationService {
    private static final DefaultRedisScript<Long> RATE_LIMIT = new DefaultRedisScript<>(
            "local m=tonumber(redis.call('GET',KEYS[1]) or '0'); local i=tonumber(redis.call('GET',KEYS[2]) or '0'); "
            + "if m>=5 or i>=30 then return 0 end; "
            + "if redis.call('INCR',KEYS[1])==1 then redis.call('EXPIRE',KEYS[1],3600) end; "
            + "if redis.call('INCR',KEYS[2])==1 then redis.call('EXPIRE',KEYS[2],3600) end; return 1", Long.class);
    @Resource private TenantRegistrationProperties properties;
    @Resource private TenantRegistrationProvisioner provisioner;
    @Resource private PhoneVerificationClient phoneClient;
    @Resource private CaptchaService captchaService;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private OAuth2TokenService tokenService;
    @Resource private TenantService tenantService;
    @Resource private LoginLogService loginLogService;
    @Resource private AdminAuthService authService;

    public TenantRegistrationConfigRespVO config() {
        return new TenantRegistrationConfigRespVO().setEnabled(properties.tenantAvailable()).setSmsAvailable(properties.smsAvailable());
    }

    public TenantRegistrationSmsRespVO send(TenantRegistrationSmsReqVO req, String ip) {
        requireAvailable();
        verifyCaptcha(req);
        Long allowed = stringRedisTemplate.execute(RATE_LIMIT, Arrays.asList("tk:registration:sms:mobile:" + req.getMobile(),
                "tk:registration:sms:ip:" + PhoneVerificationClient.hmac(properties.getPhoneSecret(), ip)));
        if (!Long.valueOf(1).equals(allowed)) { throw exception0(429, "短信发送过于频繁，请稍后重试"); }
        return phoneClient.send(req.getMobile(), req.getRequestId(), ip);
    }

    public AuthLoginRespVO register(TenantRegistrationReqVO req, String ip) {
        requireAvailable();
        // The public flow always provisions a fresh independent tenant, never the logged-in caller's tenant.
        if (SecurityFrameworkUtils.getLoginUser() != null) { throw exception0(400, "请退出当前账号后注册新租户"); }
        verifyCaptcha(req);
        TenantRegistrationDO registration;
        try { registration = provisioner.provision(req); }
        catch (DuplicateKeyException concurrentRequest) {
            // The failed dynamic-datasource transaction has ended; read the concurrent committed result afresh.
            registration = provisioner.recover(req);
            if (registration == null) { throw exception0(400, "手机号或注册请求已被使用，请直接登录或重试"); }
        }
        tenantService.validTenant(registration.getTenantId());
        TenantDO tenant = tenantService.getTenant(registration.getTenantId());
        final TenantRegistrationDO owner = registration;
        return TenantUtils.execute(owner.getTenantId(), () -> {
            // A committed request is recoverable, but cannot bypass later password changes or account disabling.
            AdminUserDO authenticated = authService.authenticate(req.getUsername(), req.getPassword());
            if (!owner.getUserId().equals(authenticated.getId()) || !owner.getTenantId().equals(authenticated.getTenantId())) {
                throw exception0(403, "注册用户归属不匹配");
            }
            OAuth2AccessTokenDO token = tokenService.createAccessToken(owner.getUserId(), UserTypeEnum.ADMIN.getValue(),
                    OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
            loginLogService.createLoginLog(new LoginLogCreateReqDTO().setLogType(LoginLogTypeEnum.LOGIN_USERNAME.getType())
                    .setTraceId(TracerUtils.getTraceId()).setUserId(owner.getUserId()).setUserType(UserTypeEnum.ADMIN.getValue())
                    .setUsername(req.getUsername()).setResult(LoginResultEnum.SUCCESS.getResult())
                    .setUserIp(ip).setUserAgent(ServletUtils.getUserAgent()));
            return BeanUtils.toBean(token, AuthLoginRespVO.class).setTenantId(owner.getTenantId()).setTenantName(tenant.getName());
        });
    }

    private void requireAvailable() {
        if (!properties.tenantAvailable() || !properties.smsAvailable()) { throw exception0(400, "自助注册暂未开放"); }
    }

    private void verifyCaptcha(CaptchaVerificationReqVO req) {
        if (req.getCaptchaVerification() == null || req.getCaptchaVerification().trim().isEmpty()) {
            throw exception0(400, "请先完成图形验证");
        }
        CaptchaVO challenge = new CaptchaVO(); challenge.setCaptchaVerification(req.getCaptchaVerification());
        ResponseModel response = captchaService.verification(challenge);
        if (response == null || !response.isSuccess()) { throw exception0(400, "图形验证失败，请重新验证"); }
    }

    public String clientIp(HttpServletRequest req) {
        String address = req.getRemoteAddr();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded == null || !properties.getTrustedProxyIps().contains(address)) { return address; }
        String[] hops = forwarded.split(",");
        if (hops.length > 16) { return address; }
        for (int i = hops.length - 1; i >= 0 && properties.getTrustedProxyIps().contains(address); i--) {
            String candidate = hops[i].trim();
            // Numeric addresses only: never resolve attacker-controlled DNS names.
            if (!(candidate.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") || candidate.matches("[a-fA-F0-9]*:[a-fA-F0-9:]+"))) { break; }
            try { InetAddress.getByName(candidate); } catch (Exception invalidAddress) { break; }
            address = candidate;
        }
        return address;
    }
}
