package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 生成路由 Response VO")
@Data
public class TkGenerationRouteRespVO {

    private Long id;
    private Long tenantId;
    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;
    private String routeName;
    private String routeConfig;
    private Integer routeVersion;
    private Integer trafficWeight;
    private String abGroup;
    private LocalDateTime lastPublishTime;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
