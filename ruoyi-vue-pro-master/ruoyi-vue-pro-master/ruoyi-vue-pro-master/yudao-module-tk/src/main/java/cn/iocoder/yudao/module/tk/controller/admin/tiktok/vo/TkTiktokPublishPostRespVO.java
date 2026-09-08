package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TkTiktokPublishPostRespVO {

    private Long id;
    private Long publishDetailId;
    private Long accountId;
    private String publishId;
    private String publicPostId;
    private String shareUrl;
    private String embedLink;
    private String embedHtml;
    private String title;
    private String videoDescription;
    private Long videoCreateTime;
    private Integer duration;
    private Integer width;
    private Integer height;
    private String coverUrl;
    private String status;
    private String failReason;
    private LocalDateTime lastSyncTime;

}
