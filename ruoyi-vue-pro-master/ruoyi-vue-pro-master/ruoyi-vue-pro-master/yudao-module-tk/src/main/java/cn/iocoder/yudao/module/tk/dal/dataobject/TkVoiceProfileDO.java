package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_voice_profile")
@KeySequence("tk_voice_profile_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkVoiceProfileDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private String provider;
    private String model;
    private String voiceCode;
    private String sourceType;
    private String mimoVoiceMode;
    private String mimoVoicePrompt;
    private String mimoSampleUrl;
    private String tags;
    private Integer sort;
    private String remark;
    private String sampleFileUrl;
    private String previewFileUrl;
    private String status;
    private Boolean enabled;
    private String language;
    private Boolean consentConfirmed;
    private Long consentOperator;
    private LocalDateTime consentTime;
    private String providerRequestId;
    private String errorMessage;
    private LocalDateTime expireTime;
    private LocalDateTime lastUsedTime;

}
