package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - TikTok 发布创建 Request VO")
@Data
public class TkTiktokPublishCreateReqVO {

    private Long generationTaskId;

    private Long uploadedVideoId;

    private Long coverTimestampMs;

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
