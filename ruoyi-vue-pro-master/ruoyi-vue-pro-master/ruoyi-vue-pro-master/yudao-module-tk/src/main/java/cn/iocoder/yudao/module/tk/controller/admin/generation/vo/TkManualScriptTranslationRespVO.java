package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 手动文案翻译 Response VO")
@Data
public class TkManualScriptTranslationRespVO {

    @Schema(description = "原始文案")
    private String sourceText;

    @Schema(description = "目标语言")
    private String targetLanguage;

    @Schema(description = "译文")
    private String translatedText;
}
