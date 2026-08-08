package cn.iocoder.yudao.module.tk.service.dashboard;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardCountItemRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureAnalysisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardFailureDiagnosisRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardMaterialHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardOverviewRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueryReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardQueueHealthRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSlowTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.dashboard.vo.TkDashboardTrendRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskSummaryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCreditLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Validated
public class TkDashboardServiceImpl implements TkDashboardService {

    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkCreditLogMapper creditLogMapper;
    @Resource
    private TkTiktokAccountMapper tiktokAccountMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public TkDashboardSummaryRespVO getSummary() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        return TenantUtils.executeIgnore(() -> new TkDashboardSummaryRespVO(
                        taskMapper.selectCount(scope, todayStart, tomorrowStart),
                        videoMapper.selectCount(scope),
                        videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.PARSING),
                        creditLogMapper.selectSettledCredits(scope, todayStart, tomorrowStart),
                        BeanUtils.toBean(libraryMapper.selectTop5(scope), TkMaterialLibraryRespVO.class, this::fillLibraryPreview),
                        BeanUtils.toBean(taskMapper.selectTop5(scope), TkGenerationTaskRespVO.class)
                )
        );
    }

    @Override
    public TkDashboardOverviewRespVO getOverview(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> {
            List<TkDashboardCountItemRespVO> statusStats = taskMapper.selectStatusStats(scope,
                    range.startTime, range.endTime, query.getLibraryId(), query.getTargetLanguage(), query.getStatus());
            Long total = sum(statusStats);
            Long success = valueOf(statusStats, TkGenerationStatusEnum.SUCCESS);
            Long failed = valueOf(statusStats, TkGenerationStatusEnum.FAILED);
            Long running = Math.max(0L, total - success - failed);
            Long consumedCredits = creditLogMapper.selectSettledCredits(scope, range.startTime, range.endTime);
            return new TkDashboardOverviewRespVO(
                    total,
                    success,
                    failed,
                    running,
                    successRate(success, total),
                    averageDuration(taskMapper.selectGenerationTrend(scope, range.startTime, range.endTime,
                            query.getLibraryId(), query.getTargetLanguage(), query.getStatus())),
                    consumedCredits,
                    libraryMapper.selectCount(scope),
                    videoMapper.selectCount(scope, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.AVAILABLE, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.PARSING, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.FAILED, query.getLibraryId()),
                    tiktokAccountMapper.selectAuthorizedCount(scope),
                    tiktokAccountMapper.selectTokenAbnormalCount(scope)
            );
        });
    }

    @Override
    public TkDashboardTrendRespVO getGenerationTrend(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> {
            List<TkDashboardTrendRespVO.TrendItem> items = taskMapper.selectGenerationTrend(scope,
                    range.startTime, range.endTime, query.getLibraryId(), query.getTargetLanguage(), query.getStatus());
            Map<String, TkDashboardCountItemRespVO> creditByDay = creditLogMapper
                    .selectDailySettledCredits(scope, range.startTime, range.endTime)
                    .stream()
                    .collect(Collectors.toMap(TkDashboardCountItemRespVO::getName, Function.identity(),
                            (first, second) -> first));
            items.forEach(item -> {
                TkDashboardCountItemRespVO credit = creditByDay.get(item.getDay());
                item.setConsumedCredits(credit == null ? 0L : safeLong(credit.getValue()));
            });
            return new TkDashboardTrendRespVO(items);
        });
    }

    @Override
    public TkDashboardFailureAnalysisRespVO getFailureAnalysis(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> new TkDashboardFailureAnalysisRespVO(
                taskMapper.selectFailureReasonStats(scope, range.startTime, range.endTime,
                                query.getLibraryId(), query.getTargetLanguage(), 8)
                        .stream()
                        .map(item -> new TkDashboardFailureAnalysisRespVO.FailureReasonItem(
                                normalizedName(item.getName()), failureLabel(item.getName()), safeLong(item.getValue())))
                        .collect(Collectors.toList()),
                taskMapper.selectFailureStepStats(scope, range.startTime, range.endTime,
                                query.getLibraryId(), query.getTargetLanguage(), 8)
                        .stream()
                        .map(item -> new TkDashboardFailureAnalysisRespVO.FailureStepItem(
                                normalizedName(item.getName()), safeLong(item.getValue())))
                        .collect(Collectors.toList()),
                BeanUtils.toBean(taskMapper.selectRecentFailures(scope, range.startTime, range.endTime,
                                query.getLibraryId(), query.getTargetLanguage(), 10),
                        TkGenerationTaskSummaryRespVO.class)
        ));
    }

    @Override
    public TkDashboardMaterialHealthRespVO getMaterialHealth(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> {
            List<TkDashboardMaterialHealthRespVO.LibraryHealthItem> libraries =
                    libraryMapper.selectHealthStats(scope, range.startTime, range.endTime,
                            query.getLibraryId(), query.getTargetLanguage(), 20);
            libraries.forEach(this::fillHealthStatus);
            return new TkDashboardMaterialHealthRespVO(
                    libraryMapper.selectCount(scope),
                    videoMapper.selectCount(scope, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.AVAILABLE, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.PARSING, query.getLibraryId()),
                    videoMapper.selectCountByStatus(scope, TkMaterialVideoStatusEnum.FAILED, query.getLibraryId()),
                    libraries
            );
        });
    }

    @Override
    public TkDashboardQueueHealthRespVO getQueueHealth(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(10);
        return TenantUtils.executeIgnore(() -> new TkDashboardQueueHealthRespVO(
                safeLong(taskMapper.selectPendingQueueCount(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus())),
                safeLong(taskMapper.selectRunningQueueCount(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus())),
                safeLong(taskMapper.selectStaleRunningCount(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus(), staleBefore)),
                safeLong(taskMapper.selectAveragePendingSeconds(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus())),
                safeLong(taskMapper.selectAverageRunningSeconds(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus())),
                BeanUtils.toBean(taskMapper.selectQueueAttentionTasks(scope, range.startTime, range.endTime,
                                query.getLibraryId(), query.getTargetLanguage(), query.getStatus(), staleBefore, 8),
                        TkGenerationTaskSummaryRespVO.class)
        ));
    }

    @Override
    public TkDashboardFailureDiagnosisRespVO getFailureDiagnosis(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> {
            Map<String, TkDashboardFailureDiagnosisRespVO.DiagnosisItem> items = new LinkedHashMap<>();
            for (TkGenerationTaskDO task : taskMapper.selectFailureDiagnosisSamples(scope, range.startTime, range.endTime,
                    query.getLibraryId(), query.getTargetLanguage(), 500)) {
                TkDashboardDiagnostics.FailureDiagnosis diagnosis = TkDashboardDiagnostics.classifyFailure(
                        task.getFailCode(), task.getCurrentStep(), task.getFailReason());
                TkDashboardFailureDiagnosisRespVO.DiagnosisItem item = items.computeIfAbsent(
                        diagnosis.getCategory(),
                        category -> new TkDashboardFailureDiagnosisRespVO.DiagnosisItem(category,
                                failureLabel(category), 0L, diagnosis.getActionStatus(), diagnosis.getActionHint()));
                item.setCount(safeLong(item.getCount()) + 1);
            }
            return new TkDashboardFailureDiagnosisRespVO(items.values().stream()
                    .sorted((first, second) -> Long.compare(safeLong(second.getCount()), safeLong(first.getCount())))
                    .collect(Collectors.toList()));
        });
    }

    @Override
    public TkDashboardSlowTaskRespVO getSlowTasks(TkDashboardQueryReqVO reqVO) {
        final TkDashboardQueryReqVO query = safeReq(reqVO);
        TkUserScope scope = dataScopeService.getCurrentScope();
        DateRange range = normalizeRange(query);
        return TenantUtils.executeIgnore(() -> new TkDashboardSlowTaskRespVO(
                taskMapper.selectSlowTasks(scope, range.startTime, range.endTime,
                        query.getLibraryId(), query.getTargetLanguage(), query.getStatus(), 10)
        ));
    }

    private void fillLibraryPreview(TkMaterialLibraryRespVO library) {
        if (library == null || library.getId() == null) {
            return;
        }
        TkMaterialVideoDO firstVideo = videoMapper.selectFirstByLibraryId(library.getId());
        if (firstVideo == null) {
            return;
        }
        library.setCoverUrl(StrUtil.blankToDefault(firstVideo.getCoverUrl(), library.getCoverUrl()));
        library.setPreviewVideoUrl(firstVideo.getFileUrl());
    }

    private DateRange normalizeRange(TkDashboardQueryReqVO reqVO) {
        LocalDateTime endTime = reqVO.getEndTime() == null
                ? LocalDate.now().plusDays(1).atStartOfDay()
                : reqVO.getEndTime();
        LocalDateTime startTime = reqVO.getStartTime() == null
                ? endTime.toLocalDate().minusDays(6).atStartOfDay()
                : reqVO.getStartTime();
        if (!startTime.isBefore(endTime)) {
            startTime = endTime.minusDays(6).truncatedTo(ChronoUnit.DAYS);
        }
        return new DateRange(startTime, endTime);
    }

    private TkDashboardQueryReqVO safeReq(TkDashboardQueryReqVO reqVO) {
        return reqVO == null ? new TkDashboardQueryReqVO() : reqVO;
    }

    private Long sum(List<TkDashboardCountItemRespVO> items) {
        return items.stream().map(TkDashboardCountItemRespVO::getValue).mapToLong(this::safeLong).sum();
    }

    private Long valueOf(List<TkDashboardCountItemRespVO> items, String name) {
        return items.stream()
                .filter(item -> StrUtil.equals(name, item.getName()))
                .findFirst()
                .map(TkDashboardCountItemRespVO::getValue)
                .map(this::safeLong)
                .orElse(0L);
    }

    private Long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Integer successRate(Long success, Long total) {
        if (total == null || total <= 0) {
            return 0;
        }
        return Math.toIntExact(Math.round(success * 100D / total));
    }

    private Long averageDuration(List<TkDashboardTrendRespVO.TrendItem> items) {
        long totalDuration = 0L;
        long days = 0L;
        for (TkDashboardTrendRespVO.TrendItem item : items) {
            if (item.getAverageDurationSeconds() != null && item.getAverageDurationSeconds() > 0) {
                totalDuration += item.getAverageDurationSeconds();
                days++;
            }
        }
        return days == 0 ? 0L : Math.round(totalDuration * 1D / days);
    }

    private String normalizedName(String value) {
        return StrUtil.blankToDefault(value, "UNKNOWN");
    }

    private String failureLabel(String failCode) {
        String code = normalizedName(failCode).toUpperCase();
        if (StrUtil.containsAny(code, "DOWNLOAD", "REFERENCE")) {
            return "REFERENCE_DOWNLOAD";
        }
        if (StrUtil.containsAny(code, "OSS", "STORAGE", "UPLOAD")) {
            return "OSS_STORAGE";
        }
        if (StrUtil.containsAny(code, "FFMPEG", "RENDER", "EXPORT")) {
            return "FFMPEG_RENDER";
        }
        if (StrUtil.containsAny(code, "VOICE", "AUDIO", "TTS")) {
            return "VOICEOVER";
        }
        if (StrUtil.containsAny(code, "SUBTITLE", "ASS")) {
            return "SUBTITLE";
        }
        if (StrUtil.containsAny(code, "TIKTOK", "AUTH", "PUBLISH")) {
            return "TIKTOK_PUBLISH";
        }
        return code;
    }

    private void fillHealthStatus(TkDashboardMaterialHealthRespVO.LibraryHealthItem item) {
        long videoCount = safeLong(item.getVideoCount());
        long available = safeLong(item.getAvailableVideoCount());
        long failed = safeLong(item.getFailedVideoCount());
        if (videoCount <= 0 || available <= 0) {
            item.setHealthStatus("INSUFFICIENT");
            return;
        }
        if (failed > 0) {
            item.setHealthStatus("HAS_FAILED_MATERIALS");
            return;
        }
        if (item.getLastUsedTime() == null || item.getLastUsedTime().isBefore(LocalDateTime.now().minusDays(30))) {
            item.setHealthStatus("LOW_ACTIVITY");
            return;
        }
        item.setHealthStatus("HEALTHY");
    }

    private static class DateRange {
        private final LocalDateTime startTime;
        private final LocalDateTime endTime;

        private DateRange(LocalDateTime startTime, LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }

}
