package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - TK 完成黄金开头视频上传 Request VO")
@Data
public class TkGenerationOpeningUploadSessionCompleteReqVO {

    @NotBlank(message = "上传会话不能为空")
    private String uploadId;
}
