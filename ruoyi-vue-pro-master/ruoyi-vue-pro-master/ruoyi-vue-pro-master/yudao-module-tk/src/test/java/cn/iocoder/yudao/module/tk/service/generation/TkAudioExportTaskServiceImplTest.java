package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkAudioExportTaskRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioExportTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkAudioExportTaskMapper;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceProviderRouter;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceTtsClient;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkAudioExportTaskServiceImplTest {

    @Test
    void exportSynthesizesAudioAndSettlesOneCredit() {
        TkAudioExportTaskMapper mapper = mock(TkAudioExportTaskMapper.class);
        doAnswer(invocation -> {
            TkAudioExportTaskDO task = invocation.getArgument(0);
            task.setId(55L);
            return 1;
        }).when(mapper).insert(any(TkAudioExportTaskDO.class));
        TkCreditService creditService = mock(TkCreditService.class);
        when(creditService.freezeForAudioExport(166L)).thenReturn(9L);
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), any(), any(), any()))
                .thenReturn("https://cdn.example/audio.mp3");
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        when(voiceProfileService.resolveVoiceSelection(null, "voice-a")).thenReturn("voice-a");

        TkAudioExportTaskServiceImpl service = new TkAudioExportTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioExportTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "dataScopeService", tenantScope());
        ReflectionTestUtils.setField(service, "voiceProviderRouter", new TkVoiceProviderRouter(
                Collections.singletonList(new FakeTtsClient())));

        TkAudioExportTaskRespVO result = service.export(new TkAudioExportTaskCreateReqVO()
                .setRequestId("request-1")
                .setScriptText("Buy now")
                .setTtsProvider(TkTtsProviderEnum.DASHSCOPE)
                .setVoiceCode("voice-a")
                .setTargetLanguage("en"));

        assertEquals(55L, result.getId());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("https://cdn.example/audio.mp3", result.getAudioUrl());
        verify(creditService).settleByLogId(9L);
        ArgumentCaptor<TkAudioExportTaskDO> taskCaptor = ArgumentCaptor.forClass(TkAudioExportTaskDO.class);
        verify(mapper).updateById(taskCaptor.capture());
        assertEquals("SUCCESS", taskCaptor.getValue().getStatus());
    }

    @Test
    void exportFailureMarksTaskFailedAndRefundsCredit() {
        TkAudioExportTaskMapper mapper = mock(TkAudioExportTaskMapper.class);
        doAnswer(invocation -> {
            TkAudioExportTaskDO task = invocation.getArgument(0);
            task.setId(55L);
            return 1;
        }).when(mapper).insert(any(TkAudioExportTaskDO.class));
        TkCreditService creditService = mock(TkCreditService.class);
        when(creditService.freezeForAudioExport(166L)).thenReturn(9L);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        when(voiceProfileService.resolveVoiceSelection(null, "voice-a")).thenReturn("voice-a");

        TkAudioExportTaskServiceImpl service = new TkAudioExportTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioExportTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "fileApi", mock(FileApi.class));
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "dataScopeService", tenantScope());
        ReflectionTestUtils.setField(service, "voiceProviderRouter", new TkVoiceProviderRouter(
                Collections.singletonList(new FakeTtsClient(true))));

        assertThrows(IllegalStateException.class, () -> service.export(new TkAudioExportTaskCreateReqVO()
                .setRequestId("request-1")
                .setScriptText("Buy now")
                .setTtsProvider(TkTtsProviderEnum.DASHSCOPE)
                .setVoiceCode("voice-a")
                .setTargetLanguage("en")));

        verify(creditService).refundByLogId(eq(9L), any());
        ArgumentCaptor<TkAudioExportTaskDO> taskCaptor = ArgumentCaptor.forClass(TkAudioExportTaskDO.class);
        verify(mapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
    }

    private TkDataScopeService tenantScope() {
        TkDataScopeService scopeService = mock(TkDataScopeService.class);
        when(scopeService.getCurrentScope()).thenReturn(new TkUserScope(10L, 166L,
                TkUserLevelEnum.TENANT_ADMIN.getCode(), null));
        return scopeService;
    }

    private static class FakeTtsClient implements TkVoiceTtsClient {
        private final boolean fail;

        private FakeTtsClient() {
            this(false);
        }

        private FakeTtsClient(boolean fail) {
            this.fail = fail;
        }

        @Override
        public String provider() {
            return TkTtsProviderEnum.DASHSCOPE;
        }

        @Override
        public String audioFormat() {
            return "mp3";
        }

        @Override
        public byte[] synthesize(TkVoiceSynthesisRequest request) {
            if (fail) {
                throw new IllegalStateException("tts unavailable");
            }
            return new byte[]{1, 2};
        }
    }
}
