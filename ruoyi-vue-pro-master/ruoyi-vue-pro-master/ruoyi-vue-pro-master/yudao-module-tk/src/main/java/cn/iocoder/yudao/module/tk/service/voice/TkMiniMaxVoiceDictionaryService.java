package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictDataDO;
import cn.iocoder.yudao.module.system.service.dict.DictDataService;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMiniMaxVoiceOptionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceFavoriteDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceFavoriteMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TkMiniMaxVoiceDictionaryService {

    public static final String VOICE_DICT_TYPE = "ai_tts_voice";
    public static final String CONFIG_DICT_TYPE = "ai_tts_config";
    private static final String PROVIDER = "MINIMAX";
    private static final String PREVIEW_BASE_URL =
            "https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/";

    private final DictDataService dictDataService;
    private final TkVoiceFavoriteMapper favoriteMapper;

    public String resolveVoiceCode(Long profileId, String code) {
        if (profileId != null) {
            throw serviceException("MiniMax 系统音色不支持自定义音色档案");
        }
        List<VoiceDefinition> voices = getValidVoices();
        if (voices.isEmpty()) {
            throw serviceException("MiniMax 系统音色未配置或全部停用");
        }
        String requested = StrUtil.trim(code);
        if (StrUtil.isBlank(requested)) {
            return voices.stream().filter(VoiceDefinition::isDefault).findFirst()
                    .map(VoiceDefinition::getVoiceId)
                    .orElseThrow(() -> serviceException("MiniMax 系统音色未配置默认音色"));
        }
        return voices.stream()
                .filter(voice -> requested.equals(voice.getValue()) || requested.equals(voice.getVoiceId()))
                .findFirst().map(VoiceDefinition::getVoiceId)
                .orElseThrow(() -> serviceException("MiniMax 系统音色不存在或已停用"));
    }

    public Map<String, Object> buildTtsRequest(String text, String voiceId) {
        if (StrUtil.isBlank(text) || text.length() > 10_000) {
            throw serviceException("MiniMax 合成文本不能为空且不能超过 10000 个字符");
        }
        if (StrUtil.isBlank(voiceId) || voiceId.length() > 128) {
            throw serviceException("MiniMax 音色编码不能为空且不能超过 128 个字符");
        }
        TtsConfiguration config = loadConfiguration();
        Map<String, Object> voiceSetting = new LinkedHashMap<>();
        voiceSetting.put("voice_id", voiceId);
        voiceSetting.put("speed", config.speed);
        voiceSetting.put("vol", config.vol);
        voiceSetting.put("pitch", config.pitch);
        voiceSetting.put("emotion", config.emotion);

        Map<String, Object> audioSetting = new LinkedHashMap<>();
        audioSetting.put("sample_rate", config.sampleRate);
        audioSetting.put("bitrate", config.bitrate);
        audioSetting.put("format", "mp3");
        audioSetting.put("channel", 1);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", config.model);
        request.put("text", text);
        request.put("output_format", "url");
        request.put("subtitle_enable", false);
        request.put("aigc_watermark", false);
        request.put("language_boost", config.languageBoost);
        request.put("voice_setting", voiceSetting);
        request.put("audio_setting", audioSetting);
        return request;
    }

    public List<TkMiniMaxVoiceOptionRespVO> getOptions() {
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        Set<String> favorites = favoriteMapper.selectListByScope(tenantId, userId, PROVIDER).stream()
                .map(TkVoiceFavoriteDO::getVoiceCode).collect(Collectors.toSet());
        return getValidVoices().stream().map(voice -> voice.toOption(favorites.contains(voice.voiceId)))
                .collect(Collectors.toList());
    }

    public void addFavorite(String voiceCode) {
        String canonicalVoiceId = resolveVoiceCode(null, voiceCode);
        Long tenantId = requireTenantId();
        Long userId = requireUserId();
        TkVoiceFavoriteDO favorite = new TkVoiceFavoriteDO();
        favorite.setTenantId(tenantId);
        favorite.setUserId(userId);
        favorite.setProvider(PROVIDER);
        favorite.setVoiceCode(canonicalVoiceId);
        favoriteMapper.insertIgnore(favorite);
    }

    public void removeFavorite(String voiceCode) {
        String canonicalVoiceId = resolveVoiceCode(null, voiceCode);
        favoriteMapper.deleteByScope(requireTenantId(), requireUserId(), PROVIDER, canonicalVoiceId);
    }

    private List<VoiceDefinition> getValidVoices() {
        List<DictDataDO> data = dictDataService.getDictDataList(0, VOICE_DICT_TYPE);
        if (data == null) {
            return Collections.emptyList();
        }
        List<VoiceDefinition> voices = new ArrayList<>();
        for (DictDataDO item : data) {
            try {
                voices.add(parseVoice(item));
            } catch (RuntimeException ex) {
                log.warn("Skipping invalid MiniMax system voice dictionary item, id={}, reason=invalid metadata",
                        item.getId());
            }
        }
        return voices;
    }

    private VoiceDefinition parseVoice(DictDataDO data) {
        VoiceMetadata metadata = parseMetadata(data.getRemark(), VoiceMetadata.class);
        if (metadata == null || StrUtil.isBlank(metadata.voiceId)
                || StrUtil.isBlank(metadata.language) || StrUtil.isBlank(metadata.country)) {
            throw new IllegalArgumentException("remark requires voiceId, language and country");
        }
        if (StrUtil.isBlank(data.getValue()) || StrUtil.isBlank(data.getLabel())) {
            throw new IllegalArgumentException("value and label are required");
        }
        return new VoiceDefinition(data.getValue().trim(), data.getLabel().trim(), metadata);
    }

    private TtsConfiguration loadConfiguration() {
        TtsConfiguration config = new TtsConfiguration();
        List<DictDataDO> data = dictDataService.getDictDataList(0, CONFIG_DICT_TYPE);
        if (data != null) {
            for (DictDataDO item : data) {
                try {
                    ConfigMetadata metadata = parseMetadata(item.getRemark(), ConfigMetadata.class);
                    if (metadata != null && StrUtil.isNotBlank(metadata.value)) {
                        config.apply(StrUtil.trim(item.getValue()), StrUtil.trim(metadata.value));
                    }
                } catch (RuntimeException ex) {
                    log.warn("Skipping invalid MiniMax TTS config dictionary item, id={}", item.getId());
                }
            }
        }
        config.validate();
        return config;
    }

    private static <T> T parseMetadata(String json, Class<T> type) {
        if (StrUtil.isBlank(json)) { return null; }
        try {
            return JsonUtils.getObjectMapper().readValue(json, type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("invalid metadata");
        }
    }

    private static Long requireTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw serviceException("当前请求缺少租户上下文");
        }
        return tenantId;
    }

    private static Long requireUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw serviceException("当前请求未登录");
        }
        return userId;
    }

    private static ServiceException serviceException(String message) {
        return new ServiceException(1_520_100_001, message);
    }

    private static String buildPreviewUrl(String voiceId) {
        return PREVIEW_BASE_URL + UriUtils.encodePathSegment(voiceId, StandardCharsets.UTF_8) + ".mp3";
    }

    @Data
    private static class VoiceMetadata {
        private String voiceId;
        private String model;
        private String language;
        private String country;
        private String gender;
        private String description;
        private String previewUrl;
        @JsonProperty("isDefault")
        private Boolean isDefault;
    }

    @Data
    private static class ConfigMetadata {
        private String value;
    }

    private static class VoiceDefinition {
        private final String value;
        private final String label;
        private final String voiceId;
        private final String model;
        private final String language;
        private final String country;
        private final String gender;
        private final String description;
        private final String previewUrl;
        private final boolean defaultVoice;

        private VoiceDefinition(String value, String label, VoiceMetadata metadata) {
            this.value = value;
            this.label = label;
            this.voiceId = metadata.voiceId.trim();
            this.model = StrUtil.blankToDefault(StrUtil.trim(metadata.model), "speech-2.8-turbo");
            this.language = metadata.language.trim();
            this.country = metadata.country.trim();
            this.gender = StrUtil.trim(metadata.gender);
            this.description = StrUtil.trim(metadata.description);
            this.previewUrl = StrUtil.blankToDefault(StrUtil.trim(metadata.previewUrl), buildPreviewUrl(this.voiceId));
            this.defaultVoice = Boolean.TRUE.equals(metadata.isDefault);
        }

        private String getValue() { return value; }
        private String getVoiceId() { return voiceId; }
        private boolean isDefault() { return defaultVoice; }

        private TkMiniMaxVoiceOptionRespVO toOption(boolean favorite) {
            return new TkMiniMaxVoiceOptionRespVO().setValue(value).setLabel(label).setVoiceId(voiceId)
                    .setModel(model).setLanguage(language).setCountry(country).setGender(gender)
                    .setDescription(description).setPreviewUrl(previewUrl).setFavorite(favorite)
                    .setIsDefault(defaultVoice);
        }
    }

    private static class TtsConfiguration {
        private String model = "speech-2.8-turbo";
        private String languageBoost = "auto";
        private BigDecimal speed = new BigDecimal("1.2");
        private BigDecimal vol = new BigDecimal("1.1");
        private int pitch;
        private String emotion = "fluent";
        private int sampleRate = 32000;
        private int bitrate = 128000;

        private void apply(String key, String value) {
            switch (StrUtil.blankToDefault(key, "")) {
                case "model": model = value; break;
                case "language_boost": languageBoost = value; break;
                case "speed": speed = new BigDecimal(value); break;
                case "vol": vol = new BigDecimal(value); break;
                case "pitch": pitch = Integer.parseInt(value); break;
                case "emotion": emotion = value; break;
                case "sample_rate": sampleRate = Integer.parseInt(value); break;
                case "bitrate": bitrate = Integer.parseInt(value); break;
                default: break;
            }
        }

        private void validate() {
            model = StrUtil.blankToDefault(model, "speech-2.8-turbo");
            languageBoost = StrUtil.blankToDefault(languageBoost, "auto");
            emotion = StrUtil.blankToDefault(emotion, "fluent");
            if (speed.compareTo(new BigDecimal("0.5")) < 0 || speed.compareTo(new BigDecimal("2.0")) > 0) {
                speed = new BigDecimal("1.2");
            }
            if (vol.compareTo(BigDecimal.ZERO) <= 0 || vol.compareTo(new BigDecimal("10")) > 0) {
                vol = new BigDecimal("1.1");
            }
            if (pitch < -12 || pitch > 12) pitch = 0;
            if (sampleRate <= 0) sampleRate = 32000;
            if (bitrate <= 0) bitrate = 128000;
        }
    }
}
