package cn.iocoder.yudao.module.tk.dal.dataobject;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("tk_generation_task")
@KeySequence("tk_generation_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TkGenerationTaskDO extends TenantBaseDO {

    @TableId
    private Long id;

    private String businessTraceId;

    private Long batchId;

    private Integer scriptIndex;

    private Integer videoIndex;

    private Long companyId;

    private String sourceUrl;

    private Long productId;

    private Long libraryId;

    private Long templateId;

    private Long voiceId;

    private String ttsProvider;

    private String voiceCode;

    private Long voiceProfileId;

    private Boolean voiceEnabled;

    private String mimoVoiceMode;

    private String mimoVoiceCode;

    private String mimoVoicePrompt;

    private String mimoVoiceSampleUrl;

    private String targetLanguage;

    private String materialPurpose;

    private String productCategoryCode;

    private String generationRouteCode;

    private String generationRouteConfig;

    private Long referenceAnalysisId;

    private Long scriptOptionId;

    private String openingVideoUrl;

    private String openingVideoName;

    private Integer openingClipStartSecond;

    private Integer openingClipEndSecond;

    private Integer referenceDuration;

    private Integer targetDuration;

    private Integer clipSeconds;

    private String segmentDurationConfig;

    private String promptText;

    private String scriptText;

    private String segmentTimeline;

    private String audioUrl;

    private Boolean bgmEnabled;

    private Long bgmAssetId;

    private String bgmSourceType;

    private String bgmUrl;

    private Double bgmVolume;

    private String subtitleUrl;

    private Boolean subtitleEnabled;

    private String subtitleStyle;

    private String subtitlePositionMode;

    private Boolean subtitleKeywordEnabled;

    private String subtitleKeywords;

    private String subtitleKeywordMode;

    private Boolean subtitleKaraokeEnabled;

    private String subtitleActiveColor;

    private String subtitleKeywordColor;

    private String subtitleFontSize;

    private String subtitleTimelineUrl;

    private String subtitleVisualAnalysisUrl;

    private String subtitleLayoutUrl;

    private String subtitleAssUrl;

    private String clipPlan;

    private String status;

    private Integer progress;

    private String outputUrl;

    private String failReason;

    private String failCode;

    private String currentStep;

    private String currentStepCode;

    private Integer currentStepCompleted;

    private Integer currentStepTotal;

    private String precheckResult;

    private Integer retryCount;

    private LocalDateTime lastRetryTime;

    private String workerId;

    private LocalDateTime heartbeatTime;

    private String leaseToken;

    private LocalDateTime leaseExpireTime;

    private LocalDateTime stepStartedAt;

    private LocalDateTime stepFinishedAt;

    private String title;

}
