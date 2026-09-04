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
    }
}
