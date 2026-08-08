package cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Schema(description = "管理后台 - TK 生成路由分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TkGenerationRoutePageReqVO extends PageParam {

    private String materialPurpose;
    private String productCategoryCode;
    private String routeCode;
    private String routeName;
    private Boolean enabled;

}
