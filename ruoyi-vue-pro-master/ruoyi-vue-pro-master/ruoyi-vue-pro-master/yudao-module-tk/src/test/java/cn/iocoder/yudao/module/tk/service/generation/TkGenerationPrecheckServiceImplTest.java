package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationPrecheckRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkGenerationPrecheckServiceImplTest {

    @Test
    void precheckLeadGenerationWarnsWhenGeneralIsMissing() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "ATTENTION", "S2_PAIN"),
                material(3L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 2L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 2L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 2L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertEquals(8, result.getSegmentSummary().size());
        TkGenerationPrecheckRespVO.PrecheckIssue durationWarning = result.getWarnings().stream()
                .filter(issue -> "MATERIAL_DURATION_NOT_ENOUGH".equals(issue.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(15, durationWarning.getRequiredDuration());
        assertEquals(14, durationWarning.getActualDuration());
        assertEquals(1, durationWarning.getMissingDuration());
        assertTrue(durationWarning.getActionHint().contains("补充素材"));

        TkGenerationPrecheckRespVO.PrecheckIssue generalWarning = result.getWarnings().stream()
                .filter(issue -> "SEGMENT_GENERAL_MISSING".equals(issue.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("GENERAL", generalWarning.getSegmentType());
        assertEquals("通用素材", generalWarning.getSegmentName());
        assertTrue(generalWarning.getActionHint().contains("通用素材"));
    }

    @Test
    void precheckLeadGenerationPassesWhenAllEightSegmentsHaveMaterial() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "ATTENTION", "S2_PAIN"),
                material(3L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 2L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 2L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 2L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(8L, 2L, "GENERAL", "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertEquals(8, result.getSegmentSummary().size());
    }

    @Test
    void precheckFailsWhenMaterialLibraryHasNoAvailableVideo() {
        TkGenerationPrecheckServiceImpl service = createService(Collections.emptyList());
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertFalse(result.getPassed());
        assertEquals("MATERIAL_EMPTY", result.getErrors().get(0).getCode());
        assertEquals(0, result.getMaterialSummary().getAvailableCount());
    }

    @Test
    void precheckPassesWhenMaterialDurationCoversTargetDuration() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(2L, 8L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 5L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().isEmpty());
        assertEquals(3, result.getMaterialSummary().getAvailableCount());
        assertEquals(17, result.getMaterialSummary().getTotalDuration());
        assertEquals(15, result.getMaterialSummary().getTargetDuration());
    }

    @Test
    void precheckPassesWhenVoiceSelectionIsMissingBecauseDefaultVoiceWillBeUsed() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(2L, 8L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 5L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);
        reqVO.setVoiceCode(null);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().stream()
                .noneMatch(issue -> "VOICE_REQUIRED".equals(issue.getCode())));
    }

    @Test
    void precheckPassesWithoutOpeningVideoWhenS1HookPoolCoversOpeningSegment() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 3L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(3L, 6L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 4L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);
        reqVO.setOpeningVideoUrl("");
        reqVO.setOpeningVideoName("");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().stream()
                .noneMatch(issue -> "OPENING_VIDEO_INVALID".equals(issue.getCode())));
        assertEquals(0, result.getSegmentSummary().stream()
                .filter(item -> "S1_HOOK".equals(item.getSegmentType()))
                .findFirst()
                .orElseThrow()
                .getMissingDuration());
    }

    @Test
    void precheckReturnsPhaseSummaryButDoesNotUsePhaseAsGenerationSegment() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "PRODUCT_SHOW", "S2_PAIN"),
                material(2L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(3L, 8L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 5L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(20);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertEquals(0, result.getPhaseSummary().getAttentionDuration());
        assertEquals(16, result.getPhaseSummary().getProductShowDuration());
        assertEquals(5, result.getPhaseSummary().getResultEffectDuration());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void precheckReturnsSegmentSummaryAndWarnsWhenRequiredSegmentMissing() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 8L, "ATTENTION", "S1_HOOK"),
                material(2L, 12L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 12L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(30);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertEquals(7, result.getSegmentSummary().size());
        assertEquals(0, result.getSegmentSummary().get(2).getDuration());
        assertEquals("S3_REVEAL", result.getSegmentSummary().get(2).getSegmentType());
        assertTrue(result.getWarnings().stream()
                .anyMatch(issue -> "SEGMENT_S3_REVEAL_INSUFFICIENT".equals(issue.getCode())));
    }

    @Test
    void precheckWarnsWhenOnlyUsagePhaseMaterialsExistWithoutExplicitSegments() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 20L, "ATTENTION"),
                material(2L, 20L, "PRODUCT_SHOW"),
                material(3L, 20L, "RESULT_EFFECT")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertEquals(0, result.getSegmentSummary().get(2).getDuration());
        assertTrue(result.getWarnings().stream()
                .anyMatch(issue -> "SEGMENT_S3_REVEAL_INSUFFICIENT".equals(issue.getCode())));
    }

    @Test
    void precheckUsesFallbackWarningsForMissingKeySegments() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 8L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(2L, 12L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 12L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(30);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getWarnings().stream()
                .anyMatch(issue -> "SEGMENT_S2_PAIN_INSUFFICIENT".equals(issue.getCode())));
        assertTrue(result.getErrors().stream()
                .noneMatch(issue -> "SEGMENT_S2_PAIN_INSUFFICIENT".equals(issue.getCode())));
    }

    @Test
    void precheckUsesCustomSegmentDurationsWhenProvided() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 5L, "ATTENTION", "S1_HOOK"),
                material(2L, 8L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 10L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(20);
        reqVO.setOpeningVideoUrl("");
        reqVO.setSegmentDurationConfig("["
                + "{\"segmentType\":\"S1_HOOK\",\"duration\":2},"
                + "{\"segmentType\":\"S4_DEMO\",\"duration\":10},"
                + "{\"segmentType\":\"S5_PROOF\",\"duration\":8}"
                + "]");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getWarnings().stream()
                .anyMatch(issue -> "SEGMENT_S4_DEMO_INSUFFICIENT".equals(issue.getCode())
                        && issue.getMessage().contains("还缺 2 秒")));
        assertTrue(result.getWarnings().stream()
                .noneMatch(issue -> "SEGMENT_S2_PAIN_INSUFFICIENT".equals(issue.getCode())));
    }

    @Test
    void precheckReturnsActionableSegmentGapDetails() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(2L, 6L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 3L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(15);

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        TkGenerationPrecheckRespVO.PrecheckIssue issue = result.getWarnings().stream()
                .filter(item -> "SEGMENT_S5_PROOF_INSUFFICIENT".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("效果证明素材不足，还差 1 秒", issue.getTitle());
        assertEquals("S5_PROOF", issue.getSegmentType());
        assertEquals("效果证明", issue.getSegmentName());
        assertEquals(4, issue.getRequiredDuration());
        assertEquals(3, issue.getActualDuration());
        assertEquals(1, issue.getMissingDuration());
        assertTrue(issue.getActionHint().contains("效果展示"));

        TkGenerationPrecheckRespVO.SegmentSummaryItem proofSummary = result.getSegmentSummary().stream()
                .filter(item -> "S5_PROOF".equals(item.getSegmentType()))
                .findFirst()
                .orElseThrow();
        assertEquals(4, proofSummary.getRequiredDuration());
        assertEquals(1, proofSummary.getMissingDuration());
    }

    @Test
    void precheckFullPoolRandomModeWarnsWhenMaterialDurationIsShorterThanTarget() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "GENERAL"),
                material(2L, 6L, "GENERAL"),
                material(3L, 7L, "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(20);
        reqVO.setOpeningVideoUrl("");
        reqVO.setSegmentDurationConfig(null);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setReferenceDuration(20);
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().isEmpty());
        TkGenerationPrecheckRespVO.PrecheckIssue warning = result.getWarnings().stream()
                .filter(issue -> "MATERIAL_DURATION_SHORTER_THAN_TARGET".equals(issue.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(20, warning.getRequiredDuration());
        assertEquals(17, warning.getActualDuration());
        assertEquals(3, warning.getMissingDuration());
        assertTrue(warning.getActionHint().contains("补充素材"));
    }

    @Test
    void precheckFullPoolRandomUsesRemainingDurationAfterUploadedOpening() {
        TkGenerationPrecheckServiceImpl service = createService(Collections.singletonList(
                material(1L, 7L, "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(10);
        reqVO.setOpeningVideoName("golden-opening.mp4");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void precheckFullPoolRandomWarnsWhenClipIsLongerThanRemainingDurationAfterOpening() {
        TkGenerationPrecheckServiceImpl service = createService(Collections.singletonList(
                material(1L, 8L, "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(10);
        reqVO.setOpeningVideoName("golden-opening.mp4");
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        TkGenerationPrecheckRespVO.PrecheckIssue warning = result.getWarnings().stream()
                .filter(issue -> "MATERIAL_TOO_LONG_FOR_TARGET".equals(issue.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(7, warning.getRequiredDuration());
        assertEquals(8, warning.getActualDuration());
        assertTrue(warning.getActionHint().contains("补充更贴近目标时长"));
    }

    @Test
    void precheckLeadGenerationUsesSelectedFullPoolRandomModeWithoutSegmentChecks() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 4L, "GENERAL"),
                material(2L, 6L, "GENERAL"),
                material(3L, 7L, "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(20);
        reqVO.setOpeningVideoUrl("");
        reqVO.setSegmentDurationConfig(null);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        reqVO.setSourceUrl("");
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        assertTrue(result.getErrors().stream()
                .noneMatch(issue -> issue.getCode().startsWith("SEGMENT_")));
    }

    @Test
    void precheckFullPoolRandomModeWarnsWhenAllClipsAreLongerThanTargetDuration() {
        TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
                material(1L, 12L, "GENERAL"),
                material(2L, 15L, "GENERAL")
        ));
        TkGenerationTaskCreateReqVO reqVO = createRequest(10);
        reqVO.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE);
        reqVO.setSegmentDurationConfig(null);
        reqVO.setClipPlanMode("FULL_POOL_RANDOM");

        TkGenerationPrecheckRespVO result = service.precheck(reqVO);

        assertTrue(result.getPassed());
        TkGenerationPrecheckRespVO.PrecheckIssue warning = result.getWarnings().stream()
                .filter(issue -> "MATERIAL_TOO_LONG_FOR_TARGET".equals(issue.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(7, warning.getRequiredDuration());
        assertEquals(12, warning.getActualDuration());
        assertTrue(warning.getActionHint().contains("补充更贴近目标时长"));
    }

    private TkGenerationPrecheckServiceImpl createService(java.util.List<TkMaterialVideoDO> materials) {
        TkGenerationPrecheckServiceImpl service = new TkGenerationPrecheckServiceImpl();
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(materials);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        return service;
    }

    private TkGenerationTaskCreateReqVO createRequest(Integer targetDuration) {
        TkGenerationTaskCreateReqVO reqVO = new TkGenerationTaskCreateReqVO();
        reqVO.setSourceUrl("https://www.tiktok.com/@demo/video/1");
        reqVO.setLibraryId(10L);
        reqVO.setVoiceCode("voice");
        reqVO.setReferenceDuration(targetDuration);
        reqVO.setOpeningVideoUrl("https://example.com/opening.mp4");
        return reqVO;
    }

    private TkMaterialVideoDO material(Long id, Long duration) {
        return material(id, duration, "GENERAL");
    }

    private TkMaterialVideoDO material(Long id, Long duration, String usagePhase) {
        return material(id, duration, usagePhase, null);
    }

    private TkMaterialVideoDO material(Long id, Long duration, String usagePhase, String segmentType) {
        return TkMaterialVideoDO.builder()
                .id(id)
                .libraryId(10L)
                .fileUrl("https://example.com/material-" + id + ".mp4")
                .duration(duration)
                .usagePhase(usagePhase)
                .segmentType(segmentType)
                .build();
    }

}
