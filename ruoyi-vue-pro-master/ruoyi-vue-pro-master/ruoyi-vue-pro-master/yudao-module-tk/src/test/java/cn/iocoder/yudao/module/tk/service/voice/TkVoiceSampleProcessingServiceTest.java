package cn.iocoder.yudao.module.tk.service.voice;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TkVoiceSampleProcessingServiceTest {

    @Test
    void keepsSupportedAudioWithoutVideoExtraction() {
        TkVoiceSampleProcessingService service = new TkVoiceSampleProcessingService(null);
        MockMultipartFile file = new MockMultipartFile("file", "speaker.mp3", "audio/mpeg", new byte[]{1, 2, 3});

        TkVoiceProcessedSample sample = service.process(file);

        assertEquals("speaker.mp3", sample.getFilename());
        assertEquals("audio/mpeg", sample.getContentType());
        assertArrayEquals(new byte[]{1, 2, 3}, sample.getContent());
    }

    @Test
    void recognizesSupportedVideoExtensions() {
        assertEquals(true, TkVoiceSampleProcessingService.isVideo("speaker.mp4"));
        assertEquals(true, TkVoiceSampleProcessingService.isVideo("speaker.MOV"));
        assertEquals(true, TkVoiceSampleProcessingService.isVideo("speaker.webm"));
        assertEquals(false, TkVoiceSampleProcessingService.isVideo("speaker.mp3"));
    }
}
