package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import lombok.Data;

@Data
public class TkTiktokPublishMediaRespVO {
    private Long id;
    private String fileName;
    private String fileUrl;
    private String coverUrl;
    private Long fileSize;
    private String mimeType;
    private String status;
}
