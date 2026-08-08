package cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - TikTok 账号 Response VO")
@Data
public class TkTiktokAccountRespVO {

    private Long id;
    private Long tenantId;
    private Long companyId;
    private String openId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private String scopes;
    private LocalDateTime accessTokenExpireTime;
    private LocalDateTime refreshTokenExpireTime;
    private String tokenStatus;
    private String authStatus;
    private String defaultPrivacyLevel;
    private Boolean allowComment;
    private Boolean allowDuet;
    private Boolean allowStitch;
    private Boolean commercialContent;
    private Boolean brandContent;
    private Boolean aigcContent;
    private String labels;
    private LocalDateTime lastAuthTime;
    private LocalDateTime lastPublishTime;
    private String failReasonCode;
    private String failReason;
    private Integer status;
    private LocalDateTime createTime;

}
