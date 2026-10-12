package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 手动文案翻译 Request VO")
@Data
public class TkManualScriptTranslationReqVO {

    @Schema(description = "待翻译原文，去除首尾空白后 1 至 3000 字符", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请输入需要翻译的文案")
    @ToString.Exclude
    private String sourceText;

    @Schema(description = "目标语言：zh-cn/en/en-us/de/es/fr/nl", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择目标语言")
    private String targetLanguage;
}
