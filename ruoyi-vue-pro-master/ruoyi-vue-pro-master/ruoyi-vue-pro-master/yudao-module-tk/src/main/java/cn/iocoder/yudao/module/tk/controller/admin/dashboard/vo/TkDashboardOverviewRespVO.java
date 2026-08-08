package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Admin - TK dashboard overview Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardOverviewRespVO {

    private Long generationTaskCount;
    private Long successVideoCount;
    private Long failedVideoCount;
    private Long runningTaskCount;
    private Integer successRate;
    private Long averageDurationSeconds;
    private Long consumedCredits;
    private Long materialLibraryCount;
    private Long materialVideoCount;
    private Long availableMaterialVideoCount;
    private Long parsingMaterialVideoCount;
    private Long failedMaterialVideoCount;
    private Long authorizedAccountCount;
    private Long abnormalAccountCount;

}
