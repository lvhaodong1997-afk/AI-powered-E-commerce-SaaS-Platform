package cn.iocoder.yudao.module.tk.controller.open.copywriting.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public final class TkOpenCopywritingVO {

    private TkOpenCopywritingVO() {
    }

    @Data
    @Schema(description = "TK Open 文案改写请求")
    public static class RewriteReq {

        @NotBlank(message = "原始文案不能为空")
        @Size(max = 20000, message = "原始文案长度不能超过 20000 个字符")
        @Schema(description = "需要改写的原始文案", requiredMode = Schema.RequiredMode.REQUIRED)
        private String copywriting;

        @NotBlank(message = "提示词不能为空")
        @Size(max = 4000, message = "提示词长度不能超过 4000 个字符")
        @Schema(description = "文案改写要求", requiredMode = Schema.RequiredMode.REQUIRED)
        private String prompt;
    }

    @Data
    @Schema(description = "TK Open 文案改写响应")
    public static class RewriteResp {

        @Schema(description = "根据提示词重新生成的新文案")
        private String copywriting;

        public RewriteResp(String copywriting) {
            this.copywriting = copywriting;
        }
    }

}
