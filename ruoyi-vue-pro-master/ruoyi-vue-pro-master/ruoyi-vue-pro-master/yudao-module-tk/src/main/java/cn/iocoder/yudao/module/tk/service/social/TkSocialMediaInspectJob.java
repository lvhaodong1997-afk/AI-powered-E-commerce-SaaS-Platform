package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Independent bounded queue. Database leases survive process restarts; stale workers cannot commit. */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "tk.social", name = "enabled", havingValue = "true")
public class TkSocialMediaInspectJob implements AutoCloseable {
    private final TkSocialMediaMapper mapper;
    private final TkSocialMediaService media;
    @Value("${tk.social.probe.max-attempts:3}") private int maxAttempts = 3;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tk-social-video-probe"); thread.setDaemon(true); return thread;
    });
    public TkSocialMediaInspectJob(TkSocialMediaMapper mapper, TkSocialMediaService media) {
        this.mapper = mapper; this.media = media;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    LocalDateTime now = LocalDateTime.now();
                    List<TkSocialMediaDO> due = TenantUtils.executeIgnore(() -> mapper.selectList(
                            new QueryWrapper<TkSocialMediaDO>().eq("media_type", "VIDEO").eq("status", "PROCESSING")
                                    .in("metadata_status", "PENDING", "INSPECTING")
                                    .and(q -> q.isNull("inspection_next_retry").or().le("inspection_next_retry", now))
                                    .and(q -> q.isNull("inspection_lease_until").or().le("inspection_lease_until", now))
                                    .orderByAsc("inspection_next_retry", "id").last("LIMIT 8")));
                    for (TkSocialMediaDO candidate : due) {
                        if (Thread.currentThread().isInterrupted()) break;
                        process(candidate);
                    }
                } catch (RuntimeException ex) { log.warn("Social video inspection scan failed; check database and probe configuration"); }
                finally { busy.set(false); }
            });
        } catch (RejectedExecutionException ex) { busy.set(false); }
    }

    void process(TkSocialMediaDO candidate) {
        if (candidate == null || candidate.getTenantId() == null || candidate.getCompanyId() == null) return;
        SecurityContext previous = SecurityContextHolder.getContext();
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        try {
            // Do not inherit the scheduler/previous request's principal or visit-tenant headers.
            SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes();
            TenantUtils.execute(candidate.getTenantId(), () -> inspectClaimed(candidate));
        } finally {
            SecurityContextHolder.setContext(previous);
            if (request != null) RequestContextHolder.setRequestAttributes(request); else RequestContextHolder.resetRequestAttributes();
        }
    }

    private void inspectClaimed(TkSocialMediaDO candidate) {
        LocalDateTime now = LocalDateTime.now();
        int limit = Math.max(1, Math.min(5, maxAttempts));
        // Last attempt may have crashed: expire it rather than leaving PROCESSING forever.
        if (candidate.getInspectionAttempts() != null && candidate.getInspectionAttempts() >= limit) {
            mapper.update(null, scope(candidate).eq("status", "PROCESSING").in("metadata_status", "PENDING", "INSPECTING")
                    .ge("inspection_attempts", limit)
                    .and(q -> q.isNull("inspection_lease_until").or().le("inspection_lease_until", now))
                    .set("status", "FAILED").set("metadata_status", "FAILED").set("metadata_error", "视频检查重试次数已用尽，请重新上传")
                    .set("inspection_lease_token", null).set("inspection_lease_until", null).set("inspection_next_retry", null));
            return;
        }
        String lease = UUID.randomUUID().toString();
        if (mapper.claimInspection(candidate.getId(), candidate.getTenantId(), candidate.getCompanyId(), lease,
                now, now.plusMinutes(20), limit) != 1) return;
        TkSocialMediaDO current = mapper.selectById(candidate.getId());
        if (current == null || !Objects.equals(current.getTenantId(), candidate.getTenantId())
                || !Objects.equals(current.getCompanyId(), candidate.getCompanyId()) || !lease.equals(current.getInspectionLeaseToken())
                || !"PROCESSING".equals(current.getStatus()) || !"INSPECTING".equals(current.getMetadataStatus())) return;
        UpdateWrapper<TkSocialMediaDO> outcome = scope(current).eq("status", "PROCESSING")
                .eq("metadata_status", "INSPECTING").eq("inspection_lease_token", lease)
                .set("inspection_lease_token", null).set("inspection_lease_until", null);
        // Cleanup below may only touch a key allocated by this attempt, never an earlier committed copy.
        current.setPublishObjectKey(null); current.setPublishEtag(null); current.setPublishVersionId(null);
        try {
            media.inspectOwnedSource(current);
            outcome.set("status", "READY").set("metadata_status", "VERIFIED").set("metadata_source", "FFPROBE")
                    .set("metadata_error", null).set("inspection_next_retry", null).set("inspected_at", current.getInspectedAt())
                    .set("width", current.getWidth()).set("height", current.getHeight()).set("duration_seconds", current.getDurationSeconds())
                    .set("frame_rate", current.getFrameRate()).set("video_codec", current.getVideoCodec()).set("audio_codec", current.getAudioCodec())
                    .set("video_bitrate", current.getVideoBitrate()).set("audio_bitrate", current.getAudioBitrate())
                    .set("audio_sample_rate", current.getAudioSampleRate()).set("normalized", current.getNormalized())
                    .set("file_size", current.getFileSize()).set("publish_object_key", current.getPublishObjectKey())
                    .set("publish_etag", current.getPublishEtag()).set("publish_version_id", current.getPublishVersionId())
                    .set("public_url", current.getPublicUrl());
        } catch (TkSocialVideoInspector.CapacityBusyException ex) {
            // Claim incremented the counter, but no inspection started. Refund exactly this claim
            // under the same token + unexpired lease CAS used for every other outcome.
            outcome.set("status", "PROCESSING").set("metadata_status", "PENDING")
                    .setSql("inspection_attempts=inspection_attempts-1")
                    .set("metadata_error", "视频检查繁忙，正在等待空闲资源")
                    .set("inspection_next_retry", LocalDateTime.now().plusSeconds(30));
        } catch (RuntimeException ex) {
            media.deletePublishCopy(current);
            int attempts = current.getInspectionAttempts() == null ? 1 : current.getInspectionAttempts();
            boolean terminal = ex instanceof IllegalArgumentException || attempts >= limit;
            outcome.set("status", terminal ? "FAILED" : "PROCESSING").set("metadata_status", terminal ? "FAILED" : "PENDING")
                    .set("metadata_error", terminal ? "视频格式不符合发布要求、文件已改变或检查失败，请重新上传" : "视频检查暂不可用，正在重试")
                    .set("inspection_next_retry", terminal ? null : now.plusSeconds(30L * attempts));
            // Deliberately exclude exception text: SDK messages may contain signatures or object URLs.
        }
        try {
            outcome.gt("inspection_lease_until", LocalDateTime.now());
            if (mapper.update(null, outcome) != 1) media.deletePublishCopy(current);
        } catch (RuntimeException ex) {
            // Do not delete a copy on an ambiguous DB commit; an expired lease safely retries with a new key.
            log.warn("Social video inspection outcome save failed, mediaId={}", candidate.getId());
        }
    }
    private UpdateWrapper<TkSocialMediaDO> scope(TkSocialMediaDO value) {
        return new UpdateWrapper<TkSocialMediaDO>().eq("id", value.getId()).eq("tenant_id", value.getTenantId()).eq("company_id", value.getCompanyId());
    }
    @PreDestroy @Override public void close() { executor.shutdownNow(); }
}
