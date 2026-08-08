package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class DefaultTkSubtitleLayoutService implements TkSubtitleLayoutService {

    private static final int CENTER_X = 540;
    private static final double SAFE_SCORE_WINDOW = 0.35D;
    private static final SubtitlePosition TOP = new SubtitlePosition("top_center", CENTER_X, 360);
    private static final SubtitlePosition UPPER = new SubtitlePosition("upper_center", CENTER_X, 520);
    private static final SubtitlePosition MIDDLE_LOWER = new SubtitlePosition("middle_lower", CENTER_X, 1240);
    private static final SubtitlePosition BOTTOM = new SubtitlePosition("bottom_center", CENTER_X, 1450);
    private static final SubtitlePosition LEFT_UPPER = new SubtitlePosition("left_upper", 360, 520);
    private static final SubtitlePosition LEFT_LOWER = new SubtitlePosition("left_lower", 360, 1180);

    @Override
    public TkSubtitleLayout layout(TkGenerationTaskDO task, TkSubtitleTimeline timeline, TkVisualAnalysis visualAnalysis) {
        String mode = StrUtil.blankToDefault(task.getSubtitlePositionMode(), "smart_safe");
        List<SubtitlePosition> positions = allowedPositions(mode, visualAnalysis);
        TkSubtitleLayout layout = new TkSubtitleLayout(new ArrayList<>());
        for (int i = 0; i < timeline.getSegments().size(); i++) {
            TkSubtitleSegment segment = timeline.getSegments().get(i);
            SubtitlePosition position = choosePosition(task, mode, positions, i, segment, visualAnalysis);
            segment.setPosition(position.name);
            segment.setX(position.x);
            segment.setY(position.y);
            layout.getSegments().add(segment);
        }
        return layout;
    }

    private List<SubtitlePosition> allowedPositions(String mode, TkVisualAnalysis visualAnalysis) {
        List<SubtitlePosition> positions = new ArrayList<>();
        if (StrUtil.equals(mode, "fixed_bottom")) {
            positions.add(BOTTOM);
            return positions;
        }
        if (StrUtil.equals(mode, "fixed_middle")) {
            positions.add(MIDDLE_LOWER);
            return positions;
        }
        if (StrUtil.equals(mode, "smart_safe") && visualAnalysis != null && visualAnalysis.isCenterSubjectLikely()) {
            positions.add(BOTTOM);
            positions.add(MIDDLE_LOWER);
            positions.add(LEFT_LOWER);
            positions.add(LEFT_UPPER);
            return positions;
        }
        if (StrUtil.equals(mode, "smart_safe")) {
            positions.add(BOTTOM);
            positions.add(MIDDLE_LOWER);
            return positions;
        }
        positions.add(TOP);
        positions.add(UPPER);
        positions.add(MIDDLE_LOWER);
        positions.add(BOTTOM);
        return positions;
    }

    private SubtitlePosition choosePosition(TkGenerationTaskDO task, String mode, List<SubtitlePosition> positions, int index,
                                            TkSubtitleSegment segment, TkVisualAnalysis visualAnalysis) {
        if (positions.isEmpty()) {
            return BOTTOM;
        }
        if (StrUtil.equals(mode, "smart_safe") && hasBoxes(visualAnalysis)) {
            return bestVisualPosition(positions, segment, visualAnalysis, index);
        }
        if (StrUtil.equals(mode, "random_safe")) {
            if (hasBoxes(visualAnalysis)) {
                return stableRandomVisualPosition(task, positions, index, segment, visualAnalysis);
            }
            return stableRandomPosition(task, positions, index, segment);
        }
        if (StrUtil.equals(mode, "alternate")) {
            return index % 2 == 0 ? BOTTOM : TOP;
        }
        if (StrUtil.equals(mode, "smart_safe")) {
            return positions.get(0);
        }
        if (StrUtil.equals(mode, "sentence_rotate") || StrUtil.equals(mode, "scene_rotate")) {
            return positions.get(index % positions.size());
        }
        return positions.get(0);
    }

    private SubtitlePosition stableRandomVisualPosition(TkGenerationTaskDO task, List<SubtitlePosition> positions,
                                                        int index, TkSubtitleSegment segment,
                                                        TkVisualAnalysis visualAnalysis) {
        List<PositionScore> scores = new ArrayList<>();
        double bestScore = Double.MAX_VALUE;
        for (SubtitlePosition position : positions) {
            double score = scorePosition(position, segment, visualAnalysis);
            scores.add(new PositionScore(position, score));
            if (score < bestScore) {
                bestScore = score;
            }
        }
        List<SubtitlePosition> safePositions = new ArrayList<>();
        for (PositionScore score : scores) {
            if (score.score <= bestScore + SAFE_SCORE_WINDOW) {
                safePositions.add(score.position);
            }
        }
        if (safePositions.isEmpty()) {
            safePositions.add(scores.get(0).position);
        }
        return stableRandomPosition(task, safePositions, index, segment);
    }

    private SubtitlePosition stableRandomPosition(TkGenerationTaskDO task, List<SubtitlePosition> positions,
                                                  int index, TkSubtitleSegment segment) {
        return positions.get(stableIndex(task, index, segment, positions.size()));
    }

    private int stableIndex(TkGenerationTaskDO task, int index, TkSubtitleSegment segment, int size) {
        Long taskId = task == null ? null : task.getId();
        int hash = Objects.hash(taskId, index, segment.getStart(), segment.getEnd(), segment.getText());
        return Math.floorMod(hash, size);
    }

    private SubtitlePosition bestVisualPosition(List<SubtitlePosition> positions, TkSubtitleSegment segment,
                                                TkVisualAnalysis visualAnalysis, int index) {
        SubtitlePosition best = positions.get(0);
        double bestScore = Double.MAX_VALUE;
        for (SubtitlePosition position : positions) {
            double score = scorePosition(position, segment, visualAnalysis);
            if (position.equals(index % 2 == 0 ? BOTTOM : TOP)) {
                score -= 0.2D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = position;
            }
        }
        return best;
    }

    private double scorePosition(SubtitlePosition position, TkSubtitleSegment segment, TkVisualAnalysis visualAnalysis) {
        Rect subtitle = subtitleRect(position);
        double score = 0D;
        for (TkVisualAnalysis.TkVisualFrame frame : visualAnalysis.getFrames()) {
            if (frame.getTime() < segment.getStart() || frame.getTime() > segment.getEnd() || frame.getBoxes() == null) {
                continue;
            }
            for (TkVisualAnalysis.TkVisualBox box : frame.getBoxes()) {
                Rect object = new Rect(box.getX(), box.getY(), box.getX() + box.getW(), box.getY() + box.getH());
                double overlap = subtitle.overlapRatio(object);
                double weight = boxWeight(box.getLabel()) * Math.max(0.2D, box.getScore());
                score += overlap * weight;
            }
        }
        if (subtitle.bottom > 1540) {
            score += 0.8D;
        }
        if (subtitle.right > 920 && subtitle.bottom > 1100) {
            score += 0.6D;
        }
        return score;
    }

    private double boxWeight(String label) {
        if (StrUtil.containsAnyIgnoreCase(label, "face", "head", "person")) {
            return 4D;
        }
        if (StrUtil.containsAnyIgnoreCase(label, "product", "goods", "item", "logo")) {
            return 3D;
        }
        return 1.5D;
    }

    private Rect subtitleRect(SubtitlePosition position) {
        int halfWidth = position.x == CENTER_X ? 430 : 330;
        int halfHeight = 88;
        return new Rect(position.x - halfWidth, position.y - halfHeight, position.x + halfWidth, position.y + halfHeight);
    }

    private boolean hasBoxes(TkVisualAnalysis visualAnalysis) {
        return visualAnalysis != null
                && visualAnalysis.getFrames() != null
                && visualAnalysis.getFrames().stream().anyMatch(frame -> frame.getBoxes() != null && !frame.getBoxes().isEmpty());
    }

    private static class SubtitlePosition {

        private final String name;
        private final int x;
        private final int y;

        private SubtitlePosition(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    private static class PositionScore {

        private final SubtitlePosition position;
        private final double score;

        private PositionScore(SubtitlePosition position, double score) {
            this.position = position;
            this.score = score;
        }
    }

    private static class Rect {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private Rect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private double overlapRatio(Rect other) {
            int width = Math.max(0, Math.min(right, other.right) - Math.max(left, other.left));
            int height = Math.max(0, Math.min(bottom, other.bottom) - Math.max(top, other.top));
            double intersection = width * height;
            double area = Math.max(1D, (right - left) * (double) (bottom - top));
            return intersection / area;
        }
    }

}
