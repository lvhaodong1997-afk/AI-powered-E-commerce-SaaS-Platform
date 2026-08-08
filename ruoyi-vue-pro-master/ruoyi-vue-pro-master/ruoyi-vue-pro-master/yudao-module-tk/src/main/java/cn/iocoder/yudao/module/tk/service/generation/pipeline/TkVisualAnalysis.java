package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkVisualAnalysis {

    private boolean centerSubjectLikely;
    private List<TkVisualFrame> frames = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TkVisualFrame {

        private double time;
        private List<TkVisualBox> boxes = new ArrayList<>();

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TkVisualBox {

        private String label;
        private int x;
        private int y;
        private int w;
        private int h;
        private double score;

    }

}
