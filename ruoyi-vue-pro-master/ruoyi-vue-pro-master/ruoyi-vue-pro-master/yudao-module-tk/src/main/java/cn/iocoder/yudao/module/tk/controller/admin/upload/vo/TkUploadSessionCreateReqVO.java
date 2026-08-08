package cn.iocoder.yudao.module.tk.controller.admin.upload.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - TK 创建分片上传会话 Request VO")
@Data
public class TkUploadSessionCreateReqVO {

    @NotNull(message = "素材库不能为空")
    private Long libraryId;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    private String contentType;

}
