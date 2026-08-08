package cn.iocoder.yudao.module.tk.controller.admin.material.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - TK 素材视频分段更新 Request VO")
@Data
public class TkMaterialVideoSegmentTypeUpdateReqVO {

    @NotEmpty(message = "素材视频不能为空")
    private List<@NotNull(message = "素材视频编号不能为空") Long> ids;

    @NotEmpty(message = "素材分段不能为空")
    private String segmentType;

}
