package cn.iocoder.yudao.module.tk.controller.admin.openapi.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TkOpenApiGovernanceVO {

    private TkOpenApiGovernanceVO() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EventPageReq extends PageParam {
        private String clientId;
        private String eventType;
        private String status;
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTimeStart;
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime createTimeEnd;
    }

    @Data
    public static class EventResp {
        private String eventId;
        private String clientId;
        private String eventType;
        private String resourceType;
        private String resourceId;
        private String callbackUrl;
        private String payloadJson;
        private String status;
        private Integer attemptCount;
        private LocalDateTime nextRetryTime;
        private Integer lastHttpStatus;
        private String lastError;
        private LocalDateTime deliveredTime;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class UsageResp {
        private LocalDate requestDate;
        private String clientId;
        private Long requestCount;
        private Long successCount;
        private Long failureCount;
        private Long averageDurationMs;
    }
}
