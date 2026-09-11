package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Schema(description = "管理后台 - TK 黄金开头视频上传会话创建 Request VO")
@Data
public class TkGenerationOpeningUploadSessionCreateReqVO {

    @NotNull(message = "素材库不能为空")
    private Long libraryId;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于0")
    private Long fileSize;

    private String contentType;
}
