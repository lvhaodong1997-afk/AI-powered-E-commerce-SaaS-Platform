package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantRegistrationDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantRegistrationMapper;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Locale;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

/** Separate bean ensures that recovery and token creation happen after the provisioning transaction. */
@Service
@TenantIgnore
@DataPermission(enable = false)
public class TenantRegistrationProvisioner {
    @Resource private TenantRegistrationProperties properties;
    @Resource private TenantRegistrationMapper registrationMapper;
    @Resource private TenantService tenantService;
    @Resource private TenantInviteService inviteService;
    @Resource private PhoneVerificationClient phoneClient;

    @DSTransactional
    public TenantRegistrationDO provision(TenantRegistrationReqVO req) {
        TenantRegistrationDO prior = recover(req);
        if (prior != null) { return prior; }
        if (registrationMapper.selectByMobile(req.getMobile()) != null) { throw exception0(400, "该手机号已注册TK租户，请直接登录"); }
        Long inviterTenantId = inviteService.validateInvite(req.getInviteCode());
        // Both unique indexes reserve the request and phone before any remote side effect.
        TenantRegistrationDO registration = new TenantRegistrationDO().setRequestId(req.getRequestId())
                .setMobile(req.getMobile()).setPayloadHash(payloadHash(req));
        registrationMapper.insert(registration);
        phoneClient.consume(req);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = properties.getValidityYears() != null && properties.getValidityYears() > 0
                ? now.plusYears(properties.getValidityYears()) : now.plusDays(properties.getValidityDays());
        TenantSaveReqVO tenant = new TenantSaveReqVO().setName(req.getTenantName().trim()).setContactName(req.getNickname())
                .setContactMobile(req.getMobile()).setStatus(CommonStatusEnum.ENABLE.getStatus()).setPackageId(properties.getDefaultPackageId())
                .setExpireTime(expireTime).setAccountCount(0)
                .setMemberLimit(properties.getMemberLimit()).setUsername(req.getUsername()).setPassword(req.getPassword());
        Long tenantId = tenantService.createTenant(tenant);
        inviteService.createCode(tenantId);
        if (inviterTenantId != null) { inviteService.bind(inviterTenantId, tenantId); }
        registration.setTenantId(tenantId).setUserId(tenantService.getTenant(tenantId).getContactUserId());
        registrationMapper.updateById(registration);
        return registration;
    }

    public TenantRegistrationDO recover(TenantRegistrationReqVO req) {
        TenantRegistrationDO existing = registrationMapper.selectByRequestId(req.getRequestId());
        if (existing == null) { return null; }
        if (!req.getMobile().equals(existing.getMobile()) || !payloadHash(req).equals(existing.getPayloadHash()) || existing.getUserId() == null) {
            throw exception0(400, "注册请求冲突，请使用原请求重试");
        }
        return existing;
    }

    String payloadHash(TenantRegistrationReqVO req) {
        // Length-prefixed fields prevent ambiguous concatenation; captcha is renewed on retries.
        StringBuilder canonical = new StringBuilder();
        for (String value : Arrays.asList(req.getRequestId(), req.getTenantName().trim(), req.getUsername(), req.getNickname(),
                req.getPassword(), req.getMobile(), req.getSmsRequestId(), req.getSmsCode(),
                req.getInviteCode() == null ? "" : req.getInviteCode().trim().toUpperCase(Locale.ROOT))) {
            canonical.append(value.length()).append(':').append(value);
        }
        return PhoneVerificationClient.hmac(properties.getPhoneSecret(), canonical.toString());
    }
}
