package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - TK 智能生成任务创建 Request VO")
@Data
public class TkGenerationTaskCreateReqVO {

    private Long companyId;
    private String sourceUrl;

    @NotNull(message = "素材库不能为空")
    private Long libraryId;

    private Long productId;
    private Long templateId;
    private Long voiceId;
    private String ttsProvider;

    @JsonAlias({"voice_code", "video_id"})
    private String voiceCode;

    @Schema(description = "租户自定义音色编号；设置后由后端解析真实音色编码", example = "12")
    private Long voiceProfileId;
    private Boolean voiceEnabled;

    private String mimoVoiceMode;
    private String mimoVoiceCode;
    private String mimoVoicePrompt;
    private String mimoVoiceSampleUrl;

    @JsonAlias({"target_language", "language"})
    private String targetLanguage;
    private String materialPurpose;
    private String productCategoryCode;

    @JsonAlias({"clip_plan_mode", "videoGenerationMode", "video_generation_mode"})
    private String clipPlanMode;

    private Long referenceAnalysisId;
    private Long scriptOptionId;
    private List<Long> scriptOptionIds;
    private Integer videosPerScript;
    private String openingVideoUrl;
    private String openingVideoName;
    private Integer openingClipStartSecond;
    private Integer openingClipEndSecond;
    private Integer referenceDuration;
    private String segmentDurationConfig;
    private String promptText;
    private Boolean bgmEnabled;
    private Long bgmAssetId;
    private Double bgmVolume;
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

}
