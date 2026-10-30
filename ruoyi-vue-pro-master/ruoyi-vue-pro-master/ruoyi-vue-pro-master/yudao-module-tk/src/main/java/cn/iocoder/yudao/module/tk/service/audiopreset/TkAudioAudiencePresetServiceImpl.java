package cn.iocoder.yudao.module.tk.service.audiopreset;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetSaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkAudioAudiencePresetMapper;
import cn.iocoder.yudao.module.tk.enums.TkAudioAudienceEnum;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoVoiceModeEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.Resource;

@Service
public class TkAudioAudiencePresetServiceImpl implements TkAudioAudiencePresetService {
    @Resource private TkAudioAudiencePresetMapper presetMapper;
    @Resource private TkDataScopeService dataScopeService;
    @Resource private TkVoiceProfileService voiceProfileService;
    @Resource private TkMiniMaxVoiceDictionaryService miniMaxVoiceDictionaryService;
    @Resource private TkBgmAssetService bgmAssetService;

    @Override
    public List<TkAudioAudiencePresetRespVO> getList() {
        TkUserScope scope = scope();
        return presetMapper
                .selectOwned(scope.getTenantId(), company(scope.getCompanyId()), scope.getUserId())
                .stream()
                .map(this::response)
                .collect(Collectors.toList());
    }

    @Override
    public TkAudioAudiencePresetRespVO get(Long id) {
        return response(owned(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(TkAudioAudiencePresetSaveReqVO req) {
        TkUserScope scope = scope();
        TkAudioAudienceEnum.validate(req.getAudienceCode());
        TkAudioAudiencePresetDO previous = req.getId() == null ? null : owned(req.getId());
        if (previous != null) {
            version(previous, req.getVersion());
            if (!Objects.equals(previous.getAudienceCode(), req.getAudienceCode()))
                throw new IllegalArgumentException("不能修改预设所属人群");
        }
        TkAudioAudiencePresetDO row = new TkAudioAudiencePresetDO();
        row.setTenantId(scope.getTenantId());
        row.setCompanyId(company(scope.getCompanyId()));
        row.setUserId(scope.getUserId());
        row.setAudienceCode(req.getAudienceCode());
        row.setTtsProvider(TkTtsProviderEnum.forNewTask(req.getTtsProvider()));
        row.setVoiceConfig(JsonUtils.toJsonString(validateVoice(req, row.getTtsProvider())));
        row.setBgmEnabled(Boolean.TRUE.equals(req.getBgmEnabled()));
        double volume = req.getBgmVolume() == null ? 0.2 : req.getBgmVolume();
        if (!Double.isFinite(volume) || volume < 0 || volume > 1)
            throw new IllegalArgumentException("BGM 音量必须为 0–1");
        row.setBgmVolume(volume);
        if (Boolean.TRUE.equals(row.getBgmEnabled())) {
            if (req.getBgmAssetId() == null) throw new IllegalArgumentException("请选择背景音乐");
            bgmAssetService.validateReadableForUpdate(req.getBgmAssetId());
            row.setBgmAssetId(req.getBgmAssetId());
        }
        row.setConfigStatus("READY");
        if (previous == null) {
            row.setVersion(1);
            List<TkAudioAudiencePresetDO> slots =
                    presetMapper.selectOwned(
                            scope.getTenantId(), company(scope.getCompanyId()), scope.getUserId());
            if (slots.stream()
                    .anyMatch(
                            item -> Objects.equals(item.getAudienceCode(), req.getAudienceCode())))
                throw new IllegalArgumentException("该人群已有预设，请刷新后编辑");
            presetMapper.insert(row);
        } else {
            row.setId(previous.getId());
            row.setVersion(previous.getVersion() + 1);
            UpdateWrapper<TkAudioAudiencePresetDO> update =
                    guard(previous).set("bgm_asset_id", row.getBgmAssetId());
            if (presetMapper.update(row, update) != 1)
                throw new IllegalArgumentException("预设已更新，请刷新重试");
        }
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset(Long id, Integer expected) {
        TkAudioAudiencePresetDO row = owned(id);
        version(row, expected);
        UpdateWrapper<TkAudioAudiencePresetDO> update =
                guard(row)
                        .set("voice_config", "{}")
                        .set("tts_provider", null)
                        .set("bgm_asset_id", null)
                        .set("bgm_enabled", false)
                        .set("bgm_volume", 0.2)
                        .set("config_status", "RESET")
                        .set("version", row.getVersion() + 1);
        if (presetMapper.update(null, update) != 1)
            throw new IllegalArgumentException("预设已更新，请刷新重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TkAudioAudiencePresetDO resolveForGeneration(
            Long id, Integer expected, Long requestedCompany) {
        TkAudioAudiencePresetDO row = owned(id);
        version(row, expected);
        if (!Objects.equals(row.getCompanyId(), company(requestedCompany)))
            throw new IllegalArgumentException("预设公司范围不匹配");
        if (!"READY".equals(row.getConfigStatus())) throw new IllegalArgumentException("预设尚未完整配置");
        TkAudioAudiencePresetSaveReqVO req =
                JsonUtils.parseObject(row.getVoiceConfig(), TkAudioAudiencePresetSaveReqVO.class);
        validateVoice(req, row.getTtsProvider());
        if (Boolean.TRUE.equals(row.getBgmEnabled())) {
            if (row.getBgmAssetId() == null) throw new IllegalArgumentException("预设背景音乐不可用");
            bgmAssetService.validateReadableForUpdate(row.getBgmAssetId());
        }
        return row;
    }

    private TkUserScope scope() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.hasTenantScope() || scope.getUserId() == null)
            throw new IllegalArgumentException("当前账号缺少个人预设范围");
        return scope;
    }

    private static Long company(Long id) {
        return id == null ? 0L : id;
    }

    private TkAudioAudiencePresetDO owned(Long id) {
        TkUserScope scope = scope();
        TkAudioAudiencePresetDO row = id == null ? null : presetMapper.selectById(id);
        if (row == null
                || !Objects.equals(scope.getTenantId(), row.getTenantId())
                || !Objects.equals(company(scope.getCompanyId()), row.getCompanyId())
                || !Objects.equals(scope.getUserId(), row.getUserId()))
            throw new IllegalArgumentException("预设不存在或无权访问");
        return row;
    }

    private void version(TkAudioAudiencePresetDO row, Integer expected) {
        if (expected == null || !Objects.equals(row.getVersion(), expected))
            throw new IllegalArgumentException("预设已更新，请刷新重试");
    }

    private UpdateWrapper<TkAudioAudiencePresetDO> guard(TkAudioAudiencePresetDO row) {
        return new UpdateWrapper<TkAudioAudiencePresetDO>()
                .eq("id", row.getId())
                .eq("tenant_id", row.getTenantId())
                .eq("company_id", row.getCompanyId())
                .eq("user_id", row.getUserId())
                .eq("version", row.getVersion());
    }

    private TkAudioAudiencePresetRespVO response(TkAudioAudiencePresetDO row) {
        TkAudioAudiencePresetRespVO result =
                JsonUtils.parseObject(row.getVoiceConfig(), TkAudioAudiencePresetRespVO.class);
        result.setId(row.getId());
        result.setVersion(row.getVersion());
        result.setAudienceCode(row.getAudienceCode());
        result.setTtsProvider(row.getTtsProvider());
        result.setBgmEnabled(row.getBgmEnabled());
        result.setBgmAssetId(row.getBgmAssetId());
        result.setBgmVolume(row.getBgmVolume());
        result.setConfigStatus(row.getConfigStatus());
        result.setCompanyId(row.getCompanyId());
        return result;
    }

    private Map<String, Object> validateVoice(TkAudioAudiencePresetSaveReqVO req, String provider) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ttsProvider", provider);
        config.put("voiceId", req.getVoiceId());
        config.put("voiceProfileId", req.getVoiceProfileId());
        String code = req.getVoiceCode();
        if (TkTtsProviderEnum.MINIMAX.equals(provider)) {
            code = miniMaxVoiceDictionaryService.resolveVoiceCode(req.getVoiceProfileId(), code);
        } else if (TkTtsProviderEnum.DASHSCOPE.equals(provider)) {
            code = voiceProfileService.resolveVoiceSelection(req.getVoiceProfileId(), code);
        } else {
            TkMimoVoiceSelection selection =
                    voiceProfileService.resolveMimoVoiceSelection(
                            req.getVoiceProfileId(),
                            req.getMimoVoiceMode(),
                            req.getMimoVoiceCode(),
                            req.getMimoVoicePrompt(),
                            req.getMimoVoiceSampleUrl());
            String mode = TkMimoVoiceModeEnum.normalize(selection.getMode());
            if (TkMimoVoiceModeEnum.PRESET.equals(mode) && StrUtil.isBlank(selection.getCode())
                    || TkMimoVoiceModeEnum.VOICE_DESIGN.equals(mode)
                            && StrUtil.isBlank(selection.getPrompt())
                    || TkMimoVoiceModeEnum.VOICE_CLONE.equals(mode)
                            && StrUtil.isBlank(selection.getSampleUrl()))
                throw new IllegalArgumentException("MiMo 音色配置不完整");
            config.put("mimoVoiceMode", mode);
            config.put("mimoVoiceCode", selection.getCode());
            config.put("mimoVoicePrompt", selection.getPrompt());
            config.put("mimoVoiceSampleUrl", selection.getSampleUrl());
        }
        config.put("voiceCode", code);
        return config;
    }
}
