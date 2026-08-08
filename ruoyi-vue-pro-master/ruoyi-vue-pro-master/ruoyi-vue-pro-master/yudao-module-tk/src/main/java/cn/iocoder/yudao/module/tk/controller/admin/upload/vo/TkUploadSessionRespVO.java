package cn.iocoder.yudao.module.tk.controller.admin.upload.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - TK 分片上传会话 Response VO")
@Data
public class TkUploadSessionRespVO {

    private String uploadId;
    private String uploadMode;
    private Integer chunkSize;
    private Integer totalChunks;
    private Long uploadedSize;
    private Set<Integer> uploadedChunks;
    private String uploadUrl;
    private String publicUrl;
    private String objectKey;
    private String accessKeyId;
    private String policy;
    private String signature;
    private String successActionStatus;
    private String expiration;

}
