package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 生成路由历史 Response VO")
@Data
public class TkGenerationRouteHistoryRespVO {

    private Long id;
    private Long routeId;
    private Integer routeVersion;
    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;
    private String routeName;
    private String routeConfig;
    private Integer trafficWeight;
    private String abGroup;
    private LocalDateTime lastPublishTime;
    private Boolean enabled;
    private String changeReason;
    private LocalDateTime createTime;

}
