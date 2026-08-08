package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 生成路由历史分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkGenerationRouteHistoryPageReqVO extends PageParam {

    private Long routeId;
    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;

}
