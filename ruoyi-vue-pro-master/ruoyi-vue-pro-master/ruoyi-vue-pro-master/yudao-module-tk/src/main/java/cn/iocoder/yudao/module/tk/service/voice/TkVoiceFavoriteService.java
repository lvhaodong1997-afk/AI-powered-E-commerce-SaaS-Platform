package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceFavoriteDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceFavoriteMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TkVoiceFavoriteService {
    private final TkVoiceFavoriteMapper favoriteMapper;
    private final TkDataScopeService dataScopeService;
    private final TkMiniMaxVoiceDictionaryService miniMaxService;
    private final TkVoiceProfileService profileService;

    public List<String> getFavorites(String provider) {
        String normalized = normalizeProvider(provider);
        return favoriteMapper.selectListByScope(requireTenantId(), requireUserId(), normalized).stream()
                .map(TkVoiceFavoriteDO::getVoiceCode).collect(Collectors.toList());
    }

    public void addFavorite(String provider, String voiceCode) {
        String normalized = normalizeProvider(provider);
        String code = normalizeCode(voiceCode);
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        if ("MINIMAX".equals(normalized)) {
            miniMaxService.addFavorite(code);
            return;
        }
        if (code.startsWith("custom:")) {
            validateCustomVoice(code, tenantId);
        } else {
            // Existing preset validation is local and does not generate speech or call the provider.
            profileService.resolveVoiceSelection(null, code);
        }
        TkVoiceFavoriteDO favorite = new TkVoiceFavoriteDO();
        favorite.setTenantId(tenantId);
        favorite.setUserId(userId);
        favorite.setProvider(normalized);
        favorite.setVoiceCode(code);
        favoriteMapper.insertIgnore(favorite);
    }

    public void removeFavorite(String provider, String voiceCode) {
        String normalized = normalizeProvider(provider);
        String code = normalizeCode(voiceCode);
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        // Generic API accepts persisted keys, never aliases. Exact deletion is repeatable even if
        // a deleted MiniMax ID has since been reused as an alias for another voice.
        favoriteMapper.deleteByScope(tenantId, userId, normalized, code);
    }

    private void validateCustomVoice(String code, Long tenantId) {
        Long profileId;
        try {
            if (!code.matches("custom:[1-9][0-9]*")) {
                throw new NumberFormatException();
            }
            profileId = Long.valueOf(code.substring("custom:".length()));
        } catch (NumberFormatException ex) {
            throw invalid("自定义音色编码无效");
        }
        // getVoice enforces current read visibility; the tenant equality also fences platform admins.
        TkVoiceProfileDO profile = profileService.getVoice(profileId);
        if (profile == null || !tenantId.equals(profile.getTenantId())
                || !"DASHSCOPE".equalsIgnoreCase(StrUtil.blankToDefault(profile.getProvider(), "DASHSCOPE"))
                || !"READY".equals(profile.getStatus()) || !Boolean.TRUE.equals(profile.getEnabled())
                || Boolean.TRUE.equals(profile.getDeleted()) || StrUtil.isBlank(profile.getVoiceCode())) {
            throw invalid("自定义音色不存在、不可访问或尚不可用");
        }
    }

    private String normalizeProvider(String provider) {
        String normalized = StrUtil.blankToDefault(provider, "").trim().toUpperCase(Locale.ROOT);
        if (!"MINIMAX".equals(normalized) && !"DASHSCOPE".equals(normalized)) {
            throw invalid("不支持的音色提供方");
        }
        return normalized;
    }

    private String normalizeCode(String voiceCode) {
        String code = StrUtil.trim(voiceCode);
        if (StrUtil.isBlank(code) || code.length() > 128) {
            throw invalid("音色编码不能为空且不能超过 128 个字符");
        }
        return code;
    }

    private Long requireTenantId() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (scope == null || scope.getTenantId() == null) {
            throw invalid("当前请求缺少租户上下文");
        }
        return scope.getTenantId();
    }

    private Long requireUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw invalid("当前请求未登录");
        }
        return userId;
    }

    private static ServiceException invalid(String message) {
        return new ServiceException(1_520_100_001, message);
    }
}
