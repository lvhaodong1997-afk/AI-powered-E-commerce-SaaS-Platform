package cn.iocoder.yudao.module.tk.service.open.admin;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiClientAdminVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiCallbackUrlValidator;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXProperties;
import cn.iocoder.yudao.module.tk.service.open.x.TkOpenXReturnUrlValidator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TkOpenApiClientAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> SUPPORTED_PERMISSIONS = new LinkedHashSet<>(
            java.util.Arrays.asList("auth", "media", "publish", "x.auth", "x.publish", "youtube.auth", "youtube.publish",
                    "facebook.auth", "facebook.publish", "instagram.auth", "instagram.publish"));
    private final TkOpenApiClientMapper clientMapper;
    private final TkOpenApiSecretCipher secretCipher;
    private final TkOpenXProperties xProperties;

    // Single constructor only: a second overload without @Autowired makes Spring look for a
    // no-arg constructor and fail context startup (production 98787dd crash).
    public TkOpenApiClientAdminService(TkOpenApiClientMapper clientMapper, TkOpenApiSecretCipher secretCipher, TkOpenXProperties xProperties) {
        this.clientMapper = clientMapper;
        this.secretCipher = secretCipher;
        this.xProperties = xProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenApiClientAdminVO.CredentialResp create(TkOpenApiClientAdminVO.CreateReq request) {
        validate(request, null);
        String clientId = nextValue("client", 18);
        String clientSecret = nextValue("tksec", 32);
        String callbackSecret = nextValue("tkcb", 32);
        TkOpenApiClientDO client = copyEditable(request, new TkOpenApiClientDO())
                .setClientId(clientId)
                .setClientSecretCipher(secretCipher.encrypt(clientSecret))
                .setCallbackSecretCipher(secretCipher.encrypt(callbackSecret));
        clientMapper.insert(client);
        return credential(clientId, clientSecret, callbackSecret);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(TkOpenApiClientAdminVO.UpdateReq request) {
        TkOpenApiClientDO existing = requireClient(request.getClientId());
        validate(request, existing.getPermissions());
        clientMapper.updateById(copyEditable(request, new TkOpenApiClientDO().setId(existing.getId())));
    }

    public void updateStatus(TkOpenApiClientAdminVO.StatusReq request) {
        TkOpenApiClientDO existing = requireClient(request.getClientId());
        clientMapper.updateById(new TkOpenApiClientDO().setId(existing.getId()).setStatus(request.getStatus()));
    }

    public void delete(String clientId) {
        clientMapper.deleteById(requireClient(clientId).getId());
    }

    public TkOpenApiClientAdminVO.Resp get(String clientId) {
        return toResp(requireClient(clientId));
    }

    public PageResult<TkOpenApiClientAdminVO.Resp> getPage(TkOpenApiClientAdminVO.PageReq request) {
        PageResult<TkOpenApiClientDO> page = clientMapper.selectPage(request);
        List<TkOpenApiClientAdminVO.Resp> result = new ArrayList<>();
        for (TkOpenApiClientDO item : page.getList()) {
            result.add(toResp(item));
        }
        return new PageResult<>(result, page.getTotal());
    }

    public TkOpenApiClientAdminVO.CredentialResp rotateSecret(String clientId, String type) {
        TkOpenApiClientDO existing = requireClient(clientId);
        String secret = nextValue("CALLBACK".equalsIgnoreCase(type) ? "tkcb" : "tksec", 32);
        TkOpenApiClientDO update = new TkOpenApiClientDO().setId(existing.getId());
        if ("CALLBACK".equalsIgnoreCase(type)) {
            update.setCallbackSecretCipher(secretCipher.encrypt(secret));
            clientMapper.updateById(update);
            return credential(clientId, null, secret);
        }
        if (!"CLIENT".equalsIgnoreCase(type)) {
            throw ServiceExceptionUtil.invalidParamException("密钥类型必须是 CLIENT 或 CALLBACK");
        }
        update.setClientSecretCipher(secretCipher.encrypt(secret));
        clientMapper.updateById(update);
        return credential(clientId, secret, null);
    }

    private void validate(TkOpenApiClientAdminVO.CreateReq request, String existingPermissions) {
        validateCallback(request.getAuthCallbackUrl());
        validateCallback(request.getPublishCallbackUrl());
        if (!cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIpMatcher.isValidRules(request.getAllowedIps())) {
            throw ServiceExceptionUtil.invalidParamException("IP 白名单格式不正确");
        }
        request.setPermissions(normalizePermissions(request.getPermissions(), existingPermissions));
        request.setReturnUrlWhitelist(normalizeReturnUrlWhitelist(request.getReturnUrlWhitelist()));
        if (request.getRateLimitPerMinute() == null) request.setRateLimitPerMinute(120);
        if (request.getDailyQuota() == null) request.setDailyQuota(10000);
        if (request.getStatus() == null) request.setStatus(0);
        if (request.getStatus() != 0 && request.getStatus() != 1) {
            throw ServiceExceptionUtil.invalidParamException("调用方状态必须是 0 或 1");
        }
    }

    private void validateCallback(String url) {
        try {
            TkOpenApiCallbackUrlValidator.validate(url);
        } catch (IllegalArgumentException ex) {
            throw ServiceExceptionUtil.invalidParamException(ex.getMessage());
        }
    }

    private String normalizePermissions(String permissions, String existingPermissions) {
        String value = StrUtil.blankToDefault(permissions, "auth,media,publish");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : value.toLowerCase().split("[,;\\s]+")) {
            if (item.isEmpty()) continue;
            if (!item.matches("[a-z][a-z0-9._-]{0,63}")) {
                throw ServiceExceptionUtil.invalidParamException("不支持的开放 API 权限：{}", item);
            }
            result.add(item);
        }
        if (existingPermissions != null) {
            for (String item : existingPermissions.toLowerCase().split("[,;\\s]+")) {
                if (!item.isEmpty() && !SUPPORTED_PERMISSIONS.contains(item)) result.add(item);
            }
        }
        if (result.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("至少选择一项开放 API 权限");
        }
        return String.join(",", result);
    }

    private String normalizeReturnUrlWhitelist(String raw) {
        if (StrUtil.isBlank(raw)) return null;
        List<String> values = new ArrayList<>();
        String trimmed = raw.trim();
        try {
            if (trimmed.startsWith("[")) {
                List<String> parsed = JsonUtils.parseArray(trimmed, String.class); if (parsed != null) values.addAll(parsed);
            } else {
                for (String line : trimmed.split("\\r?\\n")) {
                    if (StrUtil.isNotBlank(line)) values.add(line.trim());
                }
            }
        } catch (Exception ex) {
            throw ServiceExceptionUtil.invalidParamException("X 授权完成回跳白名单格式不正确");
        }
        boolean loopback = xProperties != null && xProperties.isAllowInsecureLoopbackReturnUrl();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : values) {
            if (StrUtil.isBlank(item)) continue;
            try {
                normalized.add(TkOpenXReturnUrlValidator.key(TkOpenXReturnUrlValidator.normalize(item, loopback)));
            } catch (RuntimeException ex) {
                throw ServiceExceptionUtil.invalidParamException("X 授权完成回跳白名单包含非法地址");
            }
        }
        if (normalized.isEmpty()) return null;
        try {
            return JsonUtils.toJsonString(new ArrayList<>(normalized));
        } catch (Exception ex) {
            throw ServiceExceptionUtil.invalidParamException("X 授权完成回跳白名单格式不正确");
        }
    }

    private TkOpenApiClientDO copyEditable(TkOpenApiClientAdminVO.CreateReq request, TkOpenApiClientDO target) {
        return target.setClientName(request.getClientName().trim())
                .setAuthCallbackUrl(StrUtil.trim(request.getAuthCallbackUrl()))
                .setPublishCallbackUrl(StrUtil.trim(request.getPublishCallbackUrl()))
                .setAllowedIps(StrUtil.trim(request.getAllowedIps()))
                .setReturnUrlWhitelist(StrUtil.trim(request.getReturnUrlWhitelist()))
                .setPermissions(request.getPermissions())
                .setRateLimitPerMinute(request.getRateLimitPerMinute())
                .setDailyQuota(request.getDailyQuota())
                .setStatus(request.getStatus())
                .setRemark(StrUtil.trim(request.getRemark()));
    }

    private TkOpenApiClientDO requireClient(String clientId) {
        TkOpenApiClientDO client = clientMapper.selectByClientId(clientId);
        if (client == null) {
            throw new ServiceException(GlobalErrorCodeConstants.NOT_FOUND.getCode(), "开放 API 调用方不存在");
        }
        return client;
    }

    private TkOpenApiClientAdminVO.Resp toResp(TkOpenApiClientDO client) {
        TkOpenApiClientAdminVO.Resp response = new TkOpenApiClientAdminVO.Resp();
        response.setClientId(client.getClientId());
        response.setClientName(client.getClientName());
        response.setAuthCallbackUrl(client.getAuthCallbackUrl());
        response.setPublishCallbackUrl(client.getPublishCallbackUrl());
        response.setAllowedIps(client.getAllowedIps());
        response.setReturnUrlWhitelist(client.getReturnUrlWhitelist());
        response.setPermissions(client.getPermissions());
        response.setRateLimitPerMinute(client.getRateLimitPerMinute());
        response.setDailyQuota(client.getDailyQuota());
        response.setStatus(client.getStatus());
        response.setRemark(client.getRemark());
        response.setCreateTime(client.getCreateTime());
        response.setUpdateTime(client.getUpdateTime());
        return response;
    }

    private TkOpenApiClientAdminVO.CredentialResp credential(String clientId, String clientSecret, String callbackSecret) {
        TkOpenApiClientAdminVO.CredentialResp response = new TkOpenApiClientAdminVO.CredentialResp();
        response.setClientId(clientId);
        response.setClientSecret(clientSecret);
        response.setCallbackSecret(callbackSecret);
        return response;
    }

    private String nextValue(String prefix, int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return prefix + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
