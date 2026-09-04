package cn.iocoder.yudao.module.tk.controller.open.tiktok.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

public final class TkOpenTiktokAuthVO {
    private TkOpenTiktokAuthVO() {}

    @Data
    public static class SessionCreateReq {
        @NotBlank
        @Size(max = 128)
        private String externalAccountId;
        @NotBlank
        @Pattern(regexp = "REDIRECT|QR_CODE")
        private String authMode;
        @Size(max = 512)
        private String clientState;
    }

    @Data
    public static class SessionResp {
        private String authSessionId;
        private String externalAccountId;
        private String clientState;
        private String authMode;
        private String authorizeUrl;
        private String qrcodeUrl;
        private String status;
        private LocalDateTime expireTime;
    }

    @Data
    public static class SessionStatusResp {
        private String authSessionId;
        private String externalAccountId;
        private String clientState;
        private String connectionId;
        private String accountName;
        private String status;
        private String failReason;
        private LocalDateTime expireTime;
    }

    @Data
    public static class ConnectionResp {
        private String connectionId;
        private String externalAccountId;
        private String accountName;
        private String username;
        private String avatarUrl;
        private String authStatus;
        private String tokenStatus;
        private LocalDateTime lastAuthTime;
    }
}
