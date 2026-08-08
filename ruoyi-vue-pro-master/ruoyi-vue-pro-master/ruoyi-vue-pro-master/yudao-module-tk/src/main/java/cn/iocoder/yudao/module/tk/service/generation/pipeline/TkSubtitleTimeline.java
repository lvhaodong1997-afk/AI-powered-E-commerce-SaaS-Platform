package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkSubtitleTimeline {

    private String language;
    private double audioDuration;
    private List<TkSubtitleSegment> segments = new ArrayList<>();

}
