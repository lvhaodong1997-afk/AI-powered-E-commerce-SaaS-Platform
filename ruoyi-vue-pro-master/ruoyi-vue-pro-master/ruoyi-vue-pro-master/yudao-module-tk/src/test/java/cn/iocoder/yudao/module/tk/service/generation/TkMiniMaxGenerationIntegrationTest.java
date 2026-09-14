package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.controller.admin.generation.TkGenerationTaskController;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkVoicePreviewReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.enums.TkUserLevelEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGenerationPipelineService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkTtsProviderEnum;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceProviderRouter;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceSynthesisRequest;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkVoiceTtsClient;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkMimoVoiceSelection;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkMiniMaxGenerationIntegrationTest {

    @Test
    void generationCreateDefaultsToMiniMaxAndRetainsExplicitMimo() {
        assertEquals(TkTtsProviderEnum.MINIMAX, TkTtsProviderEnum.forNewTask(null));
        assertEquals(TkTtsProviderEnum.MIMO, TkTtsProviderEnum.forNewTask(TkTtsProviderEnum.MIMO));

        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        AtomicLong nextTaskId = new AtomicLong(100L);
        doAnswer(invocation -> {
            TkGenerationTaskDO task = invocation.getArgument(0);
            task.setId(nextTaskId.getAndIncrement());
            return 1;
        }).when(taskMapper).insert(any(TkGenerationTaskDO.class));
        TkMaterialLibraryService libraryService = mock(TkMaterialLibraryService.class);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Demo").build();
        library.setTenantId(166L);
        when(libraryService.validateMaterialLibraryReadable(10L)).thenReturn(library);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(10L, 166L,
                TkUserLevelEnum.TENANT_ADMIN.getCode(), null));
        TkGenerationPrecheckService precheckService = mock(TkGenerationPrecheckService.class);
        TkGenerationPrecheckRespVO precheck = new TkGenerationPrecheckRespVO();
        precheck.setPassed(true);
        when(precheckService.precheck(any(TkGenerationTaskCreateReqVO.class))).thenReturn(precheck);
        TkCreditService creditService = mock(TkCreditService.class);
        when(creditService.freezeForGenerationTask(166L)).thenReturn(900L, 901L);
        TkVoiceProfileService voiceProfileService = mock(TkVoiceProfileService.class);
        when(voiceProfileService.resolveMimoVoiceSelection(null, "PRESET", "Mia", null, null))
                .thenReturn(new TkMimoVoiceSelection("PRESET", "Mia", null, null));
        TkMiniMaxVoiceDictionaryService dictionaryService = mock(TkMiniMaxVoiceDictionaryService.class);
        when(dictionaryService.resolveVoiceCode(null, "friendly-alias")).thenReturn("canonical-voice-id");
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "libraryService", libraryService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationPipelineService", mock(TkGenerationPipelineService.class));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", mock(TkReferenceAnalysisService.class));
        ReflectionTestUtils.setField(service, "creditService", creditService);
        ReflectionTestUtils.setField(service, "businessLogService", mock(TkBusinessLogService.class));
        ReflectionTestUtils.setField(service, "precheckService", precheckService);
        ReflectionTestUtils.setField(service, "voiceProfileService", voiceProfileService);
        ReflectionTestUtils.setField(service, "miniMaxVoiceDictionaryService", dictionaryService);

        TkGenerationTaskCreateReqVO defaultRequest = baseGenerationRequest()
                .setVoiceCode("friendly-alias");
        TkGenerationTaskCreateReqVO mimoRequest = baseGenerationRequest()
                .setTtsProvider(TkTtsProviderEnum.MIMO);
        mimoRequest.setMimoVoiceMode("PRESET");
        mimoRequest.setMimoVoiceCode("Mia");

        service.createGenerationTask(defaultRequest);
        service.createGenerationTask(mimoRequest);

        ArgumentCaptor<TkGenerationTaskDO> taskCaptor = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
        verify(taskMapper, times(2)).insert(taskCaptor.capture());
        List<TkGenerationTaskDO> tasks = taskCaptor.getAllValues();
        assertEquals(TkTtsProviderEnum.MINIMAX, tasks.get(0).getTtsProvider());
        assertEquals("canonical-voice-id", tasks.get(0).getVoiceCode());
        assertEquals(TkTtsProviderEnum.MIMO, tasks.get(1).getTtsProvider());
        assertEquals("Mia", tasks.get(1).getMimoVoiceCode());
        verify(dictionaryService).resolveVoiceCode(null, "friendly-alias");
        verify(voiceProfileService).resolveMimoVoiceSelection(null, "PRESET", "Mia", null, null);
    }

    @Test
    void previewControllerRejectsMiniMaxPaidSynthesis() {
        AtomicReference<TkVoiceSynthesisRequest> synthesisRequest = new AtomicReference<>();
        TkVoiceTtsClient client = new TkVoiceTtsClient() {
            @Override
            public String provider() {
                return TkTtsProviderEnum.MINIMAX;
            }

            @Override
            public String audioFormat() {
                return "mp3";
            }

            @Override
            public byte[] synthesize(TkVoiceSynthesisRequest request) {
                synthesisRequest.set(request);
                return new byte[]{7, 8, 9};
            }
        };
        TkGenerationTaskController controller = new TkGenerationTaskController();
        ReflectionTestUtils.setField(controller, "voiceProviderRouter",
                new TkVoiceProviderRouter(Collections.singletonList(client)));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> controller.previewVoice(new TkVoicePreviewReqVO()
                .setTtsProvider(TkTtsProviderEnum.MINIMAX)
                .setVoiceCode("friendly-alias")
                .setTargetLanguage("en")));

        org.junit.jupiter.api.Assertions.assertNull(synthesisRequest.get());
    }

    private TkGenerationTaskCreateReqVO baseGenerationRequest() {
        return new TkGenerationTaskCreateReqVO()
                .setLibraryId(10L)
                .setSourceUrl("https://www.tiktok.com/@demo/video/1")
                .setPromptText("Buy now")
                .setTargetLanguage("en")
                .setReferenceDuration(15);
    }
}
