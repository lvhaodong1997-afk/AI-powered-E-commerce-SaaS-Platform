package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@TableName("tk_open_video_transcript_task")
@KeySequence("tk_open_video_transcript_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkOpenVideoTranscriptTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    private Long companyId;

    private String sourceUrl;
    private String sourceUrlHash;
    private String targetLanguage;
    private String resolvedVideoUrl;
    private String coverUrl;
    private Integer videoDuration;
    private String resolution;
    private String audioUrl;
    private Double audioDuration;
    private String status;
    private String failReason;
    private String transcriptText;
    private String segmentsJson;
    private String wordsJson;
    private String asrProvider;
    private String asrModel;
    private String rawAsrResult;

}
