package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

@Data
public class TkTiktokAccountRecentVideoRespVO {

    private String videoId;
    private String title;
    private String coverImageUrl;
    private String shareUrl;
    private Long viewCount;
    private Long createTime;
}
