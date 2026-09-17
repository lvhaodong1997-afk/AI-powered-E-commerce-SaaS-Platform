package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 社交视频 OSS 上传会话 Response VO")
public class TkSocialVideoUploadSessionRespVO {

    private String uploadId;
    private String uploadUrl;
    private String publicUrl;
    private String objectKey;
    private String accessKeyId;
    private String policy;
    private String signature;
    private String successActionStatus;
    private String expiration;
}
