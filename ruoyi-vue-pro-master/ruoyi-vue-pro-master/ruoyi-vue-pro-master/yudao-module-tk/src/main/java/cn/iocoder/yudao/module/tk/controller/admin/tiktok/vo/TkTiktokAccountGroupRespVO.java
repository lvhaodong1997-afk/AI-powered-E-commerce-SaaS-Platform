package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - TikTok 账号分组 Response VO")
@Data
public class TkTiktokAccountGroupRespVO {

    private Long id;
    private Long tenantId;
    private Long companyId;
    private String name;
    private String scene;
    private String labels;
    private String remark;
    private Integer status;
    private List<Long> accountIds;
    private Integer accountCount;
    private LocalDateTime createTime;

}
