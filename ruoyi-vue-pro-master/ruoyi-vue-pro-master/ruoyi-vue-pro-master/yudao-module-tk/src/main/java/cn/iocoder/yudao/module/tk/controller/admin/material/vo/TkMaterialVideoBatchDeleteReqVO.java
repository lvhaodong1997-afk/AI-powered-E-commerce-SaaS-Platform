package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - TK 素材视频批量删除 Request VO")
@Data
public class TkMaterialVideoBatchDeleteReqVO {

    @NotEmpty(message = "素材视频不能为空")
    private List<@NotNull(message = "素材视频编号不能为空") Long> ids;

}
