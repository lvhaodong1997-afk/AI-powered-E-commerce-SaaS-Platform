package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 生成路由统计 Request VO")
@Data
public class TkGenerationRouteStatisticsReqVO {

    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

}
