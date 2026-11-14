package cn.iocoder.yudao.module.tk.service.automation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.automation.vo.TkAutomationVO;
import cn.iocoder.yudao.module.tk.controller.admin.credit.vo.TkCreditBalanceRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TkAutomationService {
    @Resource private TkCreditService creditService;
    @Resource private TkMaterialLibraryService materialLibraryService;
    @Resource private TkBgmAssetService bgmAssetService;
    @Resource private TkVoiceProfileService voiceProfileService;
    @Resource private TkMiniMaxVoiceDictionaryService miniMaxVoiceDictionaryService;
    @Resource private TkGenerationTaskService generationTaskService;
    @Resource private TkGenerationOutputStorageService outputStorageService;

    public TkCreditBalanceRespVO credits() {
        return creditService.getCurrentTenantBalance();
    }

    public PageResult<TkMaterialLibraryRespVO> libraries(TkMaterialLibraryPageReqVO request) {
        return BeanUtils.toBean(materialLibraryService.getMaterialLibraryPage(request), TkMaterialLibraryRespVO.class);
    }

    public List<TkAutomationVO.VoiceResp> voices() {
        List<TkAutomationVO.VoiceResp> result = new ArrayList<>();
        for (TkVoiceProfileDO profile : voiceProfileService.getVoiceList()) {
            if (!"READY".equals(profile.getStatus()) || !Boolean.TRUE.equals(profile.getEnabled())) continue;
            TkAutomationVO.VoiceResp voice = new TkAutomationVO.VoiceResp();
            voice.setVoiceProfileId(profile.getId());
            voice.setName(profile.getName());
            voice.setTtsProvider(profile.getProvider());
            voice.setLanguage(profile.getLanguage());
            result.add(voice);
        }
        miniMaxVoiceDictionaryService.getOptions().stream().filter(item -> !Boolean.TRUE.equals(item.getUnavailable()))
                .forEach(item -> {
                    TkAutomationVO.VoiceResp voice = new TkAutomationVO.VoiceResp();
                    voice.setVoiceCode(item.getValue());
                    voice.setName(item.getLabel());
                    voice.setTtsProvider("MINIMAX");
                    voice.setLanguage(item.getLanguage());
                    result.add(voice);
                });
        return result;
    }

    public List<TkAutomationVO.BgmResp> bgms() {
        return bgmAssetService.getAvailableList().stream().filter(bgmAssetService::canDelete)
                .map(asset -> {
                    TkAutomationVO.BgmResp result = new TkAutomationVO.BgmResp();
                    result.setId(asset.getId());
                    result.setName(asset.getName());
                    result.setSourceType(asset.getSourceType());
                    result.setFileName(fileName(asset.getFileUrl(), asset.getName()));
                    return result;
                }).collect(Collectors.toList());
    }

    public TkAutomationVO.TaskResp create(TkAutomationVO.CreateReq request) {
        TkBgmAssetDO bgm = bgmAssetService.validateReadable(request.getBgmAssetId());
        if (bgm == null || !bgmAssetService.canDelete(bgm)) {
            throw new ServiceException(400, "只能选择当前账号自己上传的可用 BGM");
        }
        TkGenerationTaskCreateReqVO task = new TkGenerationTaskCreateReqVO();
        task.setLibraryId(request.getLibraryId());
        task.setTitle(request.getTitle());
        task.setMaterialPurpose("ECOMMERCE");
        task.setScriptMode("MANUAL");
        task.setPromptText(request.getScriptText());
        task.setManualOriginalText(request.getScriptText());
        task.setManualTranslationEnabled(false);
        task.setTargetLanguage("zh-cn");
        task.setDurationMode("FOLLOW_NARRATION");
        task.setClipPlanMode("FULL_POOL_RANDOM");
        task.setVoiceEnabled(true);
        task.setTtsProvider(request.getTtsProvider());
        task.setVoiceProfileId(request.getVoiceProfileId());
        task.setVoiceCode(request.getVoiceCode());
        task.setBgmEnabled(true);
        task.setBgmAssetId(request.getBgmAssetId());
        task.setBgmVolume(request.getBgmVolume());
        task.setAudioMixVersion(2);
        task.setSubtitleEnabled(request.getSubtitleEnabled());
        task.setSubtitleFontSize(request.getSubtitleFontSize());
        task.setSubtitleStyle(request.getSubtitleStyle());
        task.setSubtitlePositionMode(request.getSubtitlePositionMode());
        task.setSubtitleKeywordEnabled(request.getSubtitleKeywordEnabled());
        task.setSubtitleKeywordMode("auto_manual");
        task.setSubtitleKaraokeEnabled(request.getSubtitleKaraokeEnabled());
        // The existing service owns tenant/creator checks, credit freeze/refund and async submission.
        Long id = generationTaskService.createGenerationTask(task);
        TkAutomationVO.TaskResp result = new TkAutomationVO.TaskResp();
        result.setTaskId(id);
        result.setStatus("PENDING");
        result.setCurrentStep("PENDING");
        result.setCurrentStepCode("PENDING");
        result.setProgress(0);
        result.setCompleted(false);
        result.setFailed(false);
        return result;
    }

    public TkAutomationVO.TaskResp task(Long id) {
        TkGenerationTaskDO task = generationTaskService.getGenerationTask(id);
        TkAutomationVO.TaskResp result = BeanUtils.toBean(task, TkAutomationVO.TaskResp.class);
        result.setTaskId(id);
        result.setCompleted("SUCCESS".equals(task.getStatus()));
        result.setFailed("FAILED".equals(task.getStatus()));
        if (result.getCompleted() && StrUtil.isNotBlank(task.getOutputUrl())) {
            result.setDownloadUrl(outputStorageService.refreshGeneratedAssetReadUrl(task, task.getOutputUrl()));
        }
        return result;
    }

    public TkAutomationVO.DownloadResp download(Long id, String requestedFileName) {
        String name = StrUtil.blankToDefault(requestedFileName, "video-" + id + ".mp4");
        if (name.length() > 128 || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4")
                || name.chars().anyMatch(character -> character < 32 || character == 127
                    || "/\\:\"<>|?*".indexOf(character) >= 0)) {
            throw new ServiceException(400, "文件名必须是不含路径及控制字符的 MP4 文件名，最长128字符");
        }
        TkGenerationTaskDO task = generationTaskService.getGenerationTask(id);
        if (!"SUCCESS".equals(task.getStatus()) || StrUtil.isBlank(task.getOutputUrl())) {
            throw new ServiceException(409, "任务尚未成功完成，暂无可下载成片");
        }
        TkAutomationVO.DownloadResp result = new TkAutomationVO.DownloadResp();
        result.setTaskId(id);
        result.setFileName(name);
        result.setDownloadUrl(outputStorageService.refreshGeneratedAssetReadUrl(task, task.getOutputUrl(), name));
        return result;
    }

    private String fileName(String url, String fallback) {
        try {
            String path = URI.create(url).getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
