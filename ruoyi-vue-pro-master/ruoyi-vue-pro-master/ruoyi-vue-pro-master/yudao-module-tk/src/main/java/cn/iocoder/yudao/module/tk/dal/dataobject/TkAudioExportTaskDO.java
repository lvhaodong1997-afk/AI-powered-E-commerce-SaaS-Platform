package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@TableName("tk_audio_export_task")
@KeySequence("tk_audio_export_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkAudioExportTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String requestId;
    private Long companyId;
    private String scriptText;
    private String ttsProvider;
    private String voiceCode;
    private Long voiceProfileId;
    private String mimoVoiceMode;
    private String mimoVoiceCode;
    private String mimoVoicePrompt;
    private String mimoVoiceSampleUrl;
    private String targetLanguage;
    private String audioUrl;
    private String status;
    private String failReason;
    private Long creditLogId;
}
