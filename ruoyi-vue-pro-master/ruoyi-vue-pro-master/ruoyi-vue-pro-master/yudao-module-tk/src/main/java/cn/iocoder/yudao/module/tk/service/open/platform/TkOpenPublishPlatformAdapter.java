package cn.iocoder.yudao.module.tk.service.open.platform;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TkOpenPublishPlatformAdapter {

    String platform();
    boolean isConfigured();
    String buildAuthorizeUrl(String state, String redirectUri);
    OAuthTokenResult exchangeCode(String code, String redirectUri);
    QrCodeResult createQrCode(String state);
    QrStatusResult checkQrCode(String token);
    PlatformUser queryUserInfo(String accessToken);
    OAuthTokenResult refreshAccessToken(String refreshToken);
    CreatorCapabilities queryCreatorInfo(String accessToken);
    PublishInitResult initVideoPost(String accessToken, String postMode, Map<String, Object> payload);
    void uploadVideo(String uploadUrl, Path videoFile, String contentType);
    PublishStatusResult fetchPostStatus(String accessToken, String publishId);
    String defaultPostMode();
    String verifiedPullDomain();

    @Data
    @AllArgsConstructor
    class OAuthTokenResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private Long accessTokenExpiresIn;
        private Long refreshTokenExpiresIn;
        private String scopes;
        private String openId;
        private String errorCode;
        private String failReason;
    }

    @Data
    @AllArgsConstructor
    class PlatformUser {
        private boolean success;
        private String openId;
        private String displayName;
        private String username;
        private String avatarUrl;
        private String failReason;
    }

    @Data
    @AllArgsConstructor
    class QrCodeResult {
        private boolean success;
        private String token;
        private String url;
        private String failReason;
    }

    @Data
    @AllArgsConstructor
    class QrStatusResult {
        private boolean success;
        private String status;
        private String authorizationCode;
        private String failReason;
    }

    @Data
    @AllArgsConstructor
    class CreatorCapabilities {
        private boolean success;
        private String failReason;
        private List<String> privacyLevelOptions;
        private boolean commentDisabled;
        private boolean duetDisabled;
        private boolean stitchDisabled;
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return "access_token_invalid".equals(errorCode);
        }
    }

    @Data
    @AllArgsConstructor
    class PublishInitResult {
        private boolean success;
        private String publishId;
        private String uploadUrl;
        private String failReason;
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return "access_token_invalid".equals(errorCode);
        }
    }

    @Data
    @AllArgsConstructor
    class PublishStatusResult {
        private boolean success;
        private String status;
        private String failReason;
        private String errorCode;

        public boolean isAccessTokenInvalid() {
            return "access_token_invalid".equals(errorCode);
        }
    }
}
