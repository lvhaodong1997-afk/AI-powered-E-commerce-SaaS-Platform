package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Schema(description = "管理后台 - TK Open 视频文案时间轴提取创建 Response VO")
@Data
@Builder
public class TkOpenVideoTranscriptExtractCreateRespVO {

    private Long taskId;
    private String status;

}
