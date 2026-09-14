package cn.iocoder.yudao.module.tk.service.voice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.dict.DictDataDO;
import cn.iocoder.yudao.module.system.service.dict.DictDataService;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMiniMaxVoiceOptionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceFavoriteDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkVoiceFavoriteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkMiniMaxVoiceDictionaryServiceTest {

    private final DictDataService dictDataService = mock(DictDataService.class);
    private final TkVoiceFavoriteMapper favoriteMapper = mock(TkVoiceFavoriteMapper.class);
    private final TkMiniMaxVoiceDictionaryService service =
            new TkMiniMaxVoiceDictionaryService(dictDataService, favoriteMapper);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void resolveVoiceCodeRejectsProfileId() {
        assertThrows(ServiceException.class, () -> service.resolveVoiceCode(1L, "German_SweetLady"));
        verifyNoInteractions(dictDataService);
    }

    @Test
    void resolveVoiceCodeUsesMetadataDefaultAndSupportsAliasAndVoiceId() {
        DictDataDO defaultVoice = voice(7L, "warm-alias", "Warm Voice",
                "{\"voiceId\":\"Chinese (Mandarin)_Warm Voice\",\"model\":\"speech-2.8-turbo\","
                        + "\"language\":\"zh-CN\",\"country\":\"中国\",\"isDefault\":true}");
        when(dictDataService.getDictDataList(0, "ai_tts_voice"))
                .thenReturn(Collections.singletonList(defaultVoice));

        assertEquals("Chinese (Mandarin)_Warm Voice", service.resolveVoiceCode(null, null));
        assertEquals("Chinese (Mandarin)_Warm Voice", service.resolveVoiceCode(null, "warm-alias"));
        assertEquals("Chinese (Mandarin)_Warm Voice",
                service.resolveVoiceCode(null, "Chinese (Mandarin)_Warm Voice"));
        assertThrows(ServiceException.class, () -> service.resolveVoiceCode(null, "WARM-ALIAS"));
    }

    @Test
    void getOptionsSkipsInvalidMetadataAndMarksCurrentFavorite() {
        DictDataDO invalid = voice(8L, "bad", "Bad", "{not-json");
        DictDataDO valid = voice(9L, "French_Female_News Anchor", "Patient Female Presenter",
                "{\"voiceId\":\"French_Female_News Anchor\",\"model\":\"speech-2.8-turbo\","
                        + "\"language\":\"fr-FR\",\"country\":\"法国\",\"gender\":\"female\","
                        + "\"description\":\"Patient Female Presenter\",\"isDefault\":false}");
        when(dictDataService.getDictDataList(0, "ai_tts_voice")).thenReturn(Arrays.asList(invalid, valid));
        when(favoriteMapper.selectListByScope(22L, 33L, "MINIMAX"))
                .thenReturn(Collections.singletonList(new TkVoiceFavoriteDO().setVoiceCode("French_Female_News Anchor")));
        TenantContextHolder.setTenantId(22L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(33L);
            List<TkMiniMaxVoiceOptionRespVO> options = service.getOptions();

            assertEquals(1, options.size());
            assertEquals("French_Female_News Anchor", options.get(0).getVoiceId());
            assertEquals("https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/"
                    + "French_Female_News%20Anchor.mp3", options.get(0).getPreviewUrl());
            assertTrue(options.get(0).getFavorite());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildTtsRequestUsesEnabledConfigAndReferenceDefaults() {
        when(dictDataService.getDictDataList(0, "ai_tts_config")).thenReturn(Arrays.asList(
                config("speed", "1.5"), config("output_format", "hex"), config("audio_format", "wav"),
                config("channel", "2"), config("subtitle_enable", "true"), config("aigc_watermark", "true")));

        Map<String, Object> request = service.buildTtsRequest("hello", "German_SweetLady");
        Map<String, Object> voiceSetting = (Map<String, Object>) request.get("voice_setting");
        Map<String, Object> audioSetting = (Map<String, Object>) request.get("audio_setting");

        assertEquals("speech-2.8-turbo", request.get("model"));
        assertEquals("hello", request.get("text"));
        assertEquals("url", request.get("output_format"));
        assertEquals(false, request.get("subtitle_enable"));
        assertEquals(false, request.get("aigc_watermark"));
        assertEquals("German_SweetLady", voiceSetting.get("voice_id"));
        assertEquals(new BigDecimal("1.5"), voiceSetting.get("speed"));
        assertEquals("mp3", audioSetting.get("format"));
        assertEquals(1, audioSetting.get("channel"));
        assertFalse(request.containsKey("api_key"));
    }

    @Test
    void buildTtsRequestRejectsInvalidTextAndVoiceIdBounds() {
        when(dictDataService.getDictDataList(0, "ai_tts_config")).thenReturn(Collections.emptyList());

        assertThrows(ServiceException.class, () -> service.buildTtsRequest(" ", "voice"));
        assertThrows(ServiceException.class, () -> service.buildTtsRequest(StrUtil.repeat('x', 10_001), "voice"));
        assertThrows(ServiceException.class, () -> service.buildTtsRequest("hello", " "));
        assertThrows(ServiceException.class, () -> service.buildTtsRequest("hello", StrUtil.repeat('v', 129)));
        assertDoesNotThrow(() -> service.buildTtsRequest(StrUtil.repeat('x', 10_000), StrUtil.repeat('v', 128)));
    }

    @Test
    void favoriteWriteUsesAuthenticatedScopeAndCanonicalVoiceId() {
        when(dictDataService.getDictDataList(0, "ai_tts_voice")).thenReturn(Collections.singletonList(
                voice(10L, "alias", "Voice", "{\"voiceId\":\"ActualVoice\",\"language\":\"en-US\","
                        + "\"country\":\"美国\"}")));
        TenantContextHolder.setTenantId(44L);

        try (MockedStatic<SecurityFrameworkUtils> security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(55L);
            service.addFavorite("alias");
            service.removeFavorite("ActualVoice");
        }

        verify(favoriteMapper).insertIgnore(argThat(item -> item.getTenantId().equals(44L)
                && item.getUserId().equals(55L) && item.getProvider().equals("MINIMAX")
                && item.getVoiceCode().equals("ActualVoice")));
        verify(favoriteMapper).deleteByScope(44L, 55L, "MINIMAX", "ActualVoice");
    }

    private static DictDataDO voice(Long id, String value, String label, String remark) {
        return new DictDataDO().setId(id).setValue(value).setLabel(label).setDictType("ai_tts_voice")
                .setStatus(0).setSort(id.intValue()).setRemark(remark);
    }

    private static DictDataDO config(String key, String value) {
        return new DictDataDO().setValue(key).setLabel(key).setDictType("ai_tts_config")
                .setStatus(0).setRemark("{\"value\":\"" + value + "\"}");
    }
}
