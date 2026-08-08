package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 智能生成任务 Response VO")
@Data
public class TkGenerationTaskRespVO {

    private Long id;
    private Long tenantId;
    private String creator;
    private String creatorName;
    private Integer dailyUserVideoNo;
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
    private String precheckResult;
    private Integer retryCount;
    private LocalDateTime lastRetryTime;
    private String workerId;
    private LocalDateTime heartbeatTime;
    private LocalDateTime stepStartedAt;
    private LocalDateTime stepFinishedAt;
    private String title;
    private Long latestPublishDetailId;
    private String latestPublishAccountName;
    private String latestPublishUrl;
    private LocalDateTime latestPublishUrlRegisteredTime;
    private LocalDateTime createTime;

}
