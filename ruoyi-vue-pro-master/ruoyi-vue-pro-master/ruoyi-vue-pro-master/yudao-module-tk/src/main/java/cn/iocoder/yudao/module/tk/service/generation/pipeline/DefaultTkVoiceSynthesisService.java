package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class DefaultTkVoiceSynthesisService implements TkVoiceSynthesisService {

    @Resource
    private FileApi fileApi;
    @Resource
    private TkVoiceProviderRouter voiceProviderRouter;

    @Override
    public TkAudioAsset synthesize(TkGenerationTaskDO task, String scriptText) {
        String directory = StrUtil.format("tk/{}/{}/generation-tasks/{}", task.getTenantId(), task.getCompanyId(), task.getId());
        String provider = TkTtsProviderEnum.normalize(task.getTtsProvider());
        TkVoiceTtsClient client = voiceProviderRouter.resolve(provider);
        String format = StrUtil.blankToDefault(client.audioFormat(), "mp3");
        byte[] audioBytes = client.synthesize(TkVoiceSynthesisRequest.builder()
                .text(scriptText)
                .voiceCode(task.getVoiceCode())
                .targetLanguage(task.getTargetLanguage())
                .mimoVoiceMode(task.getMimoVoiceMode())
                .mimoVoiceCode(task.getMimoVoiceCode())
                .mimoVoicePrompt(task.getMimoVoicePrompt())
                .mimoVoiceSampleUrl(task.getMimoVoiceSampleUrl())
                .build());
        String audioUrl = fileApi.createFile(audioBytes,
                StrUtil.format("voice-{}.{}", task.getId(), format), directory, "audio/" + format);
        return new TkAudioAsset(audioUrl, null);
    }

}
