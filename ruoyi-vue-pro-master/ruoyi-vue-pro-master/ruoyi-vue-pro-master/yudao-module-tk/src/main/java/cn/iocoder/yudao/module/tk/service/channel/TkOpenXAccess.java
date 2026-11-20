package cn.iocoder.yudao.module.tk.service.channel;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXEndUserDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXEndUserMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TkOpenXAccess {
    public static final String END_USER_PATTERN = "[A-Za-z0-9._:-]{1,128}";

    private final TkOpenXProperties properties;
    private final TkOpenApiClientMapper clients;
    private final TkOpenXEndUserMapper endUsers;
    private final AdminUserMapper adminUsers;

    public TkOpenXProperties.Grant requireGrant(String clientId) {
        if (!properties.isEnabled()) throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        List<TkOpenXProperties.Grant> matches = properties.getGrants().stream()
                .filter(g -> clientId != null && clientId.equals(g.getClientId())).collect(Collectors.toList());
        if (matches.size() != 1 || !matches.get(0).hasValidScope()) {
            throw TkOpenApiException.forbidden("X_CLIENT_NOT_CONFIGURED", "X publishing is not enabled for this client");
        }
        return matches.get(0);
    }

    public Binding requireBinding(TkOpenXProperties.Grant grant, String endUserId, Long requestedOwnerUserId, boolean bindIfAbsent) {
        if (grant.isPilot()) {
            if (requestedOwnerUserId != null && !requestedOwnerUserId.equals(grant.getOwnerUserId())) {
                throw TkOpenApiException.forbidden("X_OWNER_INVALID", "Owner is not in the provisioned scope");
            }
            return new Binding(grant, null, grant.getOwnerUserId(), true);
        }
        String normalized = normalizeEndUserId(endUserId);
        TkOpenXEndUserDO existing = endUsers.findByClientAndEndUser(grant.getClientId(), normalized);
        if (existing != null) {
            if (!Objects.equals(existing.getTenantId(), grant.getTenantId())
                    || !Objects.equals(existing.getCompanyId(), grant.getCompanyId())) {
                throw TkOpenApiException.forbidden("X_END_USER_OWNER_CONFLICT", "End user is already bound in a different scope");
            }
            if (requestedOwnerUserId != null && !requestedOwnerUserId.equals(existing.getOwnerUserId())) {
                throw TkOpenApiException.forbidden("X_END_USER_OWNER_CONFLICT", "End user is already bound to a different owner");
            }
            return new Binding(grant, normalized, existing.getOwnerUserId(), false);
        }
        if (!bindIfAbsent) {
            throw TkOpenApiException.forbidden("X_END_USER_NOT_BOUND", "End user has not been bound");
        }
        if (requestedOwnerUserId == null || requestedOwnerUserId <= 0) {
            throw TkOpenApiException.badRequest("X_OWNER_REQUIRED", "ownerUserId is required to bind an end user");
        }
        requireTenantMember(grant, requestedOwnerUserId);
        TkOpenXEndUserDO row = new TkOpenXEndUserDO();
        row.setClientId(grant.getClientId());
        row.setEndUserId(normalized);
        row.setTenantId(grant.getTenantId());
        row.setCompanyId(grant.getCompanyId());
        row.setOwnerUserId(requestedOwnerUserId);
        try {
            endUsers.insert(row);
        } catch (DuplicateKeyException ex) {
            TkOpenXEndUserDO raced = endUsers.findByClientAndEndUser(grant.getClientId(), normalized);
            if (raced == null || !Objects.equals(raced.getOwnerUserId(), requestedOwnerUserId)
                    || !Objects.equals(raced.getTenantId(), grant.getTenantId())
                    || !Objects.equals(raced.getCompanyId(), grant.getCompanyId())) {
                throw TkOpenApiException.forbidden("X_END_USER_OWNER_CONFLICT", "End user is already bound to a different owner");
            }
            return new Binding(grant, normalized, raced.getOwnerUserId(), false);
        }
        return new Binding(grant, normalized, requestedOwnerUserId, false);
    }

    public Binding requireExistingBinding(TkOpenXProperties.Grant grant, String endUserId) {
        return requireBinding(grant, endUserId, null, false);
    }

    public TkOpenXProperties.Grant requireSessionGrant(TkSocialAuthSessionDO row, String permission) {
        TkOpenXProperties.Grant grant = requireGrant(row.getOpenClientId());
        TkOpenApiClientDO client = clients.selectByClientId(row.getOpenClientId());
        if (!matches(row, grant, row.getOpenEndUserId()) || client == null || !Integer.valueOf(0).equals(client.getStatus())
                || !Objects.equals(row.getOpenClientId(), client.getClientId())
                || !("," + client.getPermissions() + ",").contains("," + permission + ",")) {
            throw TkOpenApiException.forbidden("X_CONNECTION_DISABLED", "X connection is no longer permitted");
        }
        return grant;
    }

    public void requireTenantMember(TkOpenXProperties.Grant grant, Long ownerUserId) {
        AtomicReference<AdminUserDO> found = new AtomicReference<>();
        TenantUtils.execute(grant.getTenantId(), () -> found.set(adminUsers.selectById(ownerUserId)));
        AdminUserDO user = found.get();
        if (user == null || user.getId() == null || !Objects.equals(user.getId(), ownerUserId)
                || !Objects.equals(user.getTenantId(), grant.getTenantId())
                || !Objects.equals(user.getTkCompanyId(), grant.getCompanyId())
                || !CommonStatusEnum.isEnable(user.getStatus())) {
            throw TkOpenApiException.forbidden("X_OWNER_INVALID", "Owner is not an active member of the provisioned tenant and company");
        }
    }

    public static String normalizeEndUserId(String endUserId) {
        if (endUserId == null || !endUserId.matches(END_USER_PATTERN)) {
            throw TkOpenApiException.badRequest("X_END_USER_REQUIRED", "A valid endUserId is required");
        }
        return endUserId;
    }

    static boolean matches(TkSocialAuthSessionDO row, TkOpenXProperties.Grant grant, String endUserId) {
        if (row == null || grant == null || !TkChannelProperties.X.equals(row.getPlatform())
                || !Objects.equals(row.getOpenClientId(), grant.getClientId())
                || !Objects.equals(row.getTenantId(), grant.getTenantId())
                || !Objects.equals(row.getCompanyId(), grant.getCompanyId())) {
            return false;
        }
        if (grant.isPilot()) {
            return Objects.equals(row.getCreator(), String.valueOf(grant.getOwnerUserId()))
                    && (endUserId == null || endUserId.isEmpty() || Objects.equals(row.getOpenEndUserId(), endUserId)
                    || row.getOpenEndUserId() == null);
        }
        return endUserId != null && endUserId.equals(row.getOpenEndUserId())
                && Objects.equals(row.getCreator(), row.getCreator())
                && StrUtil.isNotBlank(row.getOpenEndUserId());
    }

    static boolean matches(TkSocialAuthSessionDO row, TkOpenXProperties.Grant grant) {
        return matches(row, grant, row == null ? null : row.getOpenEndUserId());
    }

    static boolean usable(TkSocialAuthSessionDO row, TkSocialAccountDO account, TkOpenXProperties.Grant grant) {
        if (!"SUCCESS".equals(row.getStatus()) || account == null || !"AUTHORIZED".equals(account.getStatus())
                || !TkChannelProperties.X.equals(account.getPlatform())
                || !Objects.equals(row.getOpenAccountId(), account.getId())
                || !Objects.equals(row.getTenantId(), account.getTenantId())
                || !Objects.equals(row.getCompanyId(), account.getCompanyId())
                || !Objects.equals(row.getCreator(), account.getCreator())) {
            return false;
        }
        if (grant.isPilot()) {
            return Objects.equals(grant.getXUserId(), account.getExternalAccountId());
        }
        return account.getExternalAccountId() != null && account.getExternalAccountId().matches("[0-9]{1,30}");
    }

    public static final class Binding {
        private final TkOpenXProperties.Grant grant;
        private final String endUserId;
        private final Long ownerUserId;
        private final boolean pilot;

        public Binding(TkOpenXProperties.Grant grant, String endUserId, Long ownerUserId, boolean pilot) {
            this.grant = grant;
            this.endUserId = endUserId;
            this.ownerUserId = ownerUserId;
            this.pilot = pilot;
        }

        public TkOpenXProperties.Grant getGrant() { return grant; }
        public String getEndUserId() { return endUserId; }
        public Long getOwnerUserId() { return ownerUserId; }
        public boolean isPilot() { return pilot; }
        public Long getTenantId() { return grant.getTenantId(); }
        public Long getCompanyId() { return grant.getCompanyId(); }
        public String getClientId() { return grant.getClientId(); }
    }
}
