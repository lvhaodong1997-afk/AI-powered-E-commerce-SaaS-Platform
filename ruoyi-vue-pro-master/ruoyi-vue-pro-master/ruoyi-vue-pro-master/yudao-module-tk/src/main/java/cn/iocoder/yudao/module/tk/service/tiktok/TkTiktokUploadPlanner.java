package cn.iocoder.yudao.module.tk.service.tiktok;

import lombok.Getter;

/**
 * Calculates the FILE_UPLOAD layout required by TikTok's media transfer API.
 */
final class TkTiktokUploadPlanner {

    static final long MIN_CHUNK_SIZE = 5_000_000L;
    static final long MAX_CHUNK_SIZE = 64_000_000L;
    static final long DEFAULT_CHUNK_SIZE = 32_000_000L;
    static final long MAX_FINAL_CHUNK_SIZE = 128_000_000L;
    static final long MAX_VIDEO_SIZE = 4_000_000_000L;

    private TkTiktokUploadPlanner() {
    }

    static UploadPlan plan(long videoSize) {
        if (videoSize <= 0) {
            throw new IllegalArgumentException("视频文件不能为空");
        }
        if (videoSize > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException("视频文件超过 TikTok 4GB 限制");
        }
        if (videoSize <= MAX_CHUNK_SIZE) {
            return new UploadPlan(videoSize, videoSize, 1);
        }

        int totalChunkCount = Math.toIntExact(videoSize / DEFAULT_CHUNK_SIZE);
        return new UploadPlan(videoSize, DEFAULT_CHUNK_SIZE, Math.max(1, totalChunkCount));
    }

    @Getter
    static final class UploadPlan {

        private final long videoSize;
        private final long chunkSize;
        private final int totalChunkCount;

        private UploadPlan(long videoSize, long chunkSize, int totalChunkCount) {
            this.videoSize = videoSize;
            this.chunkSize = chunkSize;
            this.totalChunkCount = totalChunkCount;
        }

        long chunkLength(int chunkIndex) {
            if (chunkIndex < 0 || chunkIndex >= totalChunkCount) {
                throw new IndexOutOfBoundsException("TikTok 分片序号无效：" + chunkIndex);
            }
            if (chunkIndex < totalChunkCount - 1) {
                return chunkSize;
            }
            return videoSize - chunkSize * (totalChunkCount - 1L);
        }

        long chunkOffset(int chunkIndex) {
            if (chunkIndex < 0 || chunkIndex >= totalChunkCount) {
                throw new IndexOutOfBoundsException("TikTok 分片序号无效：" + chunkIndex);
            }
            return chunkSize * chunkIndex;
        }

        long totalChunkBytes() {
            long total = 0L;
            for (int index = 0; index < totalChunkCount; index++) {
                total += chunkLength(index);
            }
            return total;
        }
    }
}
