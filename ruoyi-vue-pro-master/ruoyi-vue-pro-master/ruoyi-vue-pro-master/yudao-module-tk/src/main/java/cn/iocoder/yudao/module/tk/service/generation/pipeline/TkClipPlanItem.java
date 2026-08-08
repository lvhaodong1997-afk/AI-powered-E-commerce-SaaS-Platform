package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TkClipPlanItem {

    private Integer orderNo;
    private String sourceType;
    private Long materialVideoId;
    private String fileName;
    private String fileUrl;
    private Integer startSecond;
    private Integer durationSecond;
    private String reason;
    private String section;
    private String sectionName;
    private Integer sectionOrder;
    private Integer matchScore;
    private String scriptLine;
    private String visualDirection;
    private Integer sectionTargetSecond;

    public TkClipPlanItem(Integer orderNo, String sourceType, Long materialVideoId, String fileName, String fileUrl,
                          Integer startSecond, Integer durationSecond, String reason) {
        this(orderNo, sourceType, materialVideoId, fileName, fileUrl, startSecond, durationSecond, reason,
                null, null, null, null, null, null);
    }

    public TkClipPlanItem(Integer orderNo, String sourceType, Long materialVideoId, String fileName, String fileUrl,
                          Integer startSecond, Integer durationSecond, String reason, String section,
                          String sectionName, Integer sectionOrder, Integer matchScore) {
        this(orderNo, sourceType, materialVideoId, fileName, fileUrl, startSecond, durationSecond, reason,
                section, sectionName, sectionOrder, matchScore, null, null);
    }

    public TkClipPlanItem(Integer orderNo, String sourceType, Long materialVideoId, String fileName, String fileUrl,
                          Integer startSecond, Integer durationSecond, String reason, String section,
                          String sectionName, Integer sectionOrder, Integer matchScore, String scriptLine,
                          String visualDirection) {
        this(orderNo, sourceType, materialVideoId, fileName, fileUrl, startSecond, durationSecond, reason,
                section, sectionName, sectionOrder, matchScore, scriptLine, visualDirection, null);
    }

    public TkClipPlanItem(Integer orderNo, String sourceType, Long materialVideoId, String fileName, String fileUrl,
                          Integer startSecond, Integer durationSecond, String reason, String section,
                          String sectionName, Integer sectionOrder, Integer matchScore, String scriptLine,
                          String visualDirection, Integer sectionTargetSecond) {
        this.orderNo = orderNo;
        this.sourceType = sourceType;
        this.materialVideoId = materialVideoId;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.startSecond = startSecond;
        this.durationSecond = durationSecond;
        this.reason = reason;
        this.section = section;
        this.sectionName = sectionName;
        this.sectionOrder = sectionOrder;
        this.matchScore = matchScore;
        this.scriptLine = scriptLine;
        this.visualDirection = visualDirection;
        this.sectionTargetSecond = sectionTargetSecond;
    }

}
