package cn.iocoder.yudao.module.tk.service.cleanup;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService;
import cn.iocoder.yudao.module.tk.service.tiktok.TkTiktokScheduledMediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * Retries local media deletion after a scheduled publish reaches an authoritative terminal state.
 * Task and media rows are retained for status, callback, and metrics queries.
 */
@Service
@Slf4j
public class TkScheduledMediaCleanupService {

    private static final int DEFAULT_BATCH_SIZE = 100;

    @Resource
    private TkTiktokPublishTaskMapper appTaskMapper;
    @Resource
    private TkOpenTiktokMediaMapper openMediaMapper;
    @Resource
    private TkTiktokScheduledMediaService appMediaService;
    @Resource
    private TkOpenTiktokMediaService openMediaService;

    public TkScheduledMediaCleanupService() {
    }

    public TkScheduledMediaCleanupService(TkTiktokPublishTaskMapper appTaskMapper,
                                          TkOpenTiktokMediaMapper openMediaMapper,
                                          TkTiktokScheduledMediaService appMediaService,
                                          TkOpenTiktokMediaService openMediaService) {
        this.appTaskMapper = appTaskMapper;
        this.openMediaMapper = openMediaMapper;
        this.appMediaService = appMediaService;
        this.openMediaService = openMediaService;
    }

    public int cleanupTerminalScheduledMedia(Integer batchSize) {
        int limit = normalizeBatchSize(batchSize);
        int cleaned = cleanupAppMedia(limit);
        return cleaned + cleanupOpenApiMedia(limit);
    }

    private int cleanupAppMedia(int limit) {
        List<TkTiktokPublishTaskDO> candidates = appTaskMapper == null
                ? Collections.emptyList() : appTaskMapper.selectScheduledMediaCleanupCandidates(limit);
        int cleaned = 0;
        for (TkTiktokPublishTaskDO task : candidates) {
            if (task == null || task.getId() == null || StrUtil.isBlank(task.getScheduledLocalPath())
                    || appTaskMapper.claimScheduledMediaCleanup(task.getId()) != 1) {
                continue;
            }
            try {
                appMediaService.cleanup(task.getScheduledLocalPath());
                appTaskMapper.updateScheduledMediaCleanup(task.getId(), "CLEANED", null);
                cleaned++;
            } catch (Exception ex) {
                String reason = errorMessage(ex);
                appTaskMapper.updateScheduledMediaCleanup(task.getId(), "CLEANUP_FAILED", reason);
                log.warn("[cleanupTerminalScheduledMedia][appTaskId({}) cleanup failed] {}", task.getId(), reason);
            }
        }
        return cleaned;
    }

    private int cleanupOpenApiMedia(int limit) {
        List<TkOpenTiktokMediaDO> candidates = openMediaMapper == null
                ? Collections.emptyList() : openMediaMapper.selectScheduledMediaCleanupCandidates(limit);
        int cleaned = 0;
        for (TkOpenTiktokMediaDO media : candidates) {
            if (media == null || media.getId() == null || openMediaMapper.claimScheduledMediaCleanup(media.getId()) != 1) {
                continue;
            }
            try {
                openMediaService.cleanupScheduledPublishMedia(media);
                openMediaMapper.markScheduledMediaCleanup(media.getId(), "CLEANED", null);
                cleaned++;
            } catch (Exception ex) {
                String reason = errorMessage(ex);
                openMediaMapper.markScheduledMediaCleanup(media.getId(), "CLEANUP_FAILED", reason);
                log.warn("[cleanupTerminalScheduledMedia][openMediaId({}) cleanup failed] {}", media.getId(), reason);
            }
        }
        return cleaned;
    }

    private String errorMessage(Exception ex) {
        return StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "scheduled media cleanup failed"), 512);
    }

    private int normalizeBatchSize(Integer batchSize) {
        return batchSize == null || batchSize < 1 ? DEFAULT_BATCH_SIZE : Math.min(batchSize, 500);
    }
}
