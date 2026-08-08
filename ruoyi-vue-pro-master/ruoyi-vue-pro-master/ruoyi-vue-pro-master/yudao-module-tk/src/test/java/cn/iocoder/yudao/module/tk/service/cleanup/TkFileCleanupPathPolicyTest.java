package cn.iocoder.yudao.module.tk.service.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkFileCleanupPathPolicyTest {

    @Test
    void extractGenerationTaskPathSupportsSignedOssUrl() {
        String url = "https://tk-material-factory.oss-cn-beijing.aliyuncs.com/"
                + "tk/174/174/generation-tasks/142/20260801/generated-142.mp4"
                + "?OSSAccessKeyId=demo&Expires=2101103749&Signature=abc";

        assertEquals("tk/174/174/generation-tasks/142/20260801/generated-142.mp4",
                TkFileCleanupPathPolicy.extractGenerationTaskPath(url).orElseThrow());
    }

    @Test
    void extractGenerationTaskPathRejectsMaterialVideoUrl() {
        String url = "https://tk-material-factory.oss-cn-beijing.aliyuncs.com/"
                + "tk/174/174/material-videos/20260803/source.mp4?OSSAccessKeyId=demo";

        assertTrue(TkFileCleanupPathPolicy.extractGenerationTaskPath(url).isEmpty());
    }
}
