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
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TkAudioExportTaskServiceImplTest {

    @Test
    void exportMiniMaxResolvesCanonicalVoiceStoresMp3AndSettlesOneCredit() {
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
        TkMiniMaxVoiceDictionaryService miniMaxVoiceDictionaryService = mock(TkMiniMaxVoiceDictionaryService.class);
        when(miniMaxVoiceDictionaryService.resolveVoiceCode(null, "friendly-alias"))
                .thenReturn("canonical-voice-id");

        TkAudioExportTaskServiceImpl service = new TkAudioExportTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioExportTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "miniMaxVoiceDictionaryService", miniMaxVoiceDictionaryService);
        ReflectionTestUtils.setField(service, "dataScopeService", tenantScope());
        ReflectionTestUtils.setField(service, "voiceProviderRouter", new TkVoiceProviderRouter(
                Collections.singletonList(new FakeTtsClient(TkTtsProviderEnum.MINIMAX))));

        TkAudioExportTaskRespVO result = service.export(new TkAudioExportTaskCreateReqVO()
                .setRequestId("request-1")
                .setScriptText("Buy now")
                .setTtsProvider(TkTtsProviderEnum.MINIMAX)
                .setVoiceCode("friendly-alias")
                .setTargetLanguage("en"));

        assertEquals(55L, result.getId());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("https://cdn.example/audio.mp3", result.getAudioUrl());
        verify(creditService).settleByLogId(9L);
        verify(miniMaxVoiceDictionaryService).resolveVoiceCode(null, "friendly-alias");
        ArgumentCaptor<byte[]> audioCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileApi).createFile(audioCaptor.capture(), eq("audio-55.mp3"),
                eq("tk/166/tenant/audio-exports/55"), eq("audio/mpeg"));
        assertArrayEquals(new byte[]{1, 2}, audioCaptor.getValue());
        ArgumentCaptor<TkAudioExportTaskDO> taskCaptor = ArgumentCaptor.forClass(TkAudioExportTaskDO.class);
        verify(mapper).updateById(taskCaptor.capture());
        assertEquals("SUCCESS", taskCaptor.getValue().getStatus());
        assertEquals("canonical-voice-id", taskCaptor.getValue().getVoiceCode());
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
        verify(creditService, never()).settleByLogId(any());
        ArgumentCaptor<TkAudioExportTaskDO> taskCaptor = ArgumentCaptor.forClass(TkAudioExportTaskDO.class);
        verify(mapper).updateById(taskCaptor.capture());
        assertEquals("FAILED", taskCaptor.getValue().getStatus());
    }

    @Test
    void duplicateRequestReturnsExistingTaskWithoutChargingOrSynthesizing() {
        TkAudioExportTaskMapper mapper = mock(TkAudioExportTaskMapper.class);
        TkAudioExportTaskDO existing = TkAudioExportTaskDO.builder()
                .id(77L)
                .requestId("request-duplicate")
                .status("SUCCESS")
                .audioUrl("https://cdn.example/existing.mp3")
                .build();
        existing.setTenantId(166L);
        when(mapper.selectByRequestId(166L, "request-duplicate")).thenReturn(existing);
        TkCreditService creditService = mock(TkCreditService.class);
        FileApi fileApi = mock(FileApi.class);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        TkMiniMaxVoiceDictionaryService miniMaxVoiceDictionaryService = mock(TkMiniMaxVoiceDictionaryService.class);
        TkVoiceProviderRouter voiceProviderRouter = mock(TkVoiceProviderRouter.class);

        TkAudioExportTaskServiceImpl service = new TkAudioExportTaskServiceImpl();
        ReflectionTestUtils.setField(service, "audioExportTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "miniMaxVoiceDictionaryService", miniMaxVoiceDictionaryService);
        ReflectionTestUtils.setField(service, "dataScopeService", tenantScope());
        ReflectionTestUtils.setField(service, "voiceProviderRouter", voiceProviderRouter);

        TkAudioExportTaskRespVO result = service.export(new TkAudioExportTaskCreateReqVO()
                .setRequestId("request-duplicate")
                .setScriptText("Do not synthesize")
                .setTtsProvider(TkTtsProviderEnum.MINIMAX)
                .setVoiceCode("friendly-alias"));

        assertEquals(77L, result.getId());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("https://cdn.example/existing.mp3", result.getAudioUrl());
        verifyNoInteractions(creditService, fileApi, voiceProfileService,
                miniMaxVoiceDictionaryService, voiceProviderRouter);
        verify(mapper, never()).insert(any(TkAudioExportTaskDO.class));
    }

    private TkDataScopeService tenantScope() {
        TkDataScopeService scopeService = mock(TkDataScopeService.class);
        when(scopeService.getCurrentScope()).thenReturn(new TkUserScope(10L, 166L,
                TkUserLevelEnum.TENANT_ADMIN.getCode(), null));
        return scopeService;
    }

    private static class FakeTtsClient implements TkVoiceTtsClient {
        private final String provider;
        private final boolean fail;

        private FakeTtsClient() {
            this(TkTtsProviderEnum.DASHSCOPE, false);
        }

        private FakeTtsClient(boolean fail) {
            this(TkTtsProviderEnum.DASHSCOPE, fail);
        }

        private FakeTtsClient(String provider) {
            this(provider, false);
        }

        private FakeTtsClient(String provider, boolean fail) {
            this.provider = provider;
            this.fail = fail;
        }

        @Override
        public String provider() {
            return provider;
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
