package cn.iocoder.yudao.module.tk.service.audiopreset;
import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO;
import java.util.List;
public interface TkAudioAudiencePresetService {
 List<TkAudioAudiencePresetRespVO> getList();
 TkAudioAudiencePresetRespVO get(Long id);
 Long save(TkAudioAudiencePresetSaveReqVO req);
 void reset(Long id,Integer version);
 TkAudioAudiencePresetDO resolveForGeneration(Long id,Integer expectedVersion,Long companyId);
}
