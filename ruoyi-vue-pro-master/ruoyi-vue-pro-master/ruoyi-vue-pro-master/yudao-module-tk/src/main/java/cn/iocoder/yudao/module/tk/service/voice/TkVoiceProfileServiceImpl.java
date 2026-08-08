package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceProfileMapper;
import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.enums.TkVoiceProfileStatusEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDashScopeTtsClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoTtsClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoVoiceModeEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
public class TkVoiceProfileServiceImpl implements TkVoiceProfileService {

    private static final long MAX_AUDIO_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_FILE_SIZE = 100L * 1024 * 1024;
    private static final List<String> ALLOWED_AUDIO_EXTENSIONS = Arrays.asList("mp3", "wav", "m4a");
    private static final List<String> ALLOWED_VIDEO_EXTENSIONS = Arrays.asList("mp4", "mov", "webm");
    private static final String PREVIEW_TEXT = "这是我的自定义音色试听，适合短视频自然口播。";
    private static final String DEFAULT_SYSTEM_VOICE_CODE =
            "cosyvoice-v3.5-plus-tklisa-06c5654167dd4da3bfd5d69dfd5402b0";
    private static final Set<String> SYSTEM_VOICE_CODES = new HashSet<>(Arrays.asList(
            DEFAULT_SYSTEM_VOICE_CODE,
            "cosyvoice-v3.5-plus-tklisas-50aa9d9a3de84a68993fb3f43249f782",
            "cosyvoice-v3.5-plus-tkwincent-a0246845fbee48f998c61d3d5fa552a8",
            "cosyvoice-v3.5-plus-tkwincent-eaaebcdfecc646eb9d0457f6a2eadffb",
            "cosyvoice-v3.5-plus-tklandrut-debf6da87564451f861465eee9fbc7de",
            "cosyvoice-v3.5-plus-tklea-72cb876e220e4a668e7c5b64ac97faf9",
            "cosyvoice-v3.5-plus-tkjojosiwa-86a967cf093b4fbcb4325cd7e53a8f88"));

    @Resource
    private TkVoiceProfileMapper voiceProfileMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private FileApi fileApi;
    @Resource
    private TkDashScopeVoiceEnrollmentClient enrollmentClient;
    @Resource
    private TkDashScopeTtsClient ttsClient;
    @Resource
    private TkVoiceSampleProcessingService sampleProcessingService;
    @Resource
    private TkMimoTtsClient mimoTtsClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVoice(String name, Boolean consentConfirmed, MultipartFile file) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw exception(TK_VOICE_TENANT_REQUIRED);
        }
        validateUpload(consentConfirmed, file);
        String sampleUrl;
        try {
            TkVoiceProcessedSample sample = sampleProcessingService.process(file);
            sampleUrl = fileApi.createFile(sample.getContent(), sample.getFilename(),
                    StrUtil.format("tk/{}/voice-profiles", scope.getTenantId()), sample.getContentType());
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(TK_VOICE_UPLOAD_FAILED, ex.getMessage());
        }
        TkVoiceProfileDO profile = TkVoiceProfileDO.builder()
                .name(StrUtil.blankToDefault(name, "自定义音色"))
                .provider(TkApiKeyProviderEnum.DASHSCOPE.getProvider())
                .model("cosyvoice-v3.5-plus")
                .sampleFileUrl(sampleUrl)
                .sourceType(TkVoiceProfileSourceType.DASHSCOPE_CLONE)
                .status(TkVoiceProfileStatusEnum.CLONING.getStatus())
                .enabled(true)
                .language("auto")
                .consentConfirmed(true)
                .consentOperator(SecurityFrameworkUtils.getLoginUserId())
                .consentTime(LocalDateTime.now())
                .build();
        profile.setTenantId(scope.getTenantId());
        voiceProfileMapper.insert(profile);
        cloneVoice(profile);
        return profile.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMimoDesignVoice(String name, String prompt, String tags) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw exception(TK_VOICE_TENANT_REQUIRED);
        }
        if (StrUtil.isBlank(prompt)) {
            throw exception(TK_VOICE_SELECTION_INVALID);
        }
        TkVoiceProfileDO profile = TkVoiceProfileDO.builder()
                .name(StrUtil.blankToDefault(name, "MiMo voice design"))
                .provider(TkTtsProviderEnum.MIMO)
                .model("mimo-v2.5-tts-voicedesign")
                .sourceType(TkVoiceProfileSourceType.MIMO_DESIGN)
                .mimoVoiceMode(TkMimoVoiceModeEnum.VOICE_DESIGN)
                .mimoVoicePrompt(StrUtil.trimToEmpty(prompt))
                .tags(normalizeTags(tags))
                .status(TkVoiceProfileStatusEnum.READY.getStatus())
                .enabled(true)
                .language("auto")
                .consentConfirmed(true)
                .consentOperator(SecurityFrameworkUtils.getLoginUserId())
                .consentTime(LocalDateTime.now())
                .build();
        profile.setTenantId(scope.getTenantId());
        createMimoPreview(profile);
        voiceProfileMapper.insert(profile);
        return profile.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMimoCloneVoice(String name, Boolean consentConfirmed, String sampleUrl, String tags) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw exception(TK_VOICE_TENANT_REQUIRED);
        }
        if (!Boolean.TRUE.equals(consentConfirmed)) {
            throw exception(TK_VOICE_CONSENT_REQUIRED);
        }
        if (StrUtil.isBlank(sampleUrl)) {
            throw exception(TK_VOICE_FILE_EMPTY);
        }
        TkVoiceProfileDO profile = TkVoiceProfileDO.builder()
                .name(StrUtil.blankToDefault(name, "MiMo voice clone"))
                .provider(TkTtsProviderEnum.MIMO)
                .model("mimo-v2.5-tts-voiceclone")
                .sourceType(TkVoiceProfileSourceType.MIMO_CLONE)
                .mimoVoiceMode(TkMimoVoiceModeEnum.VOICE_CLONE)
                .mimoSampleUrl(StrUtil.trimToEmpty(sampleUrl))
                .sampleFileUrl(StrUtil.trimToEmpty(sampleUrl))
                .tags(normalizeTags(tags))
                .status(TkVoiceProfileStatusEnum.READY.getStatus())
                .enabled(true)
                .language("auto")
                .consentConfirmed(true)
                .consentOperator(SecurityFrameworkUtils.getLoginUserId())
                .consentTime(LocalDateTime.now())
                .build();
        profile.setTenantId(scope.getTenantId());
        createMimoPreview(profile);
        voiceProfileMapper.insert(profile);
        return profile.getId();
    }

    @Override
    public void retryVoice(Long id) {
        TkVoiceProfileDO profile = validateWritable(id);
        profile.setStatus(TkVoiceProfileStatusEnum.CLONING.getStatus());
        profile.setErrorMessage(null);
        voiceProfileMapper.updateById(profile);
        cloneVoice(profile);
    }

    @Override
    public List<TkVoiceProfileDO> getVoiceList() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw exception(TK_VOICE_TENANT_REQUIRED);
        }
        List<TkVoiceProfileDO> profiles = voiceProfileMapper.selectListByTenant(scope.getTenantId());
        profiles.forEach(this::fillDefaults);
        return profiles;
    }

    @Override
    public TkVoiceProfileDO getVoice(Long id) {
        TkVoiceProfileDO profile = requireVoice(id);
        dataScopeService.validateReadable(profile.getTenantId(), null, null);
        return profile;
    }

    @Override
    public void updateEnabled(Long id, Boolean enabled) {
        TkVoiceProfileDO profile = validateWritable(id);
        if (!TkVoiceProfileStatusEnum.READY.getStatus().equals(profile.getStatus()) && Boolean.TRUE.equals(enabled)) {
            throw exception(TK_VOICE_NOT_READY);
        }
        profile.setEnabled(Boolean.TRUE.equals(enabled));
        voiceProfileMapper.updateById(profile);
    }

    @Override
    public void batchUpdateEnabled(List<Long> ids, Boolean enabled) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.stream().filter(id -> id != null && id > 0).forEach(id -> updateEnabled(id, enabled));
    }

    @Override
    public void updateTags(Long id, String tags) {
        TkVoiceProfileDO profile = validateWritable(id);
        profile.setTags(normalizeTags(tags));
        voiceProfileMapper.updateById(profile);
    }

    @Override
    public void deleteVoice(Long id) {
        TkVoiceProfileDO profile = validateWritable(id);
        fillDefaults(profile);
        if (TkTtsProviderEnum.DASHSCOPE.equalsIgnoreCase(profile.getProvider())) {
            enrollmentClient.deleteVoice(profile.getVoiceCode());
            fileApi.deleteFileByUrl(profile.getSampleFileUrl());
        }
        fileApi.deleteFileByUrl(profile.getPreviewFileUrl());
        voiceProfileMapper.deleteById(id);
    }

    @Override
    public void batchDeleteVoice(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.stream().filter(id -> id != null && id > 0).forEach(this::deleteVoice);
    }

    @Override
    public String resolveReadyVoiceCode(Long id) {
        TkVoiceProfileDO profile = getVoice(id);
        if (!TkVoiceProfileStatusEnum.READY.getStatus().equals(profile.getStatus())
                || !Boolean.TRUE.equals(profile.getEnabled()) || StrUtil.isBlank(profile.getVoiceCode())) {
            throw exception(TK_VOICE_NOT_READY);
        }
        profile.setLastUsedTime(LocalDateTime.now());
        voiceProfileMapper.updateById(profile);
        return profile.getVoiceCode();
    }

    @Override
    public String resolveVoiceSelection(Long profileId, String systemVoiceCode) {
        if (profileId != null) {
            return resolveReadyVoiceCode(profileId);
        }
        String resolvedSystemVoiceCode = StrUtil.blankToDefault(systemVoiceCode, DEFAULT_SYSTEM_VOICE_CODE);
        if (!SYSTEM_VOICE_CODES.contains(resolvedSystemVoiceCode)) {
            throw exception(TK_VOICE_SELECTION_INVALID);
        }
        return resolvedSystemVoiceCode;
    }

    @Override
    public TkMimoVoiceSelection resolveMimoVoiceSelection(Long profileId, String mode, String code,
                                                          String prompt, String sampleUrl) {
        if (profileId == null) {
            return new TkMimoVoiceSelection(TkMimoVoiceModeEnum.normalize(mode),
                    StrUtil.trimToEmpty(code), StrUtil.trimToEmpty(prompt), StrUtil.trimToEmpty(sampleUrl));
        }
        TkVoiceProfileDO profile = getVoice(profileId);
        fillDefaults(profile);
        if (!TkTtsProviderEnum.MIMO.equalsIgnoreCase(profile.getProvider())
                || !TkVoiceProfileStatusEnum.READY.getStatus().equals(profile.getStatus())
                || !Boolean.TRUE.equals(profile.getEnabled())) {
            throw exception(TK_VOICE_NOT_READY);
        }
        profile.setLastUsedTime(LocalDateTime.now());
        voiceProfileMapper.updateById(profile);
        String resolvedMode = TkMimoVoiceModeEnum.normalize(profile.getMimoVoiceMode());
        return new TkMimoVoiceSelection(resolvedMode, profile.getVoiceCode(),
                StrUtil.trimToEmpty(profile.getMimoVoicePrompt()),
                StrUtil.blankToDefault(profile.getMimoSampleUrl(), profile.getSampleFileUrl()));
    }

    private void cloneVoice(TkVoiceProfileDO profile) {
        try {
            String sampleAccessUrl = resolveSampleAccessUrl(profile.getSampleFileUrl());
            String voiceCode = enrollmentClient.createVoice(sampleAccessUrl, "tk" + profile.getTenantId());
            byte[] preview = ttsClient.synthesize(PREVIEW_TEXT, voiceCode, "zh-cn");
            String format = StrUtil.blankToDefault(ttsClient.getAudioFormat(), "mp3");
            String previewUrl = fileApi.createFile(preview, "preview." + format,
                    StrUtil.format("tk/{}/voice-profiles", profile.getTenantId()), "audio/" + format);
            profile.setVoiceCode(voiceCode);
            profile.setPreviewFileUrl(previewUrl);
            profile.setStatus(TkVoiceProfileStatusEnum.READY.getStatus());
            profile.setEnabled(true);
            profile.setErrorMessage(null);
        } catch (Exception ex) {
            profile.setStatus(TkVoiceProfileStatusEnum.FAILED.getStatus());
            profile.setEnabled(false);
            profile.setErrorMessage(StrUtil.maxLength(ex.getMessage(), 1000));
        }
        voiceProfileMapper.updateById(profile);
    }

    private void createMimoPreview(TkVoiceProfileDO profile) {
        byte[] preview = mimoTtsClient.synthesize(TkVoiceSynthesisRequest.builder()
                .text("This is a MiMo voice preview for short videos.")
                .targetLanguage(profile.getLanguage())
                .mimoVoiceMode(profile.getMimoVoiceMode())
                .mimoVoicePrompt(profile.getMimoVoicePrompt())
                .mimoVoiceSampleUrl(StrUtil.blankToDefault(profile.getMimoSampleUrl(), profile.getSampleFileUrl()))
                .build());
        String format = StrUtil.blankToDefault(mimoTtsClient.audioFormat(), "wav");
        String contentType = StrUtil.equalsIgnoreCase(format, "mp3") ? "audio/mpeg" : "audio/" + format;
        String previewUrl = fileApi.createFile(preview, "preview." + format,
                StrUtil.format("tk/{}/voice-profiles", profile.getTenantId()), contentType);
        profile.setPreviewFileUrl(previewUrl);
    }

    private void fillDefaults(TkVoiceProfileDO profile) {
        if (profile != null && StrUtil.isBlank(profile.getSourceType())) {
            profile.setSourceType(TkVoiceProfileSourceType.DASHSCOPE_CLONE);
        }
    }

    private String normalizeTags(String tags) {
        return StrUtil.maxLength(StrUtil.trimToEmpty(tags), 255);
    }

    private String resolveSampleAccessUrl(String sampleFileUrl) {
        try {
            return StrUtil.blankToDefault(fileApi.presignGetUrl(sampleFileUrl, 1800), sampleFileUrl);
        } catch (UnsupportedOperationException ex) {
            return sampleFileUrl;
        }
    }

    private void validateUpload(Boolean consentConfirmed, MultipartFile file) {
        if (!Boolean.TRUE.equals(consentConfirmed)) {
            throw exception(TK_VOICE_CONSENT_REQUIRED);
        }
        if (file == null || file.isEmpty()) {
            throw exception(TK_VOICE_FILE_EMPTY);
        }
        String extension = FileUtil.extName(StrUtil.blankToDefault(file.getOriginalFilename(), ""))
                .toLowerCase(Locale.ROOT);
        if (ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            if (file.getSize() > MAX_AUDIO_FILE_SIZE) {
                throw exception(TK_VOICE_FILE_TOO_LARGE);
            }
            return;
        }
        if (ALLOWED_VIDEO_EXTENSIONS.contains(extension)) {
            if (file.getSize() > MAX_VIDEO_FILE_SIZE) {
                throw exception(TK_VOICE_VIDEO_FILE_TOO_LARGE);
            }
            return;
        }
        if (!ALLOWED_AUDIO_EXTENSIONS.contains(extension)) {
            throw exception(TK_VOICE_FILE_INVALID);
        }
    }

    private TkVoiceProfileDO validateWritable(Long id) {
        TkVoiceProfileDO profile = requireVoice(id);
        dataScopeService.validateWritable(profile.getTenantId(), null);
        return profile;
    }

    private TkVoiceProfileDO requireVoice(Long id) {
        TkVoiceProfileDO profile = voiceProfileMapper.selectById(id);
        if (profile == null) {
            throw exception(TK_VOICE_NOT_EXISTS);
        }
        return profile;
    }

}
