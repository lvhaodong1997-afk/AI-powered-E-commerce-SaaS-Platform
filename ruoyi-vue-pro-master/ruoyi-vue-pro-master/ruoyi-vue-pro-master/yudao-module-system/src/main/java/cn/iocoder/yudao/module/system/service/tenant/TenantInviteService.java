package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.invite.*;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.*;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception0;

@Service
public class TenantInviteService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    @Resource private TenantInviteMapper inviteMapper;
    @Resource private TenantReferralMapper referralMapper;
    @Resource private TenantService tenantService;
    @Resource private AdminUserMapper userMapper;
    @Resource private TenantRegistrationProperties properties;

    public Long validateInvite(String value) {
        if (value == null || value.trim().isEmpty()) { return null; }
        String code = value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{10}")) { throw invalidInvite(); }
        TenantInviteDO invite = inviteMapper.selectByCode(code);
        if (invite == null) { throw invalidInvite(); }
        validateBusinessTenant(invite.getTenantId());
        return invite.getTenantId();
    }

    @DSTransactional
    public String createCode(Long tenantId) {
        TenantInviteDO existing = inviteMapper.selectByTenantId(tenantId);
        if (existing != null) { return existing.getInviteCode(); }
        for (int attempt = 0; attempt < 8; attempt++) {
            StringBuilder code = new StringBuilder(10);
            for (int i = 0; i < 10; i++) { code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]); }
            try {
                inviteMapper.insert(new TenantInviteDO().setTenantId(tenantId).setInviteCode(code.toString()));
                return code.toString();
            } catch (DuplicateKeyException collision) {
                existing = inviteMapper.selectByTenantId(tenantId);
                if (existing != null) { return existing.getInviteCode(); }
            }
        }
        throw exception0(500, "邀请码生成失败，请重试");
    }

    public void bind(Long inviterTenantId, Long invitedTenantId) {
        if (inviterTenantId.equals(invitedTenantId)) { throw invalidInvite(); }
        referralMapper.insert(new TenantReferralDO().setInviterTenantId(inviterTenantId).setInvitedTenantId(invitedTenantId));
    }

    public TenantInviteMyRespVO my() {
        Long tenantId = requireAdminTenant();
        String code = createCode(tenantId);
        String base = properties.getInviteBaseUrl() == null ? "" : properties.getInviteBaseUrl().replaceAll("/$", "");
        return new TenantInviteMyRespVO().setInviteCode(code).setInviteUrl(base + "/register?inviteCode=" + code)
                .setInvitedTenantCount(referralMapper.countByInviter(tenantId));
    }

    public PageResult<TenantInviteRecordRespVO> records(PageParam page) {
        Long tenantId = requireAdminTenant();
        PageResult<TenantReferralDO> result = referralMapper.selectByInviter(tenantId, page);
        List<TenantInviteRecordRespVO> rows = result.getList().stream().map(referral -> {
            TenantDO tenant = tenantService.getTenant(referral.getInvitedTenantId());
            return new TenantInviteRecordRespVO().setTenantName(maskName(tenant == null ? null : tenant.getName()))
                    .setRegisterTime(referral.getCreateTime());
        }).collect(Collectors.toList());
        return new PageResult<>(rows, result.getTotal());
    }

    private Long requireAdminTenant() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        AdminUserDO user = userId == null ? null : TenantUtils.executeIgnore(() -> userMapper.selectById(userId));
        if (user == null || !Integer.valueOf(0).equals(user.getStatus())
                || !("TENANT_ADMIN".equals(user.getTkUserLevel()) || "COMPANY_ADMIN".equals(user.getTkUserLevel()))
                || user.getTenantId() == null || user.getTenantId() <= 0) {
            throw exception0(403, "仅租户管理员可查看邀请信息");
        }
        validateBusinessTenant(user.getTenantId());
        return user.getTenantId();
    }

    private void validateBusinessTenant(Long tenantId) {
        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null || tenant.getPackageId() == null || tenant.getPackageId() <= 0
                || !Integer.valueOf(0).equals(tenant.getStatus()) || tenant.getExpireTime() == null
                || !tenant.getExpireTime().isAfter(LocalDateTime.now())) {
            throw invalidInvite();
        }
    }

    static String maskName(String value) {
        if (value == null || value.codePointCount(0, value.length()) < 2) { return "***"; }
        return new String(Character.toChars(value.codePointAt(0))) + "***";
    }
    private static RuntimeException invalidInvite() { return exception0(400, "邀请码不存在或已失效"); }
}
