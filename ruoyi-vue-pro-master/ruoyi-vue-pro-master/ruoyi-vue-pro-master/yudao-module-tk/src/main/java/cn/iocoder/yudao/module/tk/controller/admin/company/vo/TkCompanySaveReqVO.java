package cn.iocoder.yudao.module.tk.controller.admin.company.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Schema(description = "管理后台 - TK 公司创建/更新 Request VO")
@Data
public class TkCompanySaveReqVO {

    private Long id;

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 128, message = "公司名称不能超过 128 个字符")
    private String name;

    private Integer status;
    private String contactName;
    private String contactPhone;

}
