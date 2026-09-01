package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioExportTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkAudioExportTaskMapper;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoVoiceModeEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceProviderRouter;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceTtsClient;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class TkAudioExportTaskServiceImpl implements TkAudioExportTaskService {

    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Resource
    private TkAudioExportTaskMapper audioExportTaskMapper;
    @Resource
    private TkCreditService creditService;
    @Resource
    private FileApi fileApi;
    @Resource
    private TkVoiceProfileService voiceProfileService;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkVoiceProviderRouter voiceProviderRouter;

    @Override
    public TkAudioExportTaskRespVO export(TkAudioExportTaskCreateReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (scope == null || !scope.hasTenantScope()) {
            throw new IllegalStateException("当前账号缺少租户范围，不能生成音频");
        }
        String requestId = reqVO.getRequestId().trim();
        TkAudioExportTaskDO existing = audioExportTaskMapper.selectByRequestId(scope.getTenantId(), requestId);
        if (existing != null) {
            return BeanUtils.toBean(existing, TkAudioExportTaskRespVO.class);
        }

        Long companyId = resolveCompanyId(reqVO.getCompanyId(), scope);
        Long creditLogId = creditService.freezeForAudioExport(scope.getTenantId());
        TkAudioExportTaskDO task = null;
        try {
            task = createProcessingTask(reqVO, scope.getTenantId(), companyId, requestId, creditLogId);
            creditService.bindBusiness(creditLogId, task.getId());
            synthesizeAndStore(task);
            task.setStatus(STATUS_SUCCESS);
            task.setFailReason(null);
            audioExportTaskMapper.updateById(task);
            creditService.settleByLogId(creditLogId);
            return BeanUtils.toBean(task, TkAudioExportTaskRespVO.class);
        } catch (Exception ex) {
            if (task != null && task.getId() != null) {
                audioExportTaskMapper.updateById(new TkAudioExportTaskDO()
                        .setId(task.getId())
                        .setStatus(STATUS_FAILED)
                        .setFailReason(StrUtil.maxLength(ex.getMessage(), 2000)));
            }
            creditService.refundByLogId(creditLogId, ex.getMessage());
            throw ex;
        }
    }

    private TkAudioExportTaskDO createProcessingTask(TkAudioExportTaskCreateReqVO reqVO, Long tenantId,
                                                     Long companyId, String requestId, Long creditLogId) {
        String provider = TkTtsProviderEnum.normalize(reqVO.getTtsProvider());
        TkAudioExportTaskDO task = TkAudioExportTaskDO.builder()
                .requestId(requestId)
                .companyId(companyId)
                .scriptText(reqVO.getScriptText().trim())
                .ttsProvider(provider)
                .voiceProfileId(reqVO.getVoiceProfileId())
                .targetLanguage(StrUtil.blankToDefault(reqVO.getTargetLanguage(), "zh-cn"))
                .status(STATUS_PROCESSING)
                .creditLogId(creditLogId)
                .build();
        if (TkTtsProviderEnum.DASHSCOPE.equals(provider)) {
            task.setVoiceCode(voiceProfileService.resolveVoiceSelection(reqVO.getVoiceProfileId(), reqVO.getVoiceCode()));
        } else {
            TkMimoVoiceSelection selection = voiceProfileService.resolveMimoVoiceSelection(reqVO.getVoiceProfileId(),
                    reqVO.getMimoVoiceMode(), reqVO.getMimoVoiceCode(), reqVO.getMimoVoicePrompt(), reqVO.getMimoVoiceSampleUrl());
            task.setMimoVoiceMode(selection.getMode());
            task.setMimoVoiceCode(selection.getCode());
            task.setMimoVoicePrompt(selection.getPrompt());
            task.setMimoVoiceSampleUrl(selection.getSampleUrl());
        }
        task.setTenantId(tenantId);
        audioExportTaskMapper.insert(task);
        return task;
    }

    private void synthesizeAndStore(TkAudioExportTaskDO task) {
        TkVoiceTtsClient client = voiceProviderRouter.resolve(task.getTtsProvider());
        String format = StrUtil.blankToDefault(client.audioFormat(), "mp3");
        byte[] audioBytes = client.synthesize(TkVoiceSynthesisRequest.builder()
                .text(task.getScriptText())
                .voiceCode(task.getVoiceCode())
                .targetLanguage(task.getTargetLanguage())
                .mimoVoiceMode(StrUtil.blankToDefault(task.getMimoVoiceMode(), TkMimoVoiceModeEnum.PRESET))
                .mimoVoiceCode(task.getMimoVoiceCode())
                .mimoVoicePrompt(task.getMimoVoicePrompt())
                .mimoVoiceSampleUrl(task.getMimoVoiceSampleUrl())
                .finalSynthesis(true)
                .build());
        String companySegment = task.getCompanyId() == null ? "tenant" : String.valueOf(task.getCompanyId());
        String directory = StrUtil.format("tk/{}/{}/audio-exports/{}", task.getTenantId(), companySegment, task.getId());
        task.setAudioUrl(fileApi.createFile(audioBytes, StrUtil.format("audio-{}.{}", task.getId(), format),
                directory, "audio/" + format));
    }

    private Long resolveCompanyId(Long requestedCompanyId, TkUserScope scope) {
        if (requestedCompanyId == null) {
            return scope.getCompanyId();
        }
        dataScopeService.validateWritable(requestedCompanyId);
        return requestedCompanyId;
    }
}
