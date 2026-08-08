package cn.iocoder.yudao.module.tk.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - TK 公司精简 Response VO")
@Data
public class TkCompanySimpleRespVO {

    private Long id;
    private String name;

}
