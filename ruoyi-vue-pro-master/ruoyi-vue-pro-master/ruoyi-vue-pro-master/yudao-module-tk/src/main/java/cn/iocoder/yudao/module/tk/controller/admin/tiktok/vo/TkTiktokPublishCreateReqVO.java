package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "管理后台 - TikTok 发布创建 Request VO")
@Data
public class TkTiktokPublishCreateReqVO {

    @NotNull(message = "生成任务编号不能为空")
    private Long generationTaskId;

    private List<Long> accountIds;
    private List<Long> groupIds;
    private String title;
    private String caption;
    private String postMode;
    private String privacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;

}
