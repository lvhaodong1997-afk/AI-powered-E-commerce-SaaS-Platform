package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialAuthSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TkOpenXAccess {
    private final TkOpenXProperties properties;
    private final TkOpenApiClientMapper clients;

    public TkOpenXProperties.Grant requireGrant(String clientId) {
        if (!properties.isEnabled()) throw TkOpenApiException.unavailable("X_OPEN_API_DISABLED", "X OpenAPI is disabled");
        List<TkOpenXProperties.Grant> matches = properties.getGrants().stream()
                .filter(g -> clientId != null && clientId.equals(g.getClientId())).collect(Collectors.toList());
        if (matches.size() != 1 || !valid(matches.get(0)))
            throw TkOpenApiException.forbidden("X_CLIENT_NOT_CONFIGURED", "X publishing is not enabled for this client");
        return matches.get(0);
    }

    public TkOpenXProperties.Grant requireSessionGrant(TkSocialAuthSessionDO row, String permission) {
        TkOpenXProperties.Grant grant = requireGrant(row.getOpenClientId());
        TkOpenApiClientDO client = clients.selectByClientId(row.getOpenClientId());
        if (!matches(row, grant) || client == null || !Integer.valueOf(0).equals(client.getStatus())
                || !Objects.equals(row.getOpenClientId(), client.getClientId())
                || !("," + client.getPermissions() + ",").contains("," + permission + ",")) {
            throw TkOpenApiException.forbidden("X_CONNECTION_DISABLED", "X connection is no longer permitted");
        }
        return grant;
    }

    static boolean matches(TkSocialAuthSessionDO row, TkOpenXProperties.Grant grant) {
        return TkChannelProperties.X.equals(row.getPlatform()) && Objects.equals(row.getOpenClientId(), grant.getClientId())
                && Objects.equals(row.getTenantId(), grant.getTenantId()) && Objects.equals(row.getCompanyId(), grant.getCompanyId())
                && Objects.equals(row.getCreator(), String.valueOf(grant.getOwnerUserId()));
    }

    static boolean usable(TkSocialAuthSessionDO row, TkSocialAccountDO account, TkOpenXProperties.Grant grant) {
        return "SUCCESS".equals(row.getStatus()) && account != null && "AUTHORIZED".equals(account.getStatus())
                && TkChannelProperties.X.equals(account.getPlatform()) && Objects.equals(row.getOpenAccountId(), account.getId())
                && Objects.equals(row.getTenantId(), account.getTenantId()) && Objects.equals(row.getCompanyId(), account.getCompanyId())
                && Objects.equals(row.getCreator(), account.getCreator()) && Objects.equals(grant.getXUserId(), account.getExternalAccountId());
    }

    private static boolean valid(TkOpenXProperties.Grant grant) {
        return grant.getTenantId() != null && grant.getTenantId() > 0 && grant.getCompanyId() != null && grant.getCompanyId() > 0
                && grant.getOwnerUserId() != null && grant.getOwnerUserId() > 0
                && grant.getXUserId() != null && grant.getXUserId().matches("[0-9]{1,30}");
    }
}
