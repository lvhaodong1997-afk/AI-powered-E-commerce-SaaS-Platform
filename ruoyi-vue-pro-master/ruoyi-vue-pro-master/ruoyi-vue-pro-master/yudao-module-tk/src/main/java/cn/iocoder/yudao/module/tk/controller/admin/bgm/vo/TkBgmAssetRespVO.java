package cn.iocoder.yudao.module.tk.controller.admin.bgm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TkBgmAssetRespVO {
    private Long id;
    private String name;
    private String sourceType;
    private String style;
    private String fileUrl;
    private Integer duration;
    private String format;
    private Integer status;
    private LocalDateTime createTime;
}
