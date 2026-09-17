package cn.iocoder.yudao.module.tk.service.upload;

import lombok.AllArgsConstructor;
import lombok.Getter;

public interface TkOssObjectStorageClient {

    boolean isConfigured();

    void deleteObject(String objectKey);

    ObjectMetadata headObject(String objectKey);

    @Getter
    @AllArgsConstructor
    class ObjectMetadata {
        private final long contentLength;
        private final String sha256;
        private final String etag;
        private final String versionId;

        public ObjectMetadata(long contentLength, String sha256) {
            this(contentLength, sha256, null, null);
        }
    }
}
