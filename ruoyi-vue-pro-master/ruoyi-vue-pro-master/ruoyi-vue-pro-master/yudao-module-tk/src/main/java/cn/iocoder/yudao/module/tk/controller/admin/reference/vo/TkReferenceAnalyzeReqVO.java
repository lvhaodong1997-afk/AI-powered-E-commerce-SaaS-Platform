package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TK 对标分析 Request VO")
@Data
public class TkReferenceAnalyzeReqVO {

    private Long companyId;

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
