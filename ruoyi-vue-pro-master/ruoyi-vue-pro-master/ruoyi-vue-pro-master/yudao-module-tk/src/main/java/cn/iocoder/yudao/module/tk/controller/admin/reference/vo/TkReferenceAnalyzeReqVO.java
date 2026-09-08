package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TK 对标分析 Request VO")
@Data
public class TkReferenceAnalyzeReqVO {

    private Long companyId;

    @Schema(description = "用户自定义分析任务名称", example = "夏季防晒视频")
    @javax.validation.constraints.Size(max = 128, message = "任务名称不能超过128个字符")
    private String title;

    @NotBlank(message = "TikTok 对标链接不能为空")
    private String sourceUrl;

    @NotNull(message = "素材库不能为空")
    private Long libraryId;

    private Integer referenceDuration;

    private String targetLanguage;

    private String materialPurpose;

    private String analysisProvider;

    private Boolean forceRefresh;

}
