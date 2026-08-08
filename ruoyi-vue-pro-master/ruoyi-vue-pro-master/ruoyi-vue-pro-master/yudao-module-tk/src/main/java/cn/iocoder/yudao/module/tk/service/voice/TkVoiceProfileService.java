package cn.iocoder.yudao.module.tk.service.voice;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TkVoiceProfileService {

    Long createVoice(String name, Boolean consentConfirmed, MultipartFile file);
    Long createMimoDesignVoice(String name, String prompt, String tags);
    Long createMimoCloneVoice(String name, Boolean consentConfirmed, String sampleUrl, String tags);
    void retryVoice(Long id);
    List<TkVoiceProfileDO> getVoiceList();
    TkVoiceProfileDO getVoice(Long id);
    void updateEnabled(Long id, Boolean enabled);
    void batchUpdateEnabled(List<Long> ids, Boolean enabled);
    void updateTags(Long id, String tags);
    void deleteVoice(Long id);
    void batchDeleteVoice(List<Long> ids);
    String resolveReadyVoiceCode(Long id);
    String resolveVoiceSelection(Long profileId, String systemVoiceCode);
    TkMimoVoiceSelection resolveMimoVoiceSelection(Long profileId, String mode, String code, String prompt, String sampleUrl);

}
