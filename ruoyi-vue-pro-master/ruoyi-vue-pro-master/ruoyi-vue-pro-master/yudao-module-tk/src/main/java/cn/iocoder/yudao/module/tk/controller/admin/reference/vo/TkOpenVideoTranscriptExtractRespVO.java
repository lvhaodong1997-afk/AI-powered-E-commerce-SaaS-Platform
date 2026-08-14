package cn.iocoder.yudao.module.tk.controller.admin.reference.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - TK Open 视频文案时间轴提取 Response VO")
@Data
@Builder
public class TkOpenVideoTranscriptExtractRespVO {

    private Long taskId;
    private String status;
    private String failReason;
    private String sourceUrl;
    private String targetLanguage;
    private String resolvedVideoUrl;
    private String coverUrl;
    private Integer videoDuration;
    private String resolution;
    private String audioUrl;
    private Double audioDuration;
    private String transcriptText;
    private List<Map<String, Object>> segments;
    private List<Map<String, Object>> words;
    private String asrProvider;
    private String asrModel;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
