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
            java.util.Arrays.asList("auth", "media", "publish"));
    private final TkOpenApiClientMapper clientMapper;
    private final TkOpenApiSecretCipher secretCipher;

    public TkOpenApiClientAdminService(TkOpenApiClientMapper clientMapper, TkOpenApiSecretCipher secretCipher) {
        this.clientMapper = clientMapper;
        this.secretCipher = secretCipher;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenApiClientAdminVO.CredentialResp create(TkOpenApiClientAdminVO.CreateReq request) {
        validate(request);
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
        validate(request);
        TkOpenApiClientDO existing = requireClient(request.getClientId());
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

    private void validate(TkOpenApiClientAdminVO.CreateReq request) {
        validateCallback(request.getAuthCallbackUrl());
        validateCallback(request.getPublishCallbackUrl());
        if (!cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIpMatcher.isValidRules(request.getAllowedIps())) {
            throw ServiceExceptionUtil.invalidParamException("IP 白名单格式不正确");
        }
        request.setPermissions(normalizePermissions(request.getPermissions()));
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

    private String normalizePermissions(String permissions) {
        String value = StrUtil.blankToDefault(permissions, "auth,media,publish");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : value.toLowerCase().split("[,;\\s]+")) {
            if (!SUPPORTED_PERMISSIONS.contains(item)) {
                throw ServiceExceptionUtil.invalidParamException("不支持的开放 API 权限：{}", item);
            }
            result.add(item);
        }
        return String.join(",", result);
    }

    private TkOpenApiClientDO copyEditable(TkOpenApiClientAdminVO.CreateReq request, TkOpenApiClientDO target) {
        return target.setClientName(request.getClientName().trim())
                .setAuthCallbackUrl(StrUtil.trim(request.getAuthCallbackUrl()))
                .setPublishCallbackUrl(StrUtil.trim(request.getPublishCallbackUrl()))
                .setAllowedIps(StrUtil.trim(request.getAllowedIps()))
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
