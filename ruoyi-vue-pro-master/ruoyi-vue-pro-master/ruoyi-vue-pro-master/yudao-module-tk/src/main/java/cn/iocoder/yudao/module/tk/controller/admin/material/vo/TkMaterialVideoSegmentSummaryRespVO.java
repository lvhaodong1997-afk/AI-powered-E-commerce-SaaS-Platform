package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - TK 素材视频分组统计 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkMaterialVideoSegmentSummaryRespVO {

    @Schema(description = "素材分组")
    private String segmentType;

    @Schema(description = "可用素材数量")
    private Long count;

}
