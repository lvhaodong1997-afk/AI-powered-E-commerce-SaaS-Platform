package cn.iocoder.yudao.module.tk.dal.dataobject;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("tk_audio_audience_preset")
@KeySequence("tk_audio_audience_preset_seq")
@Data
@EqualsAndHashCode(callSuper=true)
public class TkAudioAudiencePresetDO extends TenantBaseDO {
 @TableId private Long id;
 private Long companyId;
 private Long userId;
 private String audienceCode;
 private String ttsProvider;
 private String voiceConfig;
 private Boolean bgmEnabled;
 private Long bgmAssetId;
 private Double bgmVolume;
 private String configStatus;
 private Integer version;
}
