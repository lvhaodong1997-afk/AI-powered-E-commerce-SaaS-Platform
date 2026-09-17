package cn.iocoder.yudao.module.tk.controller.admin.social.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@Schema(description = "管理后台 - 社交视频 OSS 上传完成 Request VO")
public class TkSocialVideoUploadCompleteReqVO {

    @NotBlank(message = "上传会话不能为空")
    private String uploadId;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long fileSize;

    private String contentType;

    @NotBlank(message = "OSS 对象不能为空")
    private String objectKey;

    @NotNull(message = "视频宽度不能为空")
    @Min(value = 1, message = "视频宽度必须大于 0")
    private Integer width;

    @NotNull(message = "视频高度不能为空")
    @Min(value = 1, message = "视频高度必须大于 0")
    private Integer height;

    @NotNull(message = "视频时长不能为空")
    @Min(value = 1, message = "视频时长必须大于 0")
    private Double durationSeconds;

    @NotNull(message = "视频帧率不能为空")
    @Min(value = 1, message = "视频帧率必须大于 0")
    private Double frameRate;
}
