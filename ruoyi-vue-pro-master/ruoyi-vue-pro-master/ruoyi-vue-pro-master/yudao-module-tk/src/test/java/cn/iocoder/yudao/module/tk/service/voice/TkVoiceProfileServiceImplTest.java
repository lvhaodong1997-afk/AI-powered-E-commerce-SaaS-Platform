package cn.iocoder.yudao.module.tk.service.voice;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceProfileMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.enums.TkVoiceProfileStatusEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDashScopeTtsClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoTtsClient;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkMimoVoiceModeEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkVoiceProfileServiceImplTest {

    @Test
    void resolveSelectionAllowsRegisteredSystemVoice() {
        TkVoiceProfileServiceImpl service = createService(mock(TkVoiceProfileMapper.class), tenantScope());
        assertEquals("cosyvoice-v3.5-plus-tklisa-06c5654167dd4da3bfd5d69dfd5402b0",
                service.resolveVoiceSelection(null,
                        "cosyvoice-v3.5-plus-tklisa-06c5654167dd4da3bfd5d69dfd5402b0"));
    }

    @Test
    void resolveSelectionFallsBackToDefaultSystemVoiceWhenMissing() {
        TkVoiceProfileServiceImpl service = createService(mock(TkVoiceProfileMapper.class), tenantScope());

        assertEquals("cosyvoice-v3.5-plus-tklisa-06c5654167dd4da3bfd5d69dfd5402b0",
                service.resolveVoiceSelection(null, null));
    }

    @Test
    void resolveSelectionRejectsUnknownRawVoiceCode() {
        TkVoiceProfileServiceImpl service = createService(mock(TkVoiceProfileMapper.class), tenantScope());
        assertThrows(RuntimeException.class,
                () -> service.resolveVoiceSelection(null, "cosyvoice-v3.5-plus-another-tenant-secret"));
    }

    @Test
    void createRejectsPlatformContextWithoutTargetTenant() {
        TkDataScopeService scopeService = mock(TkDataScopeService.class);
        when(scopeService.getCurrentScope()).thenReturn(new TkUserScope(1L, null,
                TkUserLevelEnum.PLATFORM_ADMIN.getCode(), null));
        TkVoiceProfileServiceImpl service = createService(mock(TkVoiceProfileMapper.class), scopeService);
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[]{1});

        assertThrows(RuntimeException.class, () -> service.createVoice("Test", true, file));
    }

    @Test
    void createRejectsMissingSpeakerConsent() {
        TkDataScopeService scopeService = tenantScope();
        TkVoiceProfileServiceImpl service = createService(mock(TkVoiceProfileMapper.class), scopeService);
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[]{1});

        assertThrows(RuntimeException.class, () -> service.createVoice("Test", false, file));
    }

    @Test
    void createStoresVoiceInsideCurrentTenant() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        TkDataScopeService scopeService = tenantScope();
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), any(), any(), any())).thenReturn("https://cdn.example/voice.mp3");
        TkDashScopeVoiceEnrollmentClient enrollmentClient = mock(TkDashScopeVoiceEnrollmentClient.class);
        when(enrollmentClient.createVoice(any(), any())).thenReturn("cosyvoice-v3.5-plus-demo-123");
        TkDashScopeTtsClient ttsClient = mock(TkDashScopeTtsClient.class);
        when(ttsClient.synthesize(any(), any(), any())).thenReturn(new byte[]{2});
        when(ttsClient.getAudioFormat()).thenReturn("mp3");
        TkVoiceProfileServiceImpl service = createService(mapper, scopeService, fileApi, enrollmentClient, ttsClient);
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[]{1});

        service.createVoice("My voice", true, file);

        ArgumentCaptor<TkVoiceProfileDO> captor = ArgumentCaptor.forClass(TkVoiceProfileDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(166L, captor.getValue().getTenantId());
        assertEquals(TkVoiceProfileStatusEnum.READY.getStatus(), captor.getValue().getStatus());
        assertEquals("cosyvoice-v3.5-plus-demo-123", captor.getValue().getVoiceCode());
    }

    @Test
    void createFallsBackToPublicUrlWhenStorageDoesNotSupportPresigning() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), any(), any(), any()))
                .thenReturn("https://example.com/public-voice.mp3");
        when(fileApi.presignGetUrl("https://example.com/public-voice.mp3", 1800))
                .thenThrow(new UnsupportedOperationException("不支持的操作"));
        TkDashScopeVoiceEnrollmentClient enrollmentClient = mock(TkDashScopeVoiceEnrollmentClient.class);
        when(enrollmentClient.createVoice("https://example.com/public-voice.mp3", "tk166"))
                .thenReturn("cosyvoice-v3.5-plus-demo-123");
        TkDashScopeTtsClient ttsClient = mock(TkDashScopeTtsClient.class);
        when(ttsClient.synthesize(any(), any(), any())).thenReturn(new byte[]{2});
        when(ttsClient.getAudioFormat()).thenReturn("mp3");
        TkVoiceProfileServiceImpl service = createService(mapper, tenantScope(), fileApi, enrollmentClient, ttsClient);
        MockMultipartFile file = new MockMultipartFile("file", "voice.mp3", "audio/mpeg", new byte[]{1});

        service.createVoice("My voice", true, file);

        verify(enrollmentClient).createVoice("https://example.com/public-voice.mp3", "tk166");
        ArgumentCaptor<TkVoiceProfileDO> captor = ArgumentCaptor.forClass(TkVoiceProfileDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(TkVoiceProfileStatusEnum.READY.getStatus(), captor.getValue().getStatus());
    }

    @Test
    void createUploadsProcessedWavWhenSourceIsVideo() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), any(), any(), any()))
                .thenReturn("https://example.com/processed.wav");
        TkDashScopeVoiceEnrollmentClient enrollmentClient = mock(TkDashScopeVoiceEnrollmentClient.class);
        when(enrollmentClient.createVoice(any(), any())).thenReturn("cosyvoice-v3.5-plus-demo-123");
        TkDashScopeTtsClient ttsClient = mock(TkDashScopeTtsClient.class);
        when(ttsClient.synthesize(any(), any(), any())).thenReturn(new byte[]{2});
        when(ttsClient.getAudioFormat()).thenReturn("mp3");
        TkVoiceSampleProcessingService sampleProcessingService = mock(TkVoiceSampleProcessingService.class);
        when(sampleProcessingService.process(any(MultipartFile.class)))
                .thenReturn(new TkVoiceProcessedSample(new byte[]{9, 8}, "speaker-voice.wav", "audio/wav"));
        TkVoiceProfileServiceImpl service = createService(mapper, tenantScope(), fileApi,
                enrollmentClient, ttsClient, sampleProcessingService);
        MockMultipartFile file = new MockMultipartFile("file", "speaker.mp4", "video/mp4", new byte[]{1, 2, 3});

        service.createVoice("My voice", true, file);

        verify(fileApi).createFile(aryEq(new byte[]{9, 8}), eq("speaker-voice.wav"),
                eq("tk/166/voice-profiles"), eq("audio/wav"));
    }

    @Test
    void resolveRejectsCrossTenantVoice() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        TkDataScopeService scopeService = tenantScope();
        TkVoiceProfileDO profile = readyProfile(9L, 200L);
        when(mapper.selectById(9L)).thenReturn(profile);
        doThrow(new IllegalStateException("cross tenant"))
                .when(scopeService).validateReadable(200L, null, null);
        TkVoiceProfileServiceImpl service = createService(mapper, scopeService);

        assertThrows(RuntimeException.class, () -> service.resolveReadyVoiceCode(9L));
        verify(scopeService).validateReadable(200L, null, null);
    }

    @Test
    void resolveRejectsVoiceThatIsNotReady() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        TkVoiceProfileDO profile = readyProfile(9L, 166L).setStatus(TkVoiceProfileStatusEnum.FAILED.getStatus());
        when(mapper.selectById(9L)).thenReturn(profile);
        TkVoiceProfileServiceImpl service = createService(mapper, tenantScope());

        assertThrows(RuntimeException.class, () -> service.resolveReadyVoiceCode(9L));
    }

    @Test
    void createMimoDesignVoiceStoresReusableProfileWithPreview() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        FileApi fileApi = mock(FileApi.class);
        when(fileApi.createFile(any(byte[].class), eq("preview.wav"), eq("tk/166/voice-profiles"), eq("audio/wav")))
                .thenReturn("https://cdn.example/mimo-preview.wav");
        TkMimoTtsClient mimoTtsClient = new FakeMimoTtsClient(new byte[]{3, 4}, "wav");
        TkVoiceProfileServiceImpl service = createService(mapper, tenantScope(), fileApi,
                new FakeEnrollmentClient(), new FakeDashScopeTtsClient(),
                new FakeSampleProcessingService(), mimoTtsClient);

        service.createMimoDesignVoice("Warm seller", "warm and confident", "female,commerce");

        ArgumentCaptor<TkVoiceProfileDO> captor = ArgumentCaptor.forClass(TkVoiceProfileDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals(166L, captor.getValue().getTenantId());
        assertEquals(TkTtsProviderEnum.MIMO, captor.getValue().getProvider());
        assertEquals("MIMO_DESIGN", captor.getValue().getSourceType());
        assertEquals(TkMimoVoiceModeEnum.VOICE_DESIGN, captor.getValue().getMimoVoiceMode());
        assertEquals("warm and confident", captor.getValue().getMimoVoicePrompt());
        assertEquals("female,commerce", captor.getValue().getTags());
        assertEquals("https://cdn.example/mimo-preview.wav", captor.getValue().getPreviewFileUrl());
        assertEquals(TkVoiceProfileStatusEnum.READY.getStatus(), captor.getValue().getStatus());
    }

    @Test
    void resolveMimoVoiceSelectionLoadsSavedVoiceMode() {
        TkVoiceProfileMapper mapper = mock(TkVoiceProfileMapper.class);
        TkVoiceProfileDO profile = new TkVoiceProfileDO()
                .setId(88L)
                .setProvider(TkTtsProviderEnum.MIMO)
                .setSourceType("MIMO_CLONE")
                .setMimoVoiceMode(TkMimoVoiceModeEnum.VOICE_CLONE)
                .setMimoSampleUrl("https://cdn.example/sample.wav")
                .setStatus(TkVoiceProfileStatusEnum.READY.getStatus())
                .setEnabled(true);
        profile.setTenantId(166L);
        when(mapper.selectById(88L)).thenReturn(profile);
        TkVoiceProfileServiceImpl service = createService(mapper, tenantScope());

        TkMimoVoiceSelection selection = service.resolveMimoVoiceSelection(88L,
                TkMimoVoiceModeEnum.PRESET, "Mia", "", "");

        assertEquals(TkMimoVoiceModeEnum.VOICE_CLONE, selection.getMode());
        assertEquals("https://cdn.example/sample.wav", selection.getSampleUrl());
    }

    private TkDataScopeService tenantScope() {
        TkDataScopeService scopeService = mock(TkDataScopeService.class);
        when(scopeService.getCurrentScope()).thenReturn(new TkUserScope(10L, 166L,
                TkUserLevelEnum.TENANT_ADMIN.getCode(), null));
        return scopeService;
    }

    private TkVoiceProfileDO readyProfile(Long id, Long tenantId) {
        TkVoiceProfileDO profile = new TkVoiceProfileDO().setId(id)
                .setVoiceCode("cosyvoice-v3.5-plus-demo-123")
                .setStatus(TkVoiceProfileStatusEnum.READY.getStatus())
                .setEnabled(true);
        profile.setTenantId(tenantId);
        return profile;
    }

    private TkVoiceProfileServiceImpl createService(TkVoiceProfileMapper mapper, TkDataScopeService scopeService) {
        return createService(mapper, scopeService, mock(FileApi.class),
                new FakeEnrollmentClient(), new FakeDashScopeTtsClient());
    }

    private TkVoiceProfileServiceImpl createService(TkVoiceProfileMapper mapper, TkDataScopeService scopeService,
                                                    FileApi fileApi, TkDashScopeVoiceEnrollmentClient enrollmentClient,
                                                    TkDashScopeTtsClient ttsClient) {
        TkVoiceSampleProcessingService sampleProcessingService = new FakeSampleProcessingService();
        return createService(mapper, scopeService, fileApi, enrollmentClient, ttsClient, sampleProcessingService);
    }

    private TkVoiceProfileServiceImpl createService(TkVoiceProfileMapper mapper, TkDataScopeService scopeService,
                                                    FileApi fileApi, TkDashScopeVoiceEnrollmentClient enrollmentClient,
                                                    TkDashScopeTtsClient ttsClient,
                                                    TkVoiceSampleProcessingService sampleProcessingService) {
        return createService(mapper, scopeService, fileApi, enrollmentClient, ttsClient, sampleProcessingService,
                new FakeMimoTtsClient(new byte[]{1}, "wav"));
    }

    private TkVoiceProfileServiceImpl createService(TkVoiceProfileMapper mapper, TkDataScopeService scopeService,
                                                    FileApi fileApi, TkDashScopeVoiceEnrollmentClient enrollmentClient,
                                                    TkDashScopeTtsClient ttsClient,
                                                    TkVoiceSampleProcessingService sampleProcessingService,
                                                    TkMimoTtsClient mimoTtsClient) {
        TkVoiceProfileServiceImpl service = new TkVoiceProfileServiceImpl();
        ReflectionTestUtils.setField(service, "voiceProfileMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", scopeService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "enrollmentClient", enrollmentClient);
        ReflectionTestUtils.setField(service, "ttsClient", ttsClient);
        ReflectionTestUtils.setField(service, "sampleProcessingService", sampleProcessingService);
        ReflectionTestUtils.setField(service, "mimoTtsClient", mimoTtsClient);
        return service;
    }

    private static class FakeMimoTtsClient extends TkMimoTtsClient {
        private final byte[] audio;
        private final String format;

        private FakeMimoTtsClient(byte[] audio, String format) {
            this.audio = audio;
            this.format = format;
        }

        @Override
        public byte[] synthesize(TkVoiceSynthesisRequest request) {
            return audio;
        }

        @Override
        public String audioFormat() {
            return format;
        }
    }

    private static class FakeEnrollmentClient extends TkDashScopeVoiceEnrollmentClient {
    }

    private static class FakeDashScopeTtsClient extends TkDashScopeTtsClient {
    }

    private static class FakeSampleProcessingService extends TkVoiceSampleProcessingService {
        private FakeSampleProcessingService() {
            super(null);
        }

        @Override
        public TkVoiceProcessedSample process(MultipartFile file) {
            try {
                return new TkVoiceProcessedSample(file.getBytes(), file.getOriginalFilename(), file.getContentType());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
