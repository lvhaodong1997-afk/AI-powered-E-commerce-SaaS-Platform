package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
    void planLeadGenerationBackfillsWholeMaterialsUntilTargetDuration() {
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

        assertTrue(plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum() >= 50);
        assertTrue(plan.size() > 8);
        assertEquals("GENERAL", plan.get(8).getSection());
        assertTrue(plan.stream().skip(8).allMatch(item -> item.getReason().contains("补足")));
    }

    @Test
    void planLeadGenerationBackfillPrefersTightWholeMaterialCombination() {
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

        assertEquals(29, plan.stream().mapToInt(TkClipPlanItem::getDurationSecond).sum());
        List<Long> backfillIds = plan.stream().skip(8)
                .map(TkClipPlanItem::getMaterialVideoId)
                .collect(Collectors.toList());
        assertEquals(Arrays.asList(10L, 11L), backfillIds);
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
                material(4L, 6L, "GENERAL", "GENERAL")
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
    void planLeadGenerationFailsWhenAnyRequiredSegmentIsMissing() {
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "lead generation script"));

        assertTrue(exception.getMessage().contains("通用素材"));
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
    void planUsesFullPoolRandomModeAndFailsWhenNoClipCanFitTargetDuration() {
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "full pool random demo"));

        assertTrue(exception.getMessage().contains("目标"));
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
    void planRequiresExplicitSegmentTypesAndDoesNotMapUsagePhaseToStorySegment() {
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "portable blender demo result"));

        assertTrue(exception.getMessage().contains("黄金3秒用途素材不足"));
    }

    @Test
    void planDoesNotUseAdjacentOrGeneralMaterialsWhenStrictSegmentIsShort() {
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.plan(task, "demo proof"));

        assertTrue(exception.getMessage().contains("使用演示用途素材不足"));
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
