package cn.iocoder.yudao.module.tk.controller.admin.upload.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - TK 完成分片上传 Request VO")
@Data
public class TkUploadSessionCompleteReqVO {

    @NotBlank(message = "上传会话不能为空")
    private String uploadId;

    private Long libraryId;

    private String fileName;

    private Long fileSize;

    private String contentType;

    private String objectKey;

    private String fileUrl;

    private String tags;

    private String usagePhase;

    private String segmentType;

}
