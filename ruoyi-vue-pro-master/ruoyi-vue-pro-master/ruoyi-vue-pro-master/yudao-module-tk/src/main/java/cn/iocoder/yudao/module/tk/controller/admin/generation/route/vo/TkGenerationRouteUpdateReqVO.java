package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - TK 生成路由更新 Request VO")
@Data
public class TkGenerationRouteUpdateReqVO {

    @NotNull
    private Long id;

    private String routeName;
    private String routeConfig;
    private Integer trafficWeight;
    private String abGroup;
    private Boolean enabled;
    private LocalDateTime lastPublishTime;
    private String remark;

}
