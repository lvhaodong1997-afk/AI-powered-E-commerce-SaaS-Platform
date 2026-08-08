package cn.iocoder.yudao.module.tk.controller.admin.generation.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TkGenerationBatchRespVO {

    private Long id;
    private Long tenantId;
    private String batchNo;
    private String name;
    private Long companyId;
    private Long libraryId;
    private String sourceUrl;
    private String targetLanguage;
    private Integer scriptCount;
    private Integer videosPerScript;
    private Integer expectedVideoCount;
    private Integer createdTaskCount;
    private Integer successTaskCount;
    private Integer failedTaskCount;
    private Integer runningTaskCount;
    private Integer progressPercent;
    private String status;
    private String failSummary;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
