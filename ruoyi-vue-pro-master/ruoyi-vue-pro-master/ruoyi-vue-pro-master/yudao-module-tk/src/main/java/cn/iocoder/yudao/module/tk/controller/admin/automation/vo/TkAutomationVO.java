package cn.iocoder.yudao.module.tk.controller.admin.automation.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;
import javax.validation.constraints.*;

/** Stable, account-scoped contract for automated video creation. */
public final class TkAutomationVO {
    private TkAutomationVO() { }

    @Data
    @ToString(onlyExplicitlyIncluded = true)
    @Schema(description = "自动出片请求；scriptText 只能包含实际口播正文")
    public static class CreateReq {
        @NotNull @Positive private Long libraryId;
        @NotBlank @Size(max = 3000) private String scriptText;
        @Size(max = 128) private String title;
        @NotBlank @Pattern(regexp = "MINIMAX|DASHSCOPE|MIMO") private String ttsProvider;
        @Positive private Long voiceProfileId;
        private String voiceCode;
        @NotNull @Positive private Long bgmAssetId;
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") private Double bgmVolume = 0.4D;
        @NotNull @AssertFalse private Boolean translationEnabled = false;
        @NotNull @Pattern(regexp = "CN") private String targetRegion = "CN";
        @NotNull @Pattern(regexp = "FOLLOW_NARRATION") private String durationMode = "FOLLOW_NARRATION";
        @NotNull @Min(1080) @Max(1080) private Integer width = 1080;
        @NotNull @Min(1920) @Max(1920) private Integer height = 1920;
        @NotNull @Min(30) @Max(30) private Integer fps = 30;
        @NotNull private Boolean subtitleEnabled = true;
        @NotNull @Pattern(regexp = "extra_large") private String subtitleFontSize = "extra_large";
        @NotNull @Pattern(regexp = "classic_white") private String subtitleStyle = "classic_white";
        @NotNull @Pattern(regexp = "smart_safe") private String subtitlePositionMode = "smart_safe";
        @NotNull private Boolean subtitleKeywordEnabled = true;
        @NotNull private Boolean subtitleKaraokeEnabled = true;

        @JsonIgnore
        @AssertTrue(message = "voiceProfileId 或 voiceCode 必须提供一个")
        public boolean isVoiceSelected() {
            boolean hasCode = voiceCode != null && !voiceCode.trim().isEmpty();
            return (voiceProfileId != null) != hasCode;
        }

        @JsonIgnore
        @AssertTrue(message = "MiMo 必须选择音色列表返回的 voiceProfileId")
        public boolean isMimoProfileSelected() {
            return !"MIMO".equals(ttsProvider) || voiceProfileId != null;
        }
    }

    @Data
    public static class VoiceResp {
        private Long voiceProfileId;
        private String voiceCode;
        private String name;
        private String ttsProvider;
        private String language;
    }

    @Data
    public static class BgmResp {
        private Long id;
        private String name;
        private String fileName;
        private String sourceType;
    }

    @Data
    public static class TaskResp {
        private Long taskId;
        private String status;
        private Integer progress;
        private String currentStep;
        private String currentStepCode;
        private Boolean completed;
        private Boolean failed;
        private String failCode;
        private String failReason;
        private String subtitleKaraokeStatus;
        private String subtitleKaraokeReason;
        private String downloadUrl;
    }

    @Data
    public static class DownloadResp {
        private Long taskId;
        private String fileName;
        private String downloadUrl;
    }
}
