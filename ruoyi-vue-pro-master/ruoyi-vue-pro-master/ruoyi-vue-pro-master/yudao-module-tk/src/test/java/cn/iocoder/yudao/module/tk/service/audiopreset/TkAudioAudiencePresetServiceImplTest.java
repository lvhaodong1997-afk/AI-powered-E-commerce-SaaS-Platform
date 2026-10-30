package cn.iocoder.yudao.module.tk.service.audiopreset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.iocoder.yudao.module.tk.controller.admin.audiopreset.vo.TkAudioAudiencePresetSaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkAudioAudiencePresetDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkAudioAudiencePresetMapper;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TkAudioAudiencePresetServiceImplTest {
    private TkAudioAudiencePresetMapper mapper;
    private TkAudioAudiencePresetServiceImpl service;
    private TkVoiceProfileService voices;
    private TkBgmAssetService bgm;

    @BeforeEach
    void setup() {
        mapper = mock(TkAudioAudiencePresetMapper.class);
        voices = mock(TkVoiceProfileService.class);
        bgm = mock(TkBgmAssetService.class);
        service = new TkAudioAudiencePresetServiceImpl();
        TkDataScopeService scopes = mock(TkDataScopeService.class);
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "COMPANY_USER", 200L));
        ReflectionTestUtils.setField(service, "presetMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", scopes);
        ReflectionTestUtils.setField(service, "voiceProfileService", voices);
        ReflectionTestUtils.setField(service, "bgmAssetService", bgm);
    }

    private TkAudioAudiencePresetDO row() {
        TkAudioAudiencePresetDO row = new TkAudioAudiencePresetDO();
        row.setId(1L);
        row.setTenantId(100L);
        row.setCompanyId(200L);
        row.setUserId(7L);
        row.setVersion(3);
        row.setAudienceCode("SILVER");
        row.setConfigStatus("READY");
        row.setTtsProvider("DASHSCOPE");
        row.setVoiceConfig("{}");
        row.setBgmEnabled(false);
        when(mapper.selectById(1L)).thenReturn(row);
        return row;
    }

    @Test
    void rejectsAnotherUsersPreset() {
        row().setUserId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.get(1L));
    }

    @Test
    void rejectsAnotherTenantEvenWithSameUser() {
        row().setTenantId(101L);
        assertThrows(IllegalArgumentException.class, () -> service.get(1L));
    }

    @Test
    void rejectsDifferentCompany() {
        row();
        assertThrows(
                IllegalArgumentException.class, () -> service.resolveForGeneration(1L, 3, 201L));
    }

    @Test
    void rejectsStaleVersionBeforeResolution() {
        row();
        assertThrows(
                IllegalArgumentException.class, () -> service.resolveForGeneration(1L, 2, 200L));
        verifyNoInteractions(voices, bgm);
    }

    @Test
    void incompletePresetCannotGenerate() {
        row().setConfigStatus("INCOMPLETE");
        assertThrows(
                IllegalArgumentException.class, () -> service.resolveForGeneration(1L, 3, 200L));
    }

    @Test
    void invalidVoiceRejectedWithoutWriting() {
        TkAudioAudiencePresetSaveReqVO req = new TkAudioAudiencePresetSaveReqVO();
        req.setAudienceCode("SILVER");
        req.setTtsProvider("DASHSCOPE");
        req.setVoiceCode("bad");
        when(voices.resolveVoiceSelection(null, "bad"))
                .thenThrow(new IllegalArgumentException("invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.save(req));
        verify(mapper, never()).insert(any(TkAudioAudiencePresetDO.class));
    }

    @Test
    void enabledBgmMustExist() {
        TkAudioAudiencePresetSaveReqVO req = new TkAudioAudiencePresetSaveReqVO();
        req.setAudienceCode("SILVER");
        req.setTtsProvider("DASHSCOPE");
        req.setBgmEnabled(true);
        req.setBgmAssetId(9L);
        req.setBgmVolume(0.2);
        when(bgm.validateReadableForUpdate(9L)).thenThrow(new IllegalArgumentException("missing"));
        assertThrows(IllegalArgumentException.class, () -> service.save(req));
        verify(mapper, never()).insert(any(TkAudioAudiencePresetDO.class));
    }

    @Test
    void resetRequiresCurrentVersion() {
        row();
        assertThrows(IllegalArgumentException.class, () -> service.reset(1L, 2));
    }

    @Test
    void successfulSaveStoresOnlyFlatVoiceConfigurationAndNormalizedScope() {
        TkAudioAudiencePresetSaveReqVO req = new TkAudioAudiencePresetSaveReqVO();
        req.setAudienceCode("SILVER");
        req.setTtsProvider("DASHSCOPE");
        req.setVoiceCode("system");
        when(voices.resolveVoiceSelection(null, "system")).thenReturn("resolved");
        when(mapper.selectOwned(100L, 200L, 7L)).thenReturn(java.util.Collections.emptyList());
        when(mapper.insert(any(TkAudioAudiencePresetDO.class)))
                .thenAnswer(
                        call -> {
                            ((TkAudioAudiencePresetDO) call.getArgument(0)).setId(10L);
                            return 1;
                        });
        assertEquals(10L, service.save(req));
        org.mockito.ArgumentCaptor<TkAudioAudiencePresetDO> capture =
                org.mockito.ArgumentCaptor.forClass(TkAudioAudiencePresetDO.class);
        verify(mapper).insert(capture.capture());
        TkAudioAudiencePresetDO saved = capture.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals(200L, saved.getCompanyId());
        assertEquals(100L, saved.getTenantId());
        assertEquals(1, saved.getVersion());
        assertEquals("READY", saved.getConfigStatus());
        assertFalse(saved.getBgmEnabled());
        assertTrue(saved.getVoiceConfig().contains("\"voiceCode\":\"resolved\""));
        assertFalse(saved.getVoiceConfig().contains("audienceCode"));
        assertFalse(saved.getVoiceConfig().contains("bgm"));
    }

    @Test
    void concurrentUpdateDoesNotSilentlyOverwrite() {
        row();
        TkAudioAudiencePresetSaveReqVO req = new TkAudioAudiencePresetSaveReqVO();
        req.setId(1L);
        req.setVersion(3);
        req.setAudienceCode("SILVER");
        req.setTtsProvider("DASHSCOPE");
        assertThrows(IllegalArgumentException.class, () -> service.save(req));
        verify(mapper)
                .update(
                        any(TkAudioAudiencePresetDO.class),
                        any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    void resetPreservesSlotAndIncrementsVersionWithCompareAndSet() {
        row();
        when(mapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(1);
        service.reset(1L, 3);
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> capture =
                org.mockito.ArgumentCaptor.forClass(
                        com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).update(isNull(), capture.capture());
        String segment = capture.getValue().getSqlSegment();
        assertTrue(segment.contains("tenant_id"));
        assertTrue(segment.contains("company_id"));
        assertTrue(segment.contains("user_id"));
        assertTrue(segment.contains("version"));
        String set =
                ((com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper) capture.getValue())
                        .getSqlSet();
        assertTrue(set.contains("config_status"));
        assertTrue(set.contains("bgm_asset_id"));
        assertTrue(set.contains("version"));
        verify(mapper, never()).deleteById(any(java.io.Serializable.class));
    }
}
