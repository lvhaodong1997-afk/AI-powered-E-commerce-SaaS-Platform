package cn.iocoder.yudao.module.tk.controller.open.tiktok.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

public final class TkOpenTiktokMediaVO {
    private TkOpenTiktokMediaVO() {}

    @Data
    public static class UploadCreateReq {
        private String mediaType;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String sha256;
    }

    @Data
    public static class UploadSessionResp {
        private String mediaType;
        private String status;
        private String uploadId;
        private String uploadMode;
        private Integer chunkSize;
        private Integer totalChunks;
        private String uploadUrl;
        private String objectKey;
        private OssFields fields;
        private LocalDateTime expireTime;
    }

    @Data
    public static class OssFields {
        @com.fasterxml.jackson.annotation.JsonProperty("xOssObjectAcl")
        private String xOssObjectAcl;
        private String contentType;
        @com.fasterxml.jackson.annotation.JsonProperty("xOssObjectAcl")
        public String getXOssObjectAcl() {return xOssObjectAcl;}
        @com.fasterxml.jackson.annotation.JsonProperty("xOssObjectAcl")
        public void setXOssObjectAcl(String value) {xOssObjectAcl=value;}
        private final String policy;
        private final String ossAccessKeyId;
        private final String signature;
        private final String xOssMetaSha256;
        public OssFields(String policy, String ossAccessKeyId, String signature, String xOssMetaSha256) {
            this.policy = policy;
            this.ossAccessKeyId = ossAccessKeyId;
            this.signature = signature;
            this.xOssMetaSha256 = xOssMetaSha256;
        }
    }

    @Data
    public static class UploadStatusResp {
        private String mediaType;
        private String failReason;
        private Integer width;
        private Integer height;
        private String uploadId;
        private String mediaId;
        private String uploadMode;
        private Long fileSize;
        private Long uploadedSize;
        private String status;
    }

    @Data
    public static class UploadCompleteReq {
        private Long fileSize;
        private String sha256;
        private Long coverTimestampMs;
    }

    @Data
    public static class MediaResp {
        private String mediaType;
        private String failReason;
        private Integer width;
        private Integer height;
        private String mediaId;
        private String uploadId;
        private String fileName;
        private Long fileSize;
        private String contentType;
        private String status;
    }
}
