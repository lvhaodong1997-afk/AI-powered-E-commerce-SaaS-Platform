package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceAnalysisDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCleanupFileMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkReferenceAnalysisMapper;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TkFileCleanupService {

    private static final List<String> GENERATION_URL_COLUMNS = Arrays.asList(
            "output_url",
            "audio_url",
            "subtitle_url",
            "subtitle_timeline_url",
            "subtitle_visual_analysis_url",
            "subtitle_layout_url",
            "subtitle_ass_url");

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkCleanupFileMapper cleanupFileMapper;
    @Resource
    private TkGenerationTaskMapper generationTaskMapper;
    @Resource
    private TkReferenceAnalysisMapper referenceAnalysisMapper;
    @Resource
    private FileService fileService;
    @Resource
    private TkMaterialOssUploadService ossUploadService;

    public CleanupResult cleanupExpiredFiles() {
        TkGenerationProperties.Cleanup cleanup = generationProperties.getCleanup();
        if (!Boolean.TRUE.equals(cleanup.getEnabled())) {
            return new CleanupResult(0, 0);
        }
        LocalDateTime now = LocalDateTime.now();
        int generatedCount = cleanupExpiredGeneratedTaskFiles(now, cleanup);
        generatedCount += cleanupExpiredGenerationTaskUrlColumns(now, cleanup);
        int referenceCount = cleanupExpiredReferencePreviewFiles(now, cleanup);
        return new CleanupResult(generatedCount, referenceCount);
    }

    private int cleanupExpiredGeneratedTaskFiles(LocalDateTime now, TkGenerationProperties.Cleanup cleanup) {
        LocalDateTime deadline = now.minusHours(normalizeHours(cleanup.getGeneratedVideoRetentionHours()));
        List<FileDO> candidates = cleanupFileMapper.selectExpiredGenerationTaskCandidates(deadline, normalizeBatchSize(cleanup.getBatchSize()));
        Map<Long, List<FileDO>> filesByTaskId = filterGenerationFilesByTaskId(candidates);
        if (filesByTaskId.isEmpty()) {
            return 0;
        }

        List<TkGenerationTaskDO> tasks = generationTaskMapper.selectByIds(filesByTaskId.keySet());
        Set<Long> cleanableTaskIds = tasks.stream()
                .filter(this::isCleanableGenerationTask)
                .map(TkGenerationTaskDO::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (cleanableTaskIds.isEmpty()) {
            return 0;
        }

        List<FileDO> expiredFiles = filesByTaskId.entrySet().stream()
                .filter(entry -> cleanableTaskIds.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(expiredFiles)) {
            return 0;
        }
        if (Boolean.TRUE.equals(cleanup.getDryRun())) {
            log.info("[cleanupExpiredGeneratedTaskFiles][dryRun taskIds({}) fileCount({})]", cleanableTaskIds, expiredFiles.size());
            return expiredFiles.size();
        }

        List<FileDO> deletedFiles = deleteFiles(expiredFiles);
        clearGenerationTaskFileUrls(deletedFiles);
        return deletedFiles.size();
    }

    private int cleanupExpiredGenerationTaskUrlColumns(LocalDateTime now, TkGenerationProperties.Cleanup cleanup) {
        LocalDateTime deadline = now.minusHours(normalizeHours(cleanup.getGeneratedVideoRetentionHours()));
        List<TkGenerationTaskDO> tasks = generationTaskMapper.selectExpiredTasksWithGenerationUrls(deadline,
                normalizeBatchSize(cleanup.getBatchSize()));
        if (CollUtil.isEmpty(tasks)) {
            return 0;
        }
        int cleanedCount = 0;
        for (TkGenerationTaskDO task : tasks) {
            Set<String> generationUrls = collectGenerationUrls(task);
            if (generationUrls.isEmpty()) {
                continue;
            }
            if (Boolean.TRUE.equals(cleanup.getDryRun())) {
                log.info("[cleanupExpiredGenerationTaskUrlColumns][dryRun taskId({}) urlCount({})]",
                        task.getId(), generationUrls.size());
                cleanedCount++;
                continue;
            }
            deleteManagedOssGenerationUrls(generationUrls);
            if (clearGenerationTaskColumnsByPaths(task.getId(), generationUrls)) {
                cleanedCount++;
            }
        }
        return cleanedCount;
    }

    private int cleanupExpiredReferencePreviewFiles(LocalDateTime now, TkGenerationProperties.Cleanup cleanup) {
        LocalDateTime deadline = now.minusHours(normalizeHours(cleanup.getReferenceVideoRetentionHours()));
        List<FileDO> expiredFiles = cleanupFileMapper.selectExpiredReferencePreviewCandidates(deadline, normalizeBatchSize(cleanup.getBatchSize()))
                .stream()
                .filter(file -> TkFileCleanupPathPolicy.isReferencePreviewPath(file.getPath()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(expiredFiles)) {
            return 0;
        }
        if (Boolean.TRUE.equals(cleanup.getDryRun())) {
            log.info("[cleanupExpiredReferencePreviewFiles][dryRun fileCount({})]", expiredFiles.size());
            return expiredFiles.size();
        }

        List<FileDO> deletedFiles = deleteFiles(expiredFiles);
        clearReferencePreviewUrls(deletedFiles);
        return deletedFiles.size();
    }

    private Map<Long, List<FileDO>> filterGenerationFilesByTaskId(List<FileDO> files) {
        return files.stream()
                .map(file -> new FileWithTaskId(file, TkFileCleanupPathPolicy.extractGenerationTaskId(file.getPath())))
                .filter(item -> item.getTaskId().isPresent())
                .collect(Collectors.groupingBy(
                        item -> item.getTaskId().getAsLong(),
                        Collectors.mapping(FileWithTaskId::getFile, Collectors.toList())));
    }

    private boolean isCleanableGenerationTask(TkGenerationTaskDO task) {
        if (task == null) {
            return false;
        }
        return TkGenerationStatusEnum.SUCCESS.equals(task.getStatus())
                || TkGenerationStatusEnum.FAILED.equals(task.getStatus());
    }

    private List<FileDO> deleteFiles(List<FileDO> files) {
        List<FileDO> deletedFiles = new ArrayList<>();
        for (FileDO file : files) {
            try {
                fileService.deleteFile(file.getId());
                deletedFiles.add(file);
            } catch (Exception ex) {
                log.warn("[deleteFiles][fileId({}) path({}) TK 过期文件删除失败]",
                        file.getId(), file.getPath(), ex);
            }
        }
        return deletedFiles;
    }

    private void clearGenerationTaskFileUrls(List<FileDO> files) {
        Set<String> paths = files.stream()
                .map(FileDO::getPath)
                .map(TkFileCleanupPathPolicy::extractGenerationTaskPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String path : paths) {
            clearGenerationColumnsByPath(path);
        }
    }

    private void clearGenerationColumnsByPath(String path) {
        for (String column : GENERATION_URL_COLUMNS) {
            clearGenerationColumnByPath(column, path);
        }
    }

    private boolean clearGenerationTaskColumnsByPaths(Long taskId, Set<String> urls) {
        boolean cleared = false;
        for (String url : urls) {
            Optional<String> path = TkFileCleanupPathPolicy.extractGenerationTaskPath(url);
            if (!path.isPresent()) {
                continue;
            }
            for (String column : GENERATION_URL_COLUMNS) {
                cleared = clearGenerationColumnByPath(taskId, column, path.get()) || cleared;
            }
        }
        return cleared;
    }

    private void clearGenerationColumnByPath(String column, String path) {
        clearGenerationColumnByPath(null, column, path);
    }

    private boolean clearGenerationColumnByPath(Long taskId, String column, String path) {
        UpdateWrapper<TkGenerationTaskDO> wrapper = new UpdateWrapper<TkGenerationTaskDO>()
                .like(column, path)
                .set(column, null);
        if (taskId != null) {
            wrapper.eq("id", taskId);
        }
        return generationTaskMapper.update(null, wrapper) > 0;
    }

    private Set<String> collectGenerationUrls(TkGenerationTaskDO task) {
        return Arrays.asList(task.getOutputUrl(), task.getAudioUrl(), task.getSubtitleUrl(),
                        task.getSubtitleTimelineUrl(), task.getSubtitleVisualAnalysisUrl(),
                        task.getSubtitleLayoutUrl(), task.getSubtitleAssUrl()).stream()
                .filter(StrUtil::isNotBlank)
                .filter(url -> TkFileCleanupPathPolicy.extractGenerationTaskPath(url).isPresent())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void deleteManagedOssGenerationUrls(Set<String> urls) {
        if (ossUploadService == null || !ossUploadService.isEnabled()) {
            return;
        }
        for (String url : urls) {
            if (!ossUploadService.isManagedUrl(url)) {
                continue;
            }
            try {
                ossUploadService.deleteByUrl(url);
            } catch (Exception ex) {
                log.warn("[deleteManagedOssGenerationUrls][url({}) TK 过期生成文件 OSS 删除失败]", url, ex);
            }
        }
    }

    private void clearReferencePreviewUrls(List<FileDO> files) {
        Set<String> videoUrls = new LinkedHashSet<>();
        Set<String> coverUrls = new LinkedHashSet<>();
        for (FileDO file : files) {
            if (StrUtil.isBlank(file.getUrl())) {
                continue;
            }
            if (StrUtil.startWith(file.getPath(), "tk/reference-videos/")) {
                videoUrls.add(file.getUrl());
            } else if (StrUtil.startWith(file.getPath(), "tk/reference-covers/")) {
                coverUrls.add(file.getUrl());
            }
        }
        updateReferencePreviewColumn(videoUrls, true);
        updateReferencePreviewColumn(coverUrls, false);
    }

    private void updateReferencePreviewColumn(Set<String> urls, boolean video) {
        for (String url : urls) {
            UpdateWrapper<TkReferenceAnalysisDO> wrapper = new UpdateWrapper<TkReferenceAnalysisDO>();
            if (video) {
                wrapper.eq("resolved_video_url", url)
                        .set("resolved_video_url", null);
            } else {
                wrapper.eq("cover_url", url)
                        .set("cover_url", null);
            }
            referenceAnalysisMapper.update(null, wrapper);
        }
    }

    private int normalizeHours(Integer hours) {
        return hours == null || hours < 1 ? 24 : hours;
    }

    private int normalizeBatchSize(Integer batchSize) {
        return batchSize == null || batchSize < 1 ? 200 : batchSize;
    }

    @Data
    @AllArgsConstructor
    public static class CleanupResult {
        private int generatedFileCount;
        private int referenceFileCount;
    }

    @Data
    @AllArgsConstructor
    private static class FileWithTaskId {
        private FileDO file;
        private OptionalLong taskId;
    }
}
