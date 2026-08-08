package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkSubtitleLayoutServiceTest {

    private final DefaultTkSubtitleLayoutService service = new DefaultTkSubtitleLayoutService();

    @Test
    void smartSafeUsesBottomSafeAreaWhenNoVisualBoxesExist() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitlePositionMode("smart_safe")
                .build();
        TkSubtitleTimeline timeline = new TkSubtitleTimeline("en-US", 4D, Arrays.asList(
                new TkSubtitleSegment("First subtitle line should stay safe.", 0D, 2D, null, 0, 0, Collections.emptyList()),
                new TkSubtitleSegment("Second subtitle line should also stay safe.", 2D, 4D, null, 0, 0, Collections.emptyList())
        ));

        TkSubtitleLayout layout = service.layout(task, timeline, null);

        assertEquals(2, layout.getSegments().size());
        for (TkSubtitleSegment segment : layout.getSegments()) {
            assertEquals("bottom_center", segment.getPosition());
            assertEquals(540, segment.getX());
            assertTrue(segment.getY() >= 1400 && segment.getY() <= 1500,
                    "Default subtitle position should stay in the bottom safe area");
        }
    }

    @Test
    void fixedModesUseStableCoordinatesForEverySegment() {
        TkSubtitleLayout bottom = service.layout(task("fixed_bottom"), timeline(3), null);
        TkSubtitleLayout middle = service.layout(task("fixed_middle"), timeline(3), null);

        for (TkSubtitleSegment segment : bottom.getSegments()) {
            assertEquals("bottom_center", segment.getPosition());
            assertEquals(540, segment.getX());
            assertEquals(1450, segment.getY());
        }
        for (TkSubtitleSegment segment : middle.getSegments()) {
            assertEquals("middle_lower", segment.getPosition());
            assertEquals(540, segment.getX());
            assertEquals(1240, segment.getY());
        }
    }

    @Test
    void alternateModeSwitchesBetweenBottomAndTopBySegmentIndex() {
        TkSubtitleLayout layout = service.layout(task("alternate"), timeline(4), null);

        assertEquals("bottom_center", layout.getSegments().get(0).getPosition());
        assertEquals("top_center", layout.getSegments().get(1).getPosition());
        assertEquals("bottom_center", layout.getSegments().get(2).getPosition());
        assertEquals("top_center", layout.getSegments().get(3).getPosition());
    }

    @Test
    void sentenceRotateCyclesThroughFourPresetSafeAreas() {
        TkSubtitleLayout layout = service.layout(task("sentence_rotate"), timeline(5), null);

        assertEquals("top_center", layout.getSegments().get(0).getPosition());
        assertEquals("upper_center", layout.getSegments().get(1).getPosition());
        assertEquals("middle_lower", layout.getSegments().get(2).getPosition());
        assertEquals("bottom_center", layout.getSegments().get(3).getPosition());
        assertEquals("top_center", layout.getSegments().get(4).getPosition());
    }

    @Test
    void randomSafeModeOnlyUsesPresetSafeAreas() {
        TkSubtitleLayout layout = service.layout(task("random_safe"), timeline(12), null);
        Set<String> allowed = new HashSet<>(Arrays.asList(
                "top_center", "upper_center", "middle_lower", "bottom_center"));

        for (TkSubtitleSegment segment : layout.getSegments()) {
            assertTrue(allowed.contains(segment.getPosition()),
                    "Random safe mode should only use defined safe-area presets");
        }
    }

    @Test
    void smartSafeUsesVisualBoxesToAvoidBottomOverlap() {
        TkVisualAnalysis visualAnalysis = new TkVisualAnalysis(false, Collections.singletonList(
                new TkVisualAnalysis.TkVisualFrame(1D, Collections.singletonList(
                        new TkVisualAnalysis.TkVisualBox("product", 100, 1360, 900, 260, 1D)
                ))
        ));

        TkSubtitleLayout layout = service.layout(task("smart_safe"), timeline(1), visualAnalysis);

        assertEquals("middle_lower", layout.getSegments().get(0).getPosition());
        assertEquals(1240, layout.getSegments().get(0).getY());
    }

    @Test
    void smartSafeCanUseLeftUpperWhenLowerSafeAreasAreBlocked() {
        TkVisualAnalysis visualAnalysis = new TkVisualAnalysis(true, Collections.singletonList(
                new TkVisualAnalysis.TkVisualFrame(0.5D, Arrays.asList(
                        new TkVisualAnalysis.TkVisualBox("product", 100, 1360, 900, 260, 1D),
                        new TkVisualAnalysis.TkVisualBox("person", 100, 1150, 900, 240, 1D),
                        new TkVisualAnalysis.TkVisualBox("logo", 20, 1080, 660, 240, 1D)
                ))
        ));

        TkSubtitleLayout layout = service.layout(task("smart_safe"), timeline(1), visualAnalysis);

        assertEquals("left_upper", layout.getSegments().get(0).getPosition());
        assertEquals(360, layout.getSegments().get(0).getX());
        assertEquals(520, layout.getSegments().get(0).getY());
    }

    @Test
    void randomSafeModeIsStableForSameTaskAndSegments() {
        TkGenerationTaskDO task = task("random_safe", 2074769964879192064L);

        String first = positionNames(service.layout(task, timeline(16), null));
        String second = positionNames(service.layout(task, timeline(16), null));

        assertEquals(first, second,
                "Random safe mode should be stable for the same task and segment index");
    }

    @Test
    void randomSafeModeFiltersVisuallyBlockedAreasBeforeChoosing() {
        TkGenerationTaskDO task = task("random_safe", 2074769578814480384L);
        TkVisualAnalysis visualAnalysis = new TkVisualAnalysis(false, Collections.singletonList(
                new TkVisualAnalysis.TkVisualFrame(0.5D, Collections.singletonList(
                        new TkVisualAnalysis.TkVisualBox("product", 100, 1360, 900, 260, 1D)
                ))
        ));

        TkSubtitleLayout layout = service.layout(task, timeline(30), visualAnalysis);

        assertFalse(positionNames(layout).contains("bottom_center"),
                "Random safe mode should not choose visually blocked bottom captions");
    }

    private TkGenerationTaskDO task(String positionMode) {
        return task(positionMode, null);
    }

    private TkGenerationTaskDO task(String positionMode, Long id) {
        return TkGenerationTaskDO.builder()
                .id(id)
                .subtitlePositionMode(positionMode)
                .build();
    }

    private TkSubtitleTimeline timeline(int segmentCount) {
        List<TkSubtitleSegment> segments = new java.util.ArrayList<>();
        for (int i = 0; i < segmentCount; i++) {
            segments.add(new TkSubtitleSegment(
                    "Subtitle line " + i,
                    i,
                    i + 1D,
                    null,
                    0,
                    0,
                    Collections.emptyList()
            ));
        }
        return new TkSubtitleTimeline("en-US", segmentCount, segments);
    }

    private String positionNames(TkSubtitleLayout layout) {
        return layout.getSegments().stream()
                .map(TkSubtitleSegment::getPosition)
                .collect(Collectors.joining(","));
    }
}
