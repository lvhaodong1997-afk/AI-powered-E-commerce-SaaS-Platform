package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 账号公开视频 Response VO")
@Data
public class TkTiktokContentVideoRespVO {

    private Long id;
    private Long companyId;
    private Long accountId;
    private String openId;
    private String videoId;
    private String title;
    private String videoDescription;
    private String shareUrl;
    private String embedLink;
    private String embedHtml;
    private String coverImageUrl;
    private Long videoCreateTime;
    private Integer duration;
    private Integer height;
    private Integer width;
    private Long likeCount;
    private Long commentCount;
    private Long shareCount;
    private Long viewCount;
    private Boolean aigc;
    private String status;
    private String failReason;
    private LocalDateTime lastSyncTime;
    private LocalDateTime noLongerPublicTime;

}
