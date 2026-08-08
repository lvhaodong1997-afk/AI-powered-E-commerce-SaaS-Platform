package cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Admin - TK dashboard material health Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkDashboardMaterialHealthRespVO {

    private Long libraryCount;
    private Long materialVideoCount;
    private Long availableVideoCount;
    private Long parsingVideoCount;
    private Long failedVideoCount;
    private List<LibraryHealthItem> libraries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LibraryHealthItem {
        private Long libraryId;
        private String libraryName;
        private Integer libraryStatus;
        private Long videoCount;
        private Long availableVideoCount;
        private Long parsingVideoCount;
        private Long failedVideoCount;
        private Long generationCount;
        private LocalDateTime lastUsedTime;
        private String healthStatus;
    }

}
