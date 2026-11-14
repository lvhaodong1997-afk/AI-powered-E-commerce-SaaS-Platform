package cn.iocoder.yudao.module.tk.service.automation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.controller.admin.automation.vo.TkAutomationVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import cn.iocoder.yudao.module.tk.service.voice.TkVoiceProfileService;
import cn.iocoder.yudao.module.tk.service.voice.TkMiniMaxVoiceDictionaryService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkVoiceProfileDO;
import cn.iocoder.yudao.module.tk.controller.admin.voice.vo.TkMiniMaxVoiceOptionRespVO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TkAutomationServiceTest {
    @Mock TkGenerationTaskService generationTaskService;
    @Mock TkGenerationOutputStorageService outputStorageService;
    @Mock TkBgmAssetService bgmAssetService;
    @Mock TkVoiceProfileService voiceProfileService;
    @Mock TkMiniMaxVoiceDictionaryService miniMaxVoiceDictionaryService;
    @InjectMocks TkAutomationService service;

    @Test void createPreservesBodyAndEnablesExplicitAudioAndSubtitleSettings() {
        TkAutomationVO.CreateReq request = new TkAutomationVO.CreateReq();
        request.setLibraryId(10L);
        request.setScriptText("完整正文，不含画像或结构说明。");
        request.setTtsProvider("MINIMAX");
        request.setVoiceCode("voice-1");
        request.setBgmAssetId(20L);
        allowBgm(request.getBgmAssetId());
        when(generationTaskService.createGenerationTask(any())).thenReturn(99L);
        assertEquals(99L, service.create(request).getTaskId());
        ArgumentCaptor<TkGenerationTaskCreateReqVO> captor = ArgumentCaptor.forClass(TkGenerationTaskCreateReqVO.class);
        verify(generationTaskService).createGenerationTask(captor.capture());
        TkGenerationTaskCreateReqVO actual = captor.getValue();
        assertEquals(request.getScriptText(), actual.getPromptText());
        assertEquals(request.getScriptText(), actual.getManualOriginalText());
        assertEquals("MANUAL", actual.getScriptMode());
        assertFalse(actual.getManualTranslationEnabled());
        assertEquals("zh-cn", actual.getTargetLanguage());
        assertEquals("FOLLOW_NARRATION", actual.getDurationMode());
        assertEquals("FULL_POOL_RANDOM", actual.getClipPlanMode());
        assertEquals(0.4D, actual.getBgmVolume());
        assertEquals(2, actual.getAudioMixVersion());
        assertEquals("extra_large", actual.getSubtitleFontSize());
        assertEquals("classic_white", actual.getSubtitleStyle());
        assertEquals("smart_safe", actual.getSubtitlePositionMode());
        assertTrue(actual.getSubtitleKeywordEnabled());
        assertTrue(actual.getSubtitleKaraokeEnabled());
    }

    @Test void insufficientCreditErrorIsPreserved() {
        TkAutomationVO.CreateReq request = new TkAutomationVO.CreateReq();
        request.setBgmAssetId(20L);
        allowBgm(20L);
        ServiceException error = new ServiceException(1060000029, "租户积分余额不足");
        when(generationTaskService.createGenerationTask(any())).thenThrow(error);
        assertSame(error, assertThrows(ServiceException.class, () -> service.create(request)));
    }

    @Test void failureIncludesReadableReasonAndNoDownloadLink() {
        when(generationTaskService.getGenerationTask(1L)).thenReturn(TkGenerationTaskDO.builder()
                .id(1L).status("FAILED").failCode("TTS_FAILED").failReason("配音生成失败").build());
        TkAutomationVO.TaskResp response = service.task(1L);
        assertTrue(response.getFailed());
        assertFalse(response.getCompleted());
        assertEquals("配音生成失败", response.getFailReason());
        assertNull(response.getDownloadUrl());
        verifyNoInteractions(outputStorageService);
    }

    @Test void downloadRefreshesLinkAfterExistingTaskAccessCheck() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder().id(1L).status("SUCCESS").outputUrl("old").build();
        when(generationTaskService.getGenerationTask(1L)).thenReturn(task);
        when(outputStorageService.refreshGeneratedAssetReadUrl(task, "old", "成片.mp4")).thenReturn("signed-url");
        assertEquals("signed-url", service.download(1L, "成片.mp4").getDownloadUrl());
    }

    @Test void failedOrInaccessibleTasksNeverGetADownloadLink() {
        when(generationTaskService.getGenerationTask(1L)).thenReturn(TkGenerationTaskDO.builder().status("PENDING").build());
        assertThrows(ServiceException.class, () -> service.download(1L, "video.mp4"));
        when(generationTaskService.getGenerationTask(2L)).thenThrow(new ServiceException(403, "无权访问"));
        assertThrows(ServiceException.class, () -> service.download(2L, "video.mp4"));
        verifyNoInteractions(outputStorageService);
    }

    @Test void downloadRejectsPathOrHeaderCharacters() {
        assertThrows(ServiceException.class, () -> service.download(1L, "../video.mp4"));
        assertThrows(ServiceException.class, () -> service.download(1L, "x\r\n.mp4"));
        verifyNoInteractions(generationTaskService, outputStorageService);
    }

    @Test void otherUsersBgmCannotBeSubmitted() {
        TkAutomationVO.CreateReq request = new TkAutomationVO.CreateReq();
        request.setBgmAssetId(20L);
        when(bgmAssetService.validateReadable(20L)).thenReturn(TkBgmAssetDO.builder().id(20L).build());
        assertThrows(ServiceException.class, () -> service.create(request));
        verifyNoInteractions(generationTaskService);
    }

    @Test void resourceListExcludesOtherUsersMusicAndUnavailableVoices() {
        TkBgmAssetDO own = TkBgmAssetDO.builder().id(20L).name("温馨居家01")
                .sourceType("USER").fileUrl("https://storage.example/upload/%E6%B8%A9%E9%A6%A8.mp3?token=secret").build();
        when(bgmAssetService.getAvailableList()).thenReturn(java.util.Arrays.asList(own,
                TkBgmAssetDO.builder().id(21L).name("他人的音乐").sourceType("USER").build()));
        when(bgmAssetService.canDelete(own)).thenReturn(true);
        java.util.List<TkAutomationVO.BgmResp> bgms = service.bgms();
        assertEquals(1, bgms.size());
        assertEquals("温馨居家01", bgms.get(0).getName());
        assertEquals("温馨.mp3", bgms.get(0).getFileName());

        when(voiceProfileService.getVoiceList()).thenReturn(java.util.Arrays.asList(
                TkVoiceProfileDO.builder().id(1L).provider("MIMO").name("可用复刻").status("READY").enabled(true).build(),
                TkVoiceProfileDO.builder().id(2L).status("READY").enabled(false).build()));
        TkMiniMaxVoiceOptionRespVO available = new TkMiniMaxVoiceOptionRespVO();
        available.setLabel("温柔学姐"); available.setValue("voice-1");
        TkMiniMaxVoiceOptionRespVO disabled = new TkMiniMaxVoiceOptionRespVO();
        disabled.setUnavailable(true);
        when(miniMaxVoiceDictionaryService.getOptions()).thenReturn(java.util.Arrays.asList(available, disabled));
        java.util.List<TkAutomationVO.VoiceResp> voices = service.voices();
        assertEquals(2, voices.size());
        assertEquals(1L, voices.get(0).getVoiceProfileId());
        assertEquals("voice-1", voices.get(1).getVoiceCode());
    }

    private void allowBgm(Long id) {
        TkBgmAssetDO asset = TkBgmAssetDO.builder().id(id).sourceType("USER").build();
        when(bgmAssetService.validateReadable(id)).thenReturn(asset);
        when(bgmAssetService.canDelete(asset)).thenReturn(true);
    }
}
