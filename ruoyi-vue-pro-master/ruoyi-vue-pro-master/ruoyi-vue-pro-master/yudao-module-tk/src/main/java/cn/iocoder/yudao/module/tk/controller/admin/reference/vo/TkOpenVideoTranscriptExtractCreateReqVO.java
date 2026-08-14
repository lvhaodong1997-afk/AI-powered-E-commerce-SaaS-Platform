package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - TK Open 视频文案时间轴提取创建 Request VO")
@Data
public class TkOpenVideoTranscriptExtractCreateReqVO {

    @NotBlank(message = "视频链接不能为空")
    @Size(max = 2048, message = "视频链接长度不能超过 2048 个字符")
    @Pattern(regexp = "^https?://.+", message = "视频链接必须以 http:// 或 https:// 开头")
    private String sourceUrl;

    private String targetLanguage;

}
