package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "管理后台 - TikTok 账号分组保存 Request VO")
@Data
public class TkTiktokAccountGroupSaveReqVO {

    private Long id;
    private Long companyId;

    @NotBlank(message = "分组名称不能为空")
    private String name;

    private String scene;
    private String labels;
    private String remark;
    private Integer status;
    private List<Long> accountIds;

}
