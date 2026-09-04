package cn.iocoder.yudao.module.tk.controller.admin.openapi.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

public final class TkOpenApiClientAdminVO {

    private TkOpenApiClientAdminVO() {
    }

    @Data
    public static class CreateReq {
        @NotBlank @Size(max = 128)
        private String clientName;
        @Size(max = 512)
        private String authCallbackUrl;
        @Size(max = 512)
        private String publishCallbackUrl;
        @Size(max = 2048)
        private String allowedIps;
        @Size(max = 512)
        private String permissions;
        @Min(1) @Max(10000)
        private Integer rateLimitPerMinute;
        @Min(1)
        private Integer dailyQuota;
        private Integer status;
        @Size(max = 512)
        private String remark;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UpdateReq extends CreateReq {
        @NotBlank
        private String clientId;
    }

    @Data
    public static class StatusReq {
        @NotBlank
        private String clientId;
        @NotNull @Min(0) @Max(1)
        private Integer status;
    }

    @Schema(description = "开放 API 调用方分页查询")
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class PageReq extends PageParam {
        private String clientId;
        private String clientName;
        private Integer status;
    }

    @Data
    public static class Resp {
        private String clientId;
        private String clientName;
        private String authCallbackUrl;
        private String publishCallbackUrl;
        private String allowedIps;
        private String permissions;
        private Integer rateLimitPerMinute;
        private Integer dailyQuota;
        private Integer status;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class CredentialResp {
        private String clientId;
        private String clientSecret;
        private String callbackSecret;
    }
}
