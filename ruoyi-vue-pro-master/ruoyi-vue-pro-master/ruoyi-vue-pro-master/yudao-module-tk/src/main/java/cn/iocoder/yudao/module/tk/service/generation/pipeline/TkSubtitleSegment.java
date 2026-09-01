package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class TkSubtitleSegment {

    private String text;
    private double start;
    private double end;
    private String position;
    private int x;
    private int y;
    private List<TkSubtitleWord> words = new ArrayList<>();
    private Boolean wordTimingReliable = Boolean.TRUE;

    public TkSubtitleSegment(String text, double start, double end, String position, int x, int y,
                             List<TkSubtitleWord> words) {
        this.text = text;
        this.start = start;
        this.end = end;
        this.position = position;
        this.x = x;
        this.y = y;
        this.words = words;
    }

}
