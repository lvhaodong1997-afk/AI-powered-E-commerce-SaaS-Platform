package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 音频导出任务 Response VO")
@Data
public class TkAudioExportTaskRespVO {

    private Long id;
    private String audioUrl;
    private String status;
    private String failReason;
}
