package cn.iocoder.yudao.module.tk.controller.admin.upload.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - TK 分片上传会话状态 Response VO")
@Data
public class TkUploadSessionStatusRespVO {

    private String uploadId;
    private Integer chunkSize;
    private Integer totalChunks;
    private Long fileSize;
    private Long uploadedSize;
    private Set<Integer> uploadedChunks;
    private String status;

}
