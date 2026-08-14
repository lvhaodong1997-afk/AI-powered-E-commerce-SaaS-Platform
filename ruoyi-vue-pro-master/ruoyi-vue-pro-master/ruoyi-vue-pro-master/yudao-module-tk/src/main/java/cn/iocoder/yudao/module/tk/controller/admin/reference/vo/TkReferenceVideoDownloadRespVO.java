package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "管理后台 - TK 对标视频下载 Response VO")
@Data
@Builder
public class TkReferenceVideoDownloadRespVO {

    private String sourceUrl;
    private String resolvedVideoUrl;
    private String coverUrl;
    private Long videoDuration;
    private String resolution;

}
