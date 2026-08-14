package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTkVoiceSynthesisServiceTest {

    @Test
    void synthesizeMarksRequestsAsFinalSynthesis() {
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), anyString(), anyString(), anyString()))
                .thenReturn("https://example.com/voice.wav");
        TkVoiceTtsClient client = mock(TkVoiceTtsClient.class);
        when(client.provider()).thenReturn(TkTtsProviderEnum.MIMO);
        when(client.audioFormat()).thenReturn("wav");
        when(client.synthesize(any(TkVoiceSynthesisRequest.class))).thenReturn(new byte[]{1});

        DefaultTkVoiceSynthesisService service = new DefaultTkVoiceSynthesisService();
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "voiceProviderRouter",
                new TkVoiceProviderRouter(Collections.singletonList(client)));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(314L)
                .companyId(208L)
                .ttsProvider(TkTtsProviderEnum.MIMO)
                .mimoVoiceMode(TkMimoVoiceModeEnum.VOICE_DESIGN)
                .mimoVoicePrompt("Natural seller voice")
                .targetLanguage("en")
                .build();
        task.setTenantId(166L);

        service.synthesize(task, "Make it catchy");

        ArgumentCaptor<TkVoiceSynthesisRequest> captor = ArgumentCaptor.forClass(TkVoiceSynthesisRequest.class);
        verify(client).synthesize(captor.capture());
        TkVoiceSynthesisRequest request = captor.getValue();
        assertEquals("Make it catchy", request.getText());
        assertEquals(TkMimoVoiceModeEnum.VOICE_DESIGN, request.getMimoVoiceMode());
        assertTrue(request.isFinalSynthesis());
        verify(fileApi).createFile(any(byte[].class), eq("voice-314.wav"),
                eq("tk/166/208/generation-tasks/314"), eq("audio/wav"));
    }
}
