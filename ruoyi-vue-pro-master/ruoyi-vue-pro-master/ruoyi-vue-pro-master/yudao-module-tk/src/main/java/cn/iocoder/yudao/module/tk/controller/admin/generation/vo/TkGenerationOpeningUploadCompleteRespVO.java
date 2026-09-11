package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 黄金开头视频上传完成 Response VO")
@Data
public class TkGenerationOpeningUploadCompleteRespVO {

    private String uploadId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String status;
}
