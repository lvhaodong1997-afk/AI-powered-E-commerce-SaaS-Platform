package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationBatchMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.audiopreset.TkAudioAudiencePresetService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGenerationPipelineService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkGenerationAudioSnapshotTest {
    @Test
    void selectedPresetOverridesAudioAndPreservesExplicitVoiceOff() {
        Fixture f = new Fixture();
        f.request.setVoiceEnabled(false);
        f.service.createGenerationTask(f.request);
        TkGenerationTaskDO task = f.created(1).get(0);
        assertEquals(12L, task.getAudioPresetId());
        assertEquals(4, task.getAudioPresetVersion());
        assertEquals("SILVER", task.getAudienceCode());
        assertEquals(2, task.getAudioMixVersion());
        assertEquals(0.65D, task.getBgmVolume());
        assertEquals("https://example.invalid/preset.mp3", task.getBgmUrl());
        assertFalse(task.getVoiceEnabled());
        assertEquals("manual-voice", f.request.getVoiceCode(), "applying a preset must not mutate manual input");
    }

    @Test
    void batchFreezesPresetMusicAndResolvedVoiceOnce() {
        Fixture f = new Fixture();
        f.request.setScriptOptionIds(Arrays.asList(101L));
        f.request.setVideosPerScript(2);
        f.service.createGenerationTasks(f.request);
        List<TkGenerationTaskDO> tasks = f.created(2);
        for (TkGenerationTaskDO task : tasks) {
            assertEquals("resolved-first", task.getVoiceCode());
            assertEquals("https://example.invalid/preset.mp3", task.getBgmUrl());
            assertEquals(0.65D, task.getBgmVolume());
            assertEquals(4, task.getAudioPresetVersion());
        }
        verify(f.presets, times(1)).resolveForGeneration(12L, 4, 20L);
        verify(f.assets, times(1)).validateReadable(88L);
        verify(f.voices, times(1)).resolveVoiceSelection(null, "preset-voice");
    }

    @Test
    void staleOrInaccessiblePresetFailsBeforeAnyCreditFreezeOrTaskInsert() {
        Fixture f = new Fixture();
        when(f.presets.resolveForGeneration(12L, 4, 20L)).thenThrow(new IllegalArgumentException("预设已更新"));
        cn.iocoder.yudao.framework.common.exception.ServiceException error = assertThrows(
                cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> f.service.createGenerationTask(f.request));
        assertEquals(400, error.getCode());
        assertEquals("预设已更新", error.getMessage());
        verify(f.credits, never()).freezeForGenerationTask(any());
        verify(f.tasks, never()).insert(any(TkGenerationTaskDO.class));
    }

    @Test
    void unselectedAudienceKeepsManualAudioAndRejectsUnknownVersionBeforeCharge() {
        Fixture f = new Fixture();
        f.request.setAudioPresetId(null);
        f.request.setAudioPresetVersion(null);
        f.request.setBgmVolume(1D);
        f.service.createGenerationTask(f.request);
        TkGenerationTaskDO task = f.created(1).get(0);
        assertEquals("manual-voice", task.getVoiceCode());
        assertEquals(1D, task.getBgmVolume());
        assertNull(task.getAudioPresetId());
        verifyNoInteractions(f.presets);

        Fixture invalid = new Fixture();
        invalid.request.setAudioPresetId(null);
        invalid.request.setBgmEnabled(false);
        invalid.request.setAudioMixVersion(99);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> invalid.service.createGenerationTask(invalid.request));
        verify(invalid.credits, never()).freezeForGenerationTask(any());
    }

    @Test
    void newMixSupportsFullMusicGainAndTrueSilence() {
        assertEquals(1D, selectedVolume(1D, 2));
        assertEquals(0D, selectedVolume(0D, 2));
    }

    @Test
    void legacyClientRetainsExistingThirtyPercentCap() {
        assertEquals(0.30D, selectedVolume(1D, null));
        assertEquals(0.01D, selectedVolume(0D, 1));
    }

    @Test
    void rejectsUnknownMixVersionAndNonFiniteGain() {
        assertThrows(IllegalArgumentException.class, () -> selectedVolume(0.1D, 3));
        assertThrows(IllegalArgumentException.class, () -> selectedVolume(Double.NaN, 2));
    }

    @Test
    void presetAndMixMetadataSurvivesBatchRequestCopyAndTaskPersistence() {
        String json = "{\"audioPresetId\":12,\"audioPresetVersion\":4,\"audioMixVersion\":2,\"audienceCode\":\"SILVER\"}";
        TkGenerationTaskCreateReqVO req = JsonUtils.parseObject(json, TkGenerationTaskCreateReqVO.class);
        TkGenerationTaskCreateReqVO copy = ReflectionTestUtils.invokeMethod(new TkGenerationTaskServiceImpl(), "copyCreateReqVO", req);
        assertEquals(12L, JsonUtils.parseTree(JsonUtils.toJsonString(copy)).path("audioPresetId").asLong());
        assertEquals(4, JsonUtils.parseTree(JsonUtils.toJsonString(copy)).path("audioPresetVersion").asInt());
        assertEquals(2, JsonUtils.parseTree(JsonUtils.toJsonString(copy)).path("audioMixVersion").asInt());
        TkGenerationTaskDO task = JsonUtils.parseObject(json, TkGenerationTaskDO.class);
        assertEquals("SILVER", JsonUtils.parseTree(JsonUtils.toJsonString(task)).path("audienceCode").asText());
        assertEquals(2, JsonUtils.parseTree(JsonUtils.toJsonString(task)).path("audioMixVersion").asInt());
    }

    private double selectedVolume(Double volume, Integer version) {
        TkGenerationTaskCreateReqVO req = JsonUtils.parseObject("{\"bgmEnabled\":true,\"bgmAssetId\":9,\"audioMixVersion\":"
                + version + "}", TkGenerationTaskCreateReqVO.class);
        req.setBgmVolume(volume);
        TkBgmAssetService assets = mock(TkBgmAssetService.class);
        when(assets.validateReadable(9L)).thenReturn(TkBgmAssetDO.builder().id(9L).sourceType("USER").fileUrl("https://example.invalid/music.mp3").build());
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        ReflectionTestUtils.setField(service, "bgmAssetService", assets);
        Object result = ReflectionTestUtils.invokeMethod(service, "resolveBgmSelection", req);
        return (Double) ReflectionTestUtils.getField(result, "volume");
    }

    private static class Fixture {
        final TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        final TkAudioAudiencePresetService presets = mock(TkAudioAudiencePresetService.class);
        final TkGenerationTaskMapper tasks = mock(TkGenerationTaskMapper.class);
        final TkGenerationBatchMapper batches = mock(TkGenerationBatchMapper.class);
        final TkBgmAssetService assets = mock(TkBgmAssetService.class);
        final TkVoiceProfileService voices = mock(TkVoiceProfileService.class);
        final TkCreditService credits = mock(TkCreditService.class);
        final TkGenerationTaskCreateReqVO request = new TkGenerationTaskCreateReqVO();

        Fixture() {
            TkMaterialLibraryService libraries = mock(TkMaterialLibraryService.class);
            TkDataScopeService scope = mock(TkDataScopeService.class);
            TkGenerationPrecheckService precheck = mock(TkGenerationPrecheckService.class);
            TkReferenceAnalysisService references = mock(TkReferenceAnalysisService.class);
            ReflectionTestUtils.setField(service, "audioAudiencePresetService", presets);
            ReflectionTestUtils.setField(service, "taskMapper", tasks);
            ReflectionTestUtils.setField(service, "batchMapper", batches);
            ReflectionTestUtils.setField(service, "libraryService", libraries);
            ReflectionTestUtils.setField(service, "dataScopeService", scope);
            ReflectionTestUtils.setField(service, "bgmAssetService", assets);
            ReflectionTestUtils.setField(service, "voiceProfileService", voices);
            ReflectionTestUtils.setField(service, "creditService", credits);
            ReflectionTestUtils.setField(service, "precheckService", precheck);
            ReflectionTestUtils.setField(service, "referenceAnalysisService", references);
            ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
            ReflectionTestUtils.setField(service, "businessLogService", mock(TkBusinessLogService.class));
            ReflectionTestUtils.setField(service, "generationPipelineService", mock(TkGenerationPipelineService.class));
            TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).companyId(20L).name("Demo").build();
            library.setTenantId(8L);
            when(libraries.validateMaterialLibraryReadable(10L)).thenReturn(library);
            when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 8L, "COMPANY_USER", 20L));
            TkGenerationPrecheckRespVO check = new TkGenerationPrecheckRespVO();
            check.setPassed(true);
            when(precheck.precheck(any())).thenReturn(check);
            when(credits.freezeForGenerationTask(8L)).thenReturn(900L);
            request.setLibraryId(10L);
            request.setMaterialPurpose("LEAD_GENERATION");
            request.setScriptMode("MANUAL");
            request.setPromptText("A short voice script");
            request.setTtsProvider("DASHSCOPE");
            request.setVoiceCode("manual-voice");
            request.setVoiceEnabled(true);
            request.setAudioPresetId(12L);
            request.setAudioPresetVersion(4);
            request.setAudioMixVersion(2);
            request.setBgmEnabled(true);
            request.setBgmAssetId(88L);
            request.setBgmVolume(0.1D);
            TkAudioAudiencePresetDO preset = new TkAudioAudiencePresetDO();
            preset.setId(12L);
            preset.setVersion(4);
            preset.setAudienceCode("SILVER");
            preset.setTtsProvider("DASHSCOPE");
            preset.setVoiceConfig("{\"voiceCode\":\"preset-voice\"}");
            preset.setBgmEnabled(true);
            preset.setBgmAssetId(88L);
            preset.setBgmVolume(0.65D);
            when(presets.resolveForGeneration(12L, 4, 20L)).thenReturn(preset);
            TkBgmAssetDO music = TkBgmAssetDO.builder().id(88L).sourceType("USER").fileUrl("https://example.invalid/preset.mp3").build();
            when(assets.validateReadable(88L)).thenReturn(music);
            when(voices.resolveVoiceSelection(null, "manual-voice")).thenReturn("manual-voice");
            when(voices.resolveVoiceSelection(null, "preset-voice")).thenReturn("resolved-first", "resolved-second");
            when(references.validateScriptOptionReadable(101L)).thenReturn(TkReferenceScriptOptionDO.builder().id(101L).libraryId(10L).build());
            doAnswer(call -> { ((TkGenerationTaskDO) call.getArgument(0)).setId(100L); return 1; }).when(tasks).insert(any(TkGenerationTaskDO.class));
            doAnswer(call -> { ((TkGenerationBatchDO) call.getArgument(0)).setId(700L); return 1; }).when(batches).insert(any(TkGenerationBatchDO.class));
            TkGenerationBatchDO batch = TkGenerationBatchDO.builder().id(700L).expectedVideoCount(2).build();
            batch.setTenantId(8L);
            when(batches.selectById(700L)).thenReturn(batch);
            when(tasks.selectListByBatchId(any(), any())).thenReturn(Collections.emptyList());
        }

        List<TkGenerationTaskDO> created(int count) {
            ArgumentCaptor<TkGenerationTaskDO> captured = ArgumentCaptor.forClass(TkGenerationTaskDO.class);
            verify(tasks, times(count)).insert(captured.capture());
            return captured.getAllValues();
        }
    }
}
