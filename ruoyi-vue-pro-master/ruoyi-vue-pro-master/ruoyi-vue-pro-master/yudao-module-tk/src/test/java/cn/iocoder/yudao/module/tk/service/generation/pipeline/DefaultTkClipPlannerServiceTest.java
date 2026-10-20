package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTkClipPlannerServiceTest {

    @Test
    void planLeadGenerationUsesOneWholeMaterialForEverySegmentAndGeneralLast() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 3L, "ATTENTION", "S2_PAIN"),
                material(3L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 5L, "PRODUCT_SHOW", "S4_DEMO"),
                material(40L, 6L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 7L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 8L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 9L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(8L, 10L, "GENERAL", "GENERAL")
        ));
        when(taskMapper.selectRecentSuccessfulClipPlansByLibraryId(10L, 109L, 10))
                .thenReturn(Arrays.asList(recentTask(203L, 4L)));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(109L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(15)
                .openingVideoUrl("https://example.com/opening.mp4")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "lead generation script");

        assertEquals(Arrays.asList("S1_HOOK", "S2_PAIN", "S3_REVEAL", "S4_DEMO",
                        "S5_PROOF", "S6_DETAIL", "S7_LIFESTYLE", "GENERAL"),
                plan.stream().map(TkClipPlanItem::getSection).collect(Collectors.toList()));
        assertEquals(Arrays.asList(1L, 2L, 3L, 40L, 5L, 6L, 7L, 8L),
                plan.stream().map(TkClipPlanItem::getMaterialVideoId).collect(Collectors.toList()));
        assertEquals(Arrays.asList(2, 3, 4, 6, 7, 8, 9, 10),
                plan.stream().map(TkClipPlanItem::getDurationSecond).collect(Collectors.toList()));
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
        assertTrue(plan.stream().allMatch(item -> item.getSectionTargetSecond() == null));
    }

    @Test
    void nativeOpeningIsPinnedAtZeroAndBodyPlanStartsAfterItsActualDuration() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 6L, "ATTENTION", "S2_PAIN"),
                material(3L, 8L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 10L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 8L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(190L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(30)
                .openingProcessMode(TkNativeOpeningSupport.MODE_NATIVE)
                .openingVideoUrl("https://example.com/opening.mp4")
                .openingDurationMs(3200L)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "body script", 32);

        assertEquals("OPENING", plan.get(0).getSourceType());
        assertEquals(0, plan.get(0).getStartSecond());
        assertEquals(3200L, plan.get(0).getDurationMillis());
        assertTrue(plan.stream().skip(1).noneMatch(item -> "S1_HOOK".equals(item.getSection())));
        assertTrue(plan.stream().skip(1).mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 27);
    }

    @Test
    void nativeOpeningWithSubsecondBodyDoesNotCreateNegativeDurationSections() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S2_PAIN"),
                material(2L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(3L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 2L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(191L)
                .libraryId(10L)
                .targetDuration(8)
                .openingProcessMode(TkNativeOpeningSupport.MODE_NATIVE)
                .openingVideoUrl("https://example.com/opening.mp4")
                .openingDurationMs(7_500L)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "body script", 8);

        assertTrue(plan.stream().skip(1).allMatch(item -> item.getDurationSecond() > 0));
        assertEquals(1, plan.stream().skip(1).count());
    }

    @Test
    void nativeOpeningRequiresMeasuredDurationBeforePlanning() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 30L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .libraryId(10L)
                .targetDuration(30)
                .openingProcessMode(TkNativeOpeningSupport.MODE_NATIVE)
                .openingVideoUrl("https://example.com/opening.mp4")
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "body script", 30));

        assertTrue(error.getMessage().contains("开头视频时长"));
    }

    @Test
    void planLeadGenerationKeepsEveryMaterialUniqueWhenTargetDurationIsLongerThanLibrary() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 5L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "ATTENTION", "S2_PAIN"),
                material(3L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 4L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 5L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 6L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(8L, 5L, "GENERAL", "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(181L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(50)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "lead generation script");

        assertEquals(8, plan.size());
        assertEquals(33, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(8, plan.stream().map(TkClipPlanItem::getMaterialVideoId).distinct().count());
        assertEquals("GENERAL", plan.get(7).getSection());
    }

    @Test
    void planLeadGenerationFinalBackfillContinuesLastGeneralSource() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "ATTENTION", "S2_PAIN"),
                material(3L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 2L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 2L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 2L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(8L, 2L, "GENERAL", "GENERAL"),
                material(9L, 20L, "GENERAL", "GENERAL"),
                material(10L, 6L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(11L, 7L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(12L, 14L, "GENERAL", "GENERAL")
        ));
        when(taskMapper.selectRecentSuccessfulClipPlansByLibraryId(10L, 186L, 10))
                .thenReturn(Arrays.asList(recentTask(301L, 9L), recentTask(302L, 10L),
                        recentTask(303L, 11L), recentTask(304L, 12L)));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(186L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(20)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "lead generation script", 29);

        assertEquals(30, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        List<Long> backfillIds = plan.stream().skip(8)
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(12L), backfillIds);
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
    }

    @Test
    void replanLeadGenerationTailReplacesLastWholeMaterialWhenTailIsLowDynamic() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 5L, "ATTENTION", "S1_HOOK"),
                material(2L, 5L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 5L, "GENERAL", "GENERAL"),
                material(4L, 6L, "GENERAL", "S4_DEMO")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(187L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(15)
                .build();
        List<TkClipPlanItem> original = Arrays.asList(
                new TkClipPlanItem(1, "MATERIAL", 1L, "material-1.mp4", "http://example.com/material-1.mp4", 0, 5,
                        "base", "S1_HOOK", "黄金3秒", 1, null, null, null, null),
                new TkClipPlanItem(2, "MATERIAL", 2L, "material-2.mp4", "http://example.com/material-2.mp4", 0, 5,
                        "base", "S4_DEMO", "使用演示", 2, null, null, null, null),
                new TkClipPlanItem(3, "MATERIAL", 3L, "material-3.mp4", "http://example.com/material-3.mp4", 0, 5,
                        "base", "GENERAL", "通用素材", 3, null, null, null, null)
        );

        List<TkClipPlanItem> replanned = service.replanTailForLowDynamic(task, original, 15);

        assertEquals(Arrays.asList(1L, 2L, 4L), replanned.stream()
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList()));
        assertTrue(replanned.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 15);
        assertTrue(replanned.get(2).getReason().contains("尾部"));
    }

    @Test
    void planLeadGenerationUsesAvailableUniqueMaterialsWhenSegmentIsMissing() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "ATTENTION", "S2_PAIN"),
                material(3L, 2L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 2L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 2L, "PRODUCT_SHOW", "S6_DETAIL"),
                material(7L, 2L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(110L)
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .targetDuration(15)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "lead generation script");

        assertEquals(7, plan.size());
        assertEquals(7, plan.stream().map(TkClipPlanItem::getMaterialVideoId).distinct().count());
    }

    @Test
    void planUsesWholeMaterialsWithoutClipDurationPool() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 3L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(3L, 6L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 4L, "RESULT_EFFECT", "S5_PROOF")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(99L)
                .libraryId(10L)
                .targetDuration(15)
                .clipSeconds(3)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "脚步提拉带 支撑 防滑");

        assertEquals(15, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(Arrays.asList(2, 3, 6, 4), plan.stream()
                .map(TkClipPlanItem::getDurationSecond)
                .collect(Collectors.toList()));
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
    }

    @Test
    void planRandomizesMaterialSelectionWithinSegmentPool() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(2, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 4L, "PRODUCT_SHOW", "S4_DEMO")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(106L)
                .libraryId(10L)
                .targetDuration(10)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":7}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo");

        List<Long> demoIds = plan.stream()
                .filter(item -> "S4_DEMO".equals(item.getSection()))
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(3L, 2L), demoIds);
        assertTrue(plan.stream()
                .filter(item -> "S4_DEMO".equals(item.getSection()))
                .allMatch(item -> item.getStartSecond() == 0 && item.getDurationSecond() == 4));
        assertTrue(plan.stream().allMatch(item -> item.getMatchScore() == null));
    }

    @Test
    void planUsesRouteConfigWhenUserSegmentConfigIsBlank() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 5L, "ATTENTION", "S1_HOOK"),
                material(2L, 6L, "PRODUCT_SHOW", "S4_DEMO")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(111L)
                .libraryId(10L)
                .targetDuration(11)
                .generationRouteCode("ECOM_APPAREL")
                .generationRouteConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":5},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":6}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "route config demo");

        assertEquals(Arrays.asList("S1_HOOK", "S4_DEMO"),
                plan.stream().map(TkClipPlanItem::getSection).collect(Collectors.toList()));
        assertEquals(Arrays.asList(5, 6),
                plan.stream().map(TkClipPlanItem::getSectionTargetSecond).collect(Collectors.toList()));
    }

    @Test
    void planUsesFullPoolRandomModeFromRouteConfigAndKeepsWholeClipsOnly() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(1, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 4L, "GENERAL"),
                material(2L, 6L, "GENERAL"),
                material(3L, 7L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(112L)
                .libraryId(10L)
                .targetDuration(10)
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "full pool random demo");

        assertEquals(10, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(2, plan.size());
        assertTrue(plan.stream().allMatch(item -> "MATERIAL".equals(item.getSourceType())));
        assertTrue(plan.stream().allMatch(item -> item.getSection() == null));
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
        assertTrue(plan.stream().map(TkClipPlanItem::getMaterialVideoId).collect(Collectors.toList())
                .containsAll(Arrays.asList(1L, 2L)));
    }

    @Test
    void planFullPoolRandomKeepsUploadedOpeningFirstAndRandomizesRemainingDuration() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "GENERAL"),
                material(2L, 4L, "GENERAL"),
                material(3L, 7L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(186L)
                .libraryId(10L)
                .targetDuration(10)
                .openingVideoUrl("https://example.com/golden-opening.mp4")
                .openingVideoName("golden-opening.mp4")
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "full pool random with opening");

        assertEquals(10, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(2, plan.size());
        assertEquals("OPENING", plan.get(0).getSourceType());
        assertEquals("golden-opening.mp4", plan.get(0).getFileName());
        assertEquals(3, plan.get(0).getDurationSecond());
        assertEquals(1, plan.get(0).getOrderNo());
        assertEquals("MATERIAL", plan.get(1).getSourceType());
        assertEquals(3L, plan.get(1).getMaterialVideoId());
        assertEquals(2, plan.get(1).getOrderNo());
        assertEquals(7, plan.get(1).getDurationSecond());
    }

    @Test
    void planUsesEffectiveTargetDurationWhenAudioIsLongerThanRequestedDuration() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 5L, "GENERAL"),
                material(2L, 13L, "GENERAL"),
                material(3L, 9L, "GENERAL"),
                material(4L, 8L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(184L)
                .libraryId(10L)
                .targetDuration(27)
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "full pool random demo", 35);

        assertEquals(35, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(4, plan.size());
        assertTrue(plan.stream().allMatch(item -> item.getReason().contains("完整使用")));
    }

    @Test
    void planUsesEffectiveTargetDurationForSegmentedModeWhenAudioIsLonger() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 2L, "ATTENTION", "S1_HOOK"),
                material(2L, 3L, "ATTENTION", "S2_PAIN"),
                material(3L, 3L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 6L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 4L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 4L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(185L)
                .libraryId(10L)
                .targetDuration(10)
                .generationRouteConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":7}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "segmented demo", 22);

        assertEquals(22, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        assertEquals(Arrays.asList("S1_HOOK", "S2_PAIN", "S3_REVEAL", "S4_DEMO", "S5_PROOF", "S7_LIFESTYLE"),
                plan.stream().map(TkClipPlanItem::getSection).collect(Collectors.toList()));
    }

    @Test
    void planUsesClosestUniqueMaterialWhenNoClipFitsTargetDuration() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 12L, "GENERAL"),
                material(2L, 15L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(113L)
                .libraryId(10L)
                .targetDuration(10)
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "full pool random demo");

        assertEquals(1, plan.size());
        assertEquals(1L, plan.get(0).getMaterialVideoId());
        assertEquals(12, plan.get(0).getDurationSecond());
        assertEquals(10, plan.get(0).getSectionTargetSecond());
    }

    @Test
    void planFullPoolRandomKeepsMaterialsUniqueWhenLibraryDurationIsShort() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(0, 0, 0, 0));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 4L, "GENERAL"),
                material(2L, 6L, "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(114L)
                .libraryId(10L)
                .targetDuration(15)
                .generationRouteConfig("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "full pool random short library");

        assertEquals(2, plan.size());
        assertEquals(2, plan.stream().map(TkClipPlanItem::getMaterialVideoId).distinct().count());
        assertEquals(10, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
    }

    @Test
    void planPrefersMaterialsNotUsedByRecentSuccessfulTasks() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(1));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 5L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 5L, "PRODUCT_SHOW", "S4_DEMO")
        ));
        when(taskMapper.selectRecentSuccessfulClipPlansByLibraryId(10L, 107L, 10))
                .thenReturn(Arrays.asList(recentTask(201L, 2L)));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(107L)
                .libraryId(10L)
                .targetDuration(8)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":5}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo");

        List<Long> demoIds = plan.stream()
                .filter(item -> "S4_DEMO".equals(item.getSection()))
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(3L), demoIds);
    }

    @Test
    void planFallsBackToRecentMaterialsWhenFreshMaterialsAreNotEnough() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new FixedRandom(1));
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 4L, "PRODUCT_SHOW", "S4_DEMO")
        ));
        when(taskMapper.selectRecentSuccessfulClipPlansByLibraryId(10L, 108L, 10))
                .thenReturn(Arrays.asList(recentTask(202L, 2L)));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(108L)
                .libraryId(10L)
                .targetDuration(10)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":7}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo");

        List<Long> demoIds = plan.stream()
                .filter(item -> "S4_DEMO".equals(item.getSection()))
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(3L, 2L), demoIds);
    }

    @Test
    void planBuildsTimelineWhenExplicitSegmentMaterialsExist() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 10L, "ATTENTION", "S1_HOOK"),
                material(2L, 10L, "ATTENTION", "S2_PAIN"),
                material(3L, 10L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 30L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 12L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 12L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(100L)
                .libraryId(10L)
                .targetDuration(30)
                .clipSeconds(3)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "portable blender easy clean smooth result");

        assertTrue(plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 30);
        assertEquals("S1_HOOK", plan.get(0).getSection());
        assertTrue(plan.stream().anyMatch(item -> "S4_DEMO".equals(item.getSection())));
        assertEquals("S7_LIFESTYLE", plan.get(plan.size() - 1).getSection());
        assertTrue(plan.stream().allMatch(item -> item.getSectionOrder() != null && item.getSectionName() != null));
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
    }

    @Test
    void planUsesSevenSegmentTimelineWhenProvided() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 10L, "ATTENTION", "S1_HOOK"),
                material(2L, 10L, "ATTENTION", "S2_PAIN"),
                material(3L, 10L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 12L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 10L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 10L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(101L)
                .libraryId(10L)
                .targetDuration(20)
                .clipSeconds(3)
                .segmentTimeline("["
                        + "{\"timeWindow\":\"0-3s\",\"segmentLibrary\":\"S1_HOOK\",\"scriptLine\":\"Hook line\",\"visualDirection\":\"展示强钩子画面\"},"
                        + "{\"timeWindow\":\"3-6s\",\"segmentLibrary\":\"S2_PAIN\",\"scriptLine\":\"Pain line\",\"visualDirection\":\"展示痛点场景\"},"
                        + "{\"timeWindow\":\"6-9s\",\"segmentLibrary\":\"S3_REVEAL\",\"scriptLine\":\"Reveal line\",\"visualDirection\":\"展示产品亮相\"},"
                        + "{\"timeWindow\":\"9-15s\",\"segmentLibrary\":\"S4_DEMO\",\"scriptLine\":\"Demo line\",\"visualDirection\":\"展示使用过程\"},"
                        + "{\"timeWindow\":\"15-20s\",\"segmentLibrary\":\"S5_PROOF\",\"scriptLine\":\"Proof line\",\"visualDirection\":\"展示使用效果\"}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "Hook line Pain line Reveal line Demo line Proof line");

        assertTrue(plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 20);
        assertEquals("S1_HOOK", plan.get(0).getSection());
        assertEquals("S5_PROOF", plan.get(plan.size() - 1).getSection());
        assertTrue(plan.stream().noneMatch(item -> "S8_CTA".equals(item.getSection())));
        assertTrue(plan.stream().allMatch(item -> item.getVisualDirection() != null));
        assertTrue(plan.stream().allMatch(item -> item.getStartSecond() == 0));
    }

    @Test
    void planUsesCustomSegmentDurationConfigBeforeAiTimeline() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 10L, "ATTENTION", "S1_HOOK"),
                material(2L, 10L, "ATTENTION", "S2_PAIN"),
                material(3L, 10L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(4L, 12L, "PRODUCT_SHOW", "S4_DEMO"),
                material(5L, 10L, "RESULT_EFFECT", "S5_PROOF"),
                material(6L, 10L, "PRODUCT_SHOW", "S7_LIFESTYLE")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(104L)
                .libraryId(10L)
                .targetDuration(20)
                .clipSeconds(3)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":2},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":10},"
                        + "{\"segmentType\":\"S5_PROOF\",\"duration\":8}"
                        + "]")
                .segmentTimeline("["
                        + "{\"timeWindow\":\"0-5s\",\"segmentLibrary\":\"S1_HOOK\"},"
                        + "{\"timeWindow\":\"5-10s\",\"segmentLibrary\":\"S2_PAIN\"},"
                        + "{\"timeWindow\":\"10-20s\",\"segmentLibrary\":\"S7_LIFESTYLE\"}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "custom demo proof");

        assertTrue(plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 20);
        assertTrue(durationOf(plan, "S1_HOOK") >= 2);
        assertTrue(durationOf(plan, "S4_DEMO") >= 10);
        assertTrue(durationOf(plan, "S5_PROOF") >= 8);
        assertEquals(2, sectionTargetOf(plan, "S1_HOOK"));
        assertEquals(10, sectionTargetOf(plan, "S4_DEMO"));
        assertEquals(8, sectionTargetOf(plan, "S5_PROOF"));
        assertEquals(0, durationOf(plan, "S2_PAIN"));
        assertEquals(0, durationOf(plan, "S7_LIFESTYLE"));
    }

    @Test
    void planUsesWholeMaterialsUntilSectionDurationIsEnoughWithoutTrimming() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 4L, "PRODUCT_SHOW", "S4_DEMO")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(105L)
                .libraryId(10L)
                .targetDuration(10)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":7}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo");

        List<TkClipPlanItem> demoItems = plan.stream()
                .filter(item -> "S4_DEMO".equals(item.getSection()))
                .collect(Collectors.toList());
        assertEquals(2, demoItems.size());
        assertEquals(Arrays.asList(4, 4), demoItems.stream()
                .map(TkClipPlanItem::getDurationSecond)
                .collect(Collectors.toList()));
        assertTrue(demoItems.stream().allMatch(item -> item.getStartSecond() == 0));
        assertTrue(demoItems.stream().allMatch(item -> item.getSectionTargetSecond() == 7));
        assertEquals(11, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
    }

    @Test
    void planRejectsInitialRequiredSectionWhenOnlyUnassignedSourcePoolExists() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 20L, "ATTENTION"),
                material(2L, 20L, "PRODUCT_SHOW"),
                material(3L, 20L, "RESULT_EFFECT")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(102L)
                .libraryId(10L)
                .targetDuration(15)
                .clipSeconds(3)
                .build();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "portable blender demo result"));

        assertTrue(error.getMessage().contains("S1_HOOK"));
        assertTrue(error.getMessage().contains("素材不足"));
    }

    @Test
    void planKeepsNonemptyShortSectionForRendererInsteadOfTakingFutureSource() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setClipDurationPool(Arrays.asList(2, 3, 4));
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 10L, "ATTENTION", "S1_HOOK"),
                material(2L, 10L, "PRODUCT_SHOW", "S3_REVEAL"),
                material(3L, 4L, "PRODUCT_SHOW", "S4_DEMO"),
                material(4L, 10L, "RESULT_EFFECT", "S5_PROOF"),
                material(5L, 10L, "PRODUCT_SHOW", "S7_LIFESTYLE"),
                material(6L, 10L, "GENERAL", "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(103L)
                .libraryId(10L)
                .targetDuration(15)
                .clipSeconds(3)
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo proof");

        assertEquals(plan.size(), plan.stream().map(TkClipPlanItem::getMaterialVideoId).distinct().count());
        assertTrue(plan.stream().noneMatch(item -> Long.valueOf(4L).equals(item.getMaterialVideoId())
                && "S4_DEMO".equals(item.getSection())));
    }

    @Test
    void planDoesNotUseGeneralWhenLastOwnModuleIsExhausted() {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        TkMaterialVideoMapper materialVideoMapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", materialVideoMapper);
        when(materialVideoMapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(
                material(1L, 3L, "ATTENTION", "S1_HOOK"),
                material(2L, 2L, "PRODUCT_SHOW", "S4_DEMO"),
                material(3L, 10L, "GENERAL", "GENERAL"),
                material(4L, 6L, "GENERAL", "GENERAL")
        ));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(115L)
                .libraryId(10L)
                .targetDuration(10)
                .segmentDurationConfig("["
                        + "{\"segmentType\":\"S1_HOOK\",\"duration\":3},"
                        + "{\"segmentType\":\"S4_DEMO\",\"duration\":7}"
                        + "]")
                .build();

        List<TkClipPlanItem> plan = service.plan(task, "demo");

        assertEquals(Arrays.asList(1L, 2L), materialIds(plan));
        assertEquals(7, sectionTargetOf(plan, "S4_DEMO"));
    }

    @Test
    void ownModuleStillWinsBeforeLastSourceFallback() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 2L, "GENERAL", "S3_REVEAL"),
                material(2L, 8L, "GENERAL", "S4_DEMO"),
                material(3L, 6L, "GENERAL", "S3_REVEAL"));
        assertEquals(Arrays.asList(1L, 2L), materialIds(service.plan(
                orderedTask(new int[]{2, 8}, "S3_REVEAL", "S4_DEMO"), "demo")));
    }

    @Test
    void fallbackContinuesLastSourceEvenWhenUnusedS6Exists() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S5_PROOF"),
                material(2L, 7L, "GENERAL", "S5_PROOF"),
                material(3L, 7L, "GENERAL", "S6_DETAIL"));
        List<TkClipPlanItem> plan = service.plan(orderedTask(new int[]{1, 7}, "S5_PROOF", "S7_LIFESTYLE"), "demo");
        assertEquals(Arrays.asList(1L, 2L), materialIds(plan));
        assertEquals("S7_LIFESTYLE", plan.get(1).getSection());
        assertTrue(plan.get(1).getReason().contains("S5_PROOF"));
        assertTrue(plan.get(1).getReason().contains("S7_LIFESTYLE"));
    }

    @Test
    void multipleMissingSectionsContinueActualSourceDespiteTargetLabels() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 3L, "GENERAL", "S5_PROOF"),
                material(2L, 3L, "GENERAL", "S5_PROOF"),
                material(3L, 5L, "GENERAL", "S5_PROOF"),
                material(4L, 20L, "GENERAL", "S4_DEMO"));
        List<TkClipPlanItem> plan = service.plan(orderedTask(new int[]{3, 3, 5},
                "S5_PROOF", "S6_DETAIL", "S7_LIFESTYLE"), "demo");
        assertEquals(Arrays.asList(1L, 2L, 3L), materialIds(plan));
        assertEquals(Arrays.asList("S5_PROOF", "S6_DETAIL", "S7_LIFESTYLE"), plan.stream()
                .map(TkClipPlanItem::getSection).collect(Collectors.toList()));
    }

    @Test
    void lastSourceUsesTimelineOrderRatherThanHighestEnum() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 2L, "GENERAL", "S5_PROOF"),
                material(2L, 2L, "GENERAL", "S2_PAIN"),
                material(3L, 4L, "GENERAL", "S2_PAIN"),
                material(4L, 4L, "GENERAL", "S5_PROOF"));
        TkGenerationTaskDO task = TkGenerationTaskDO.builder().libraryId(10L).targetDuration(8)
                .segmentTimeline("[{\"segmentLibrary\":\"S5_PROOF\",\"timeWindow\":\"0-2s\"},"
                        + "{\"segmentLibrary\":\"S2_PAIN\",\"timeWindow\":\"2-4s\"},"
                        + "{\"segmentLibrary\":\"S7_LIFESTYLE\",\"timeWindow\":\"4-8s\"}]").build();
        assertEquals(Arrays.asList(1L, 2L, 3L), materialIds(service.plan(task, "demo")));
    }

    @Test
    void noInitialSourceCannotInventGeneralOrAnUnusedOrderedPool() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 5L, "GENERAL", "GENERAL"),
                material(2L, 20L, "GENERAL", "S1_HOOK"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.plan(
                orderedTask(new int[]{8}, "S7_LIFESTYLE"), "demo"));
        assertTrue(error.getMessage().contains("S7_LIFESTYLE"));
        assertTrue(error.getMessage().contains("尚无实际来源模块"));
    }

    @Test
    void oneGapConsumesDurationNearestUniqueClipsOnlyFromLastPool() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S5_PROOF"),
                material(2L, 3L, "GENERAL", "S5_PROOF"),
                material(3L, 5L, "GENERAL", "S5_PROOF"),
                material(4L, 20L, "GENERAL", "S5_PROOF"),
                material(5L, 8L, "GENERAL", "S6_DETAIL"));
        assertEquals(Arrays.asList(1L, 3L, 2L), materialIds(service.plan(
                orderedTask(new int[]{1, 8}, "S5_PROOF", "S7_LIFESTYLE"), "demo")));
    }

    @Test
    void exhaustedLastPoolCannotRevisitEarlierOrNeverUsedPools() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S2_PAIN"),
                material(2L, 20L, "GENERAL", "S2_PAIN"),
                material(3L, 1L, "GENERAL", "S5_PROOF"),
                material(4L, 20L, "GENERAL", "S6_DETAIL"),
                material(5L, 20L, "GENERAL", "GENERAL"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.plan(
                orderedTask(new int[]{1, 1, 6}, "S2_PAIN", "S5_PROOF", "S7_LIFESTYLE"), "demo"));
        assertTrue(error.getMessage().contains("S7_LIFESTYLE"));
        assertTrue(error.getMessage().contains("S5_PROOF"));
        assertTrue(error.getMessage().contains("耗尽"));
    }

    @Test
    void nonemptyShortSectionDoesNotReintroduceUsedSourcesDuringFinalFill() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S1_HOOK"),
                material(2L, 20L, "GENERAL", "S1_HOOK"),
                material(3L, 1L, "GENERAL", "S4_DEMO"));

        List<TkClipPlanItem> plan = service.plan(orderedTask(new int[]{1, 7}, "S1_HOOK", "S4_DEMO"), "demo");

        assertEquals(Arrays.asList(1L, 3L), materialIds(plan));
        assertEquals(7, sectionTargetOf(plan, "S4_DEMO"));
    }

    @Test
    void futureOwnModuleWinsAfterEarlierFallbackContinuedPreviousSource() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 3L, "GENERAL", "S1_HOOK"),
                material(2L, 3L, "GENERAL", "S1_HOOK"),
                material(3L, 5L, "GENERAL", "S3_REVEAL"));
        List<TkClipPlanItem> plan = service.plan(orderedTask(new int[]{3, 3, 5},
                "S1_HOOK", "S2_PAIN", "S3_REVEAL"), "demo");
        assertEquals(Arrays.asList(1L, 2L, 3L), materialIds(plan));
    }

    @Test
    void invalidOrderedMaterialsCannotFillAnEmptySection() {
        TkMaterialVideoDO noUrl = material(1L, 10L, "GENERAL", "S4_DEMO");
        noUrl.setFileUrl("");
        DefaultTkClipPlannerService service = plannerWith(noUrl,
                material(2L, 0L, "GENERAL", "S5_PROOF"));

        assertThrows(IllegalStateException.class, () -> service.plan(
                orderedTask(new int[]{8}, "S4_DEMO"), "demo"));
    }

    @Test
    void leadMissingModuleContinuesLastSourceAndReportsTargetAndSource() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 2L, "GENERAL", "S1_HOOK"),
                material(2L, 1L, "GENERAL", "S1_HOOK"),
                material(3L, 8L, "GENERAL", "S3_REVEAL"),
                material(4L, 2L, "GENERAL", "S3_REVEAL"));
        TkGenerationTaskDO task = orderedTask(new int[]{10}, "S1_HOOK");
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        List<TkClipPlanItem> plan = service.plan(task, "lead");
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L), materialIds(plan));
        assertEquals("S2_PAIN", plan.get(1).getSection());
        assertTrue(plan.get(1).getReason().contains("S1_HOOK"));
        assertTrue(plan.get(1).getReason().contains("S2_PAIN"));
    }

    @Test
    void tailReplanContinuesActualSourceOfRetainedS7ItemAndExcludesAllOriginalIds() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 3L, "GENERAL", "S5_PROOF"),
                material(2L, 5L, "GENERAL", "S5_PROOF"),
                material(3L, 5L, "GENERAL", "S5_PROOF"),
                material(4L, 6L, "GENERAL", "GENERAL"));
        TkGenerationTaskDO task = orderedTask(new int[]{8}, "S7_LIFESTYLE");
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        List<TkClipPlanItem> original = Arrays.asList(
                new TkClipPlanItem(1, "MATERIAL", 1L, "first", "https://example.com/first", 0, 3,
                        "fallback", "S7_LIFESTYLE", "custom target", 1, null, "target script", "target visual", 8),
                new TkClipPlanItem(2, "MATERIAL", 2L, "tail", "https://example.com/tail", 0, 5,
                        "tail", "S7_LIFESTYLE", "custom target", 2, null, "target script", "target visual", 8));
        List<TkClipPlanItem> replanned = service.replanTailForLowDynamic(task, original, 8);
        assertEquals(Arrays.asList(1L, 3L), materialIds(replanned));
        assertEquals("S7_LIFESTYLE", replanned.get(1).getSection());
        assertEquals("custom target", replanned.get(1).getSectionName());
        assertEquals("target script", replanned.get(1).getScriptLine());
        assertEquals("target visual", replanned.get(1).getVisualDirection());
        assertEquals(8, replanned.get(1).getSectionTargetSecond());
        assertTrue(replanned.get(1).getReason().contains("S5_PROOF"));
    }

    @Test
    void s7ContinuesS5AfterS1ThroughS5WereUsedEvenWithUnusedS6() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S1_HOOK"),
                material(2L, 1L, "GENERAL", "S2_PAIN"),
                material(3L, 1L, "GENERAL", "S3_REVEAL"),
                material(4L, 1L, "GENERAL", "S4_DEMO"),
                material(5L, 1L, "GENERAL", "S5_PROOF"),
                material(6L, 20L, "GENERAL", "S6_DETAIL"),
                material(20L, 3L, "GENERAL", "S5_PROOF"));
        List<TkClipPlanItem> plan = service.plan(orderedTask(new int[]{1, 1, 1, 1, 1, 3},
                "S1_HOOK", "S2_PAIN", "S3_REVEAL", "S4_DEMO", "S5_PROOF", "S7_LIFESTYLE"), "demo");
        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L, 20L), materialIds(plan));
    }

    @Test
    void s7ContinuesS6WhenItIsTheLastActualSource() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S5_PROOF"),
                material(2L, 1L, "GENERAL", "S6_DETAIL"),
                material(3L, 6L, "GENERAL", "S6_DETAIL"));
        assertEquals(Arrays.asList(1L, 2L, 3L), materialIds(service.plan(
                orderedTask(new int[]{1, 1, 6}, "S5_PROOF", "S6_DETAIL", "S7_LIFESTYLE"), "demo")));
    }

    @Test
    void generalCanContinueWhenItIsTheLastActualSource() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 4L, "GENERAL", "GENERAL"),
                material(2L, 4L, "GENERAL", "GENERAL"),
                material(3L, 4L, "GENERAL", "S6_DETAIL"));
        TkGenerationTaskDO task = orderedTask(new int[]{8}, "S7_LIFESTYLE");
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        // S6 is selected first, then GENERAL becomes the last actual source before final fill.
        assertEquals(Arrays.asList(3L, 1L), materialIds(service.plan(task, "lead")));
        task.setTargetDuration(12);
        assertEquals(Arrays.asList(3L, 1L, 2L), materialIds(service.plan(task, "lead")));
    }

    @Test
    void leadFailsClearlyWhenNoValidSourceCanProduceAnyPlan() {
        DefaultTkClipPlannerService service = plannerWith(material(1L, 0L, "GENERAL", "S1_HOOK"));
        TkGenerationTaskDO task = orderedTask(new int[]{8}, "S1_HOOK");
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "lead"));

        assertTrue(error.getMessage().contains("素材不足"));
    }

    @Test
    void productionRequested15Effective21ContinuesS5WithUniqueIdsWhenS6S7AreEmpty() {
        ArrayList<TkMaterialVideoDO> materials = new ArrayList<>();
        String[] modules = {"S1_HOOK", "S2_PAIN", "S3_REVEAL", "S4_DEMO", "S5_PROOF"};
        int[] counts = {31, 53, 17, 44, 29};
        long id = 1;
        for (int module = 0; module < modules.length; module++) {
            for (int clip = 0; clip < counts[module]; clip++) {
                materials.add(material(id++, 2L, "GENERAL", modules[module]));
            }
        }
        DefaultTkClipPlannerService service = plannerWith(materials.toArray(new TkMaterialVideoDO[0]));
        TkGenerationTaskDO task = orderedTask(new int[]{3, 2, 2, 3, 2, 3},
                "S1_HOOK", "S2_PAIN", "S3_REVEAL", "S4_DEMO", "S5_PROOF", "S7_LIFESTYLE");

        List<TkClipPlanItem> plan = service.plan(task, "production-shaped independent fixture", 21);

        assertEquals(15, task.getTargetDuration());
        assertEquals(21, plan.stream().collect(Collectors.toMap(TkClipPlanItem::getSection,
                TkClipPlanItem::getSectionTargetSecond, (first, ignored) -> first)).values().stream()
                .mapToInt(Integer::intValue).sum());
        assertEquals(plan.size(), materialIds(plan).stream().distinct().count());
        assertTrue(plan.stream().anyMatch(item -> "S7_LIFESTYLE".equals(item.getSection())));
        assertTrue(plan.stream().filter(item -> "S7_LIFESTYLE".equals(item.getSection()))
                .allMatch(item -> item.getMaterialVideoId() >= 146L
                        && item.getReason().contains("S5_PROOF")));
    }

    @Test
    void invalidUnusedIdsUrlsAndDurationsCannotFillFromLastPool() {
        TkMaterialVideoDO noUrl = material(2L, 7L, "GENERAL", "S5_PROOF");
        noUrl.setFileUrl(" ");
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S5_PROOF"), noUrl,
                material(null, 7L, "GENERAL", "S5_PROOF"),
                material(0L, 7L, "GENERAL", "S5_PROOF"),
                material(-1L, 7L, "GENERAL", "S5_PROOF"),
                material(3L, 0L, "GENERAL", "S5_PROOF"),
                material(4L, -2L, "GENERAL", "S5_PROOF"),
                material(5L, 7L, "GENERAL", "S6_DETAIL"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.plan(
                orderedTask(new int[]{1, 7}, "S5_PROOF", "S7_LIFESTYLE"), "demo"));
        assertTrue(error.getMessage().contains("S5_PROOF"));
        assertTrue(error.getMessage().contains("耗尽"));
    }

    @Test
    void uploadedOpeningWithoutMaterialIdDoesNotFabricateSource() {
        DefaultTkClipPlannerService service = plannerWith(material(1L, 7L, "GENERAL", "S1_HOOK"));
        TkGenerationTaskDO task = orderedTask(new int[]{1, 7}, "S1_HOOK", "S7_LIFESTYLE");
        task.setOpeningVideoUrl("https://example.com/opening.mp4");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.plan(task, "demo"));
        assertTrue(error.getMessage().contains("S7_LIFESTYLE"));
        assertTrue(error.getMessage().contains("尚无实际来源模块"));
    }

    @Test
    void nativeLeadOpeningCannotBorrowUnusedLibraryHookWithoutActualBodySource() {
        DefaultTkClipPlannerService service = plannerWith(material(1L, 7L, "GENERAL", "S1_HOOK"));
        TkGenerationTaskDO task = orderedTask(new int[]{1, 7}, "S1_HOOK", "S7_LIFESTYLE");
        task.setOpeningProcessMode(TkNativeOpeningSupport.MODE_NATIVE);
        task.setOpeningVideoUrl("https://example.com/opening.mp4");
        task.setOpeningDurationMs(1000L);
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.plan(task, "demo"));
        assertTrue(error.getMessage().contains("正文"));
    }

    @Test
    void leadFinalFillContinuesActualS5BehindS7TargetLabel() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 1L, "GENERAL", "S5_PROOF"),
                material(2L, 7L, "GENERAL", "S5_PROOF"),
                material(3L, 7L, "GENERAL", "S6_DETAIL"));
        List<TkClipPlanItem> plan = new ArrayList<>();
        plan.add(new TkClipPlanItem(1, "MATERIAL", 1L, "first", "https://example.com/first", 0, 1,
                "fallback", "S7_LIFESTYLE", "custom target", 1, null, "target script", "target visual", 8));
        ReflectionTestUtils.invokeMethod(service, "backfillLeadGenerationPlan", plan,
                Arrays.asList(material(1L, 1L, "GENERAL", "S5_PROOF"),
                        material(2L, 7L, "GENERAL", "S5_PROOF"),
                        material(3L, 7L, "GENERAL", "S6_DETAIL")), 2, 8);
        assertEquals(Arrays.asList(1L, 2L), materialIds(plan));
        assertEquals("S7_LIFESTYLE", plan.get(1).getSection());
        assertEquals("custom target", plan.get(1).getSectionName());
        assertEquals("target script", plan.get(1).getScriptLine());
        assertEquals("target visual", plan.get(1).getVisualDirection());
        assertEquals(8, plan.get(1).getSectionTargetSecond());
        assertTrue(plan.get(1).getReason().contains("S5_PROOF"));
    }

    @Test
    void exhaustedTailPoolReturnsOriginalWithoutUsingRemovedOrUnrelatedClips() {
        DefaultTkClipPlannerService service = plannerWith(
                material(1L, 3L, "GENERAL", "S5_PROOF"),
                material(2L, 5L, "GENERAL", "S5_PROOF"),
                material(3L, 8L, "GENERAL", "S6_DETAIL"));
        TkGenerationTaskDO task = orderedTask(new int[]{8}, "S7_LIFESTYLE");
        task.setMaterialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION);
        List<TkClipPlanItem> original = Arrays.asList(
                new TkClipPlanItem(1, "MATERIAL", 1L, "first", "https://example.com/first", 0, 3,
                        "fallback", "S7_LIFESTYLE", "lifestyle", 1, null),
                new TkClipPlanItem(2, "MATERIAL", 2L, "tail", "https://example.com/tail", 0, 5,
                        "tail", "S7_LIFESTYLE", "lifestyle", 2, null));
        org.junit.jupiter.api.Assertions.assertSame(original, service.replanTailForLowDynamic(task, original, 8));
    }

    private DefaultTkClipPlannerService plannerWith(TkMaterialVideoDO... materials) {
        DefaultTkClipPlannerService service = new DefaultTkClipPlannerService();
        ReflectionTestUtils.setField(service, "random", new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1; // Keep supplied own-module order stable without affecting fallback assertions.
            }
        });
        TkMaterialVideoMapper mapper = mock(TkMaterialVideoMapper.class);
        ReflectionTestUtils.setField(service, "materialVideoMapper", mapper);
        when(mapper.selectListByLibraryId(10L)).thenReturn(Arrays.asList(materials));
        return service;
    }

    private TkGenerationTaskDO orderedTask(int[] durations, String... sections) {
        StringBuilder config = new StringBuilder("[");
        int total = 0;
        for (int i = 0; i < sections.length; i++) {
            if (i > 0) {
                config.append(',');
            }
            config.append("{\"segmentType\":\"").append(sections[i])
                    .append("\",\"duration\":").append(durations[i]).append('}');
            total += durations[i];
        }
        return TkGenerationTaskDO.builder().libraryId(10L).targetDuration(total)
                .segmentDurationConfig(config.append(']').toString()).build();
    }

    private List<Long> materialIds(List<TkClipPlanItem> plan) {
        return plan.stream().map(TkClipPlanItem::getMaterialVideoId).collect(Collectors.toList());
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
                .fileName("material-" + id + ".mp4")
                .fileUrl("http://example.com/material-" + id + ".mp4")
                .duration(duration)
                .tags("脚步提拉带,支撑")
                .usagePhase(usagePhase)
                .segmentType(segmentType)
                .build();
    }

    private TkGenerationTaskDO recentTask(Long taskId, Long materialVideoId) {
        return TkGenerationTaskDO.builder()
                .id(taskId)
                .libraryId(10L)
                .clipPlan("[{\"materialVideoId\":" + materialVideoId + ",\"sourceType\":\"MATERIAL\"}]")
                .build();
    }

    private int durationOf(List<TkClipPlanItem> plan, String section) {
        return plan.stream()
                .filter(item -> section.equals(item.getSection()))
                .mapToInt(TkClipPlanItem::getDurationSecond)
                .sum();
    }

    private int sectionTargetOf(List<TkClipPlanItem> plan, String section) {
        return plan.stream()
                .filter(item -> section.equals(item.getSection()))
                .map(TkClipPlanItem::getSectionTargetSecond)
                .findFirst()
                .orElse(0);
    }

    private static class FixedRandom extends Random {

        private final int[] values;
        private int cursor;

        private FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[cursor++];
            return Math.floorMod(value, bound);
        }

    }

}
