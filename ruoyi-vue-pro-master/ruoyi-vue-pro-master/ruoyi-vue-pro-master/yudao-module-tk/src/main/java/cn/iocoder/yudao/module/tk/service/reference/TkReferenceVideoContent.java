package cn.iocoder.yudao.module.tk.service.reference;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TkReferenceVideoContent {

    private String sourceUrl;
    private String resolvedVideoUrl;
    private String coverUrl;
    private Long durationSeconds;
    private String resolution;
    private List<Frame> frames;

    @Data
    @AllArgsConstructor
    public static class Frame {

        private Integer second;
        private String mimeType;
        private String base64Data;

    }

}
