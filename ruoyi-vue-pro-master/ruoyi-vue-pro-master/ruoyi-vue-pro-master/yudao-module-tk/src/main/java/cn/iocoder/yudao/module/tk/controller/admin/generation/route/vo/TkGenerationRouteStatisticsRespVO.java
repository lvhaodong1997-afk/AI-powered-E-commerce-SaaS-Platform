package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 生成路由统计 Response VO")
@Data
public class TkGenerationRouteStatisticsRespVO {

    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;
    private String routeName;
    private Long generationCount;
    private Long successCount;
    private Long failedCount;
    private Long runningCount;
    private Double successRate;
    private Long averageDurationSeconds;

}
