package cn.iocoder.yudao.module.tk.service.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void extractTranscriptAudioPathSupportsFileUrl() {
        String url = "https://tkassetplant.fnn.net.cn/admin-api/infra/file/29/get/"
                + "tk/open-video-transcripts/112/20260905/transcript-audio-112.wav";

        assertEquals("tk/open-video-transcripts/112/20260905/transcript-audio-112.wav",
                TkFileCleanupPathPolicy.extractTranscriptAudioPath(url).orElseThrow());
        assertEquals(112L, TkFileCleanupPathPolicy.extractTranscriptTaskId(url).getAsLong());
    }

    @Test
    void extractTranscriptAudioPathRejectsMismatchedTaskId() {
        String path = "tk/open-video-transcripts/112/20260905/transcript-audio-113.wav";

        assertFalse(TkFileCleanupPathPolicy.extractTranscriptAudioPath(path).isPresent());
        assertTrue(TkFileCleanupPathPolicy.extractTranscriptTaskId(path).isEmpty());
    }
}
