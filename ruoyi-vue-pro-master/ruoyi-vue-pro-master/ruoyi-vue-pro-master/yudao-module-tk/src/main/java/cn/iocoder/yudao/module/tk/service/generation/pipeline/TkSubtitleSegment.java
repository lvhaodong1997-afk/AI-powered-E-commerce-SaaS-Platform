package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkSubtitleSegment {

    private String text;
    private double start;
    private double end;
    private String position;
    private int x;
    private int y;
    private List<TkSubtitleWord> words = new ArrayList<>();

}
