package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - TikTok 发布视频分片上传完成 Request VO")
@Data
public class TkTiktokPublishMediaSessionCompleteReqVO {

    @NotBlank(message = "上传会话不能为空")
    private String uploadId;

    private String coverUrl;
}
