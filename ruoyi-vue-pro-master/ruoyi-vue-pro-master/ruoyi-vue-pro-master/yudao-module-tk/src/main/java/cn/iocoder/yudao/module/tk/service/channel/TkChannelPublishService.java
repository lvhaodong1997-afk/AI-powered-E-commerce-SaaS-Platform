package cn.iocoder.yudao.module.tk.service.channel;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.social.TkSocialMediaService;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAuthService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.validation.constraints.*;

@Service
@RequiredArgsConstructor
public class TkChannelPublishService {
    private static final long X_MAX_VIDEO_BYTES = 512L * 1024 * 1024;
    private final TkChannelAccountService accounts;
    private final TkSocialMediaService media;
    private final TkSocialPublishTaskMapper tasks;
    private final TkSocialPublishDetailMapper details;
    private final TkDataScopeService scope;
    private final PlatformTransactionManager transactions;

    @Data
    public static class Create {
        @NotNull private Long accountId;
        @Positive private Long generationTaskId;
        @Positive @JsonInclude(JsonInclude.Include.NON_NULL) private Long mediaId;
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,80}") private String idempotencyKey;
        @Size(max = 100) private String title;
        @Size(max = 5000) private String text;
        private String privacy;
        private Boolean forKids;
        private Boolean synthetic;
    }

    public String create(String channel, Create request) {
        if (!accounts.ready(channel)) throw new IllegalArgumentException("Channel publishing is not configured");
        validate(channel, request);
        TkSocialAccountDO account = accounts.owned(channel, request.getAccountId());
        if (!"AUTHORIZED".equals(account.getStatus())) throw new IllegalArgumentException("Reconnect the publishing account");
        Long tenant = account.getTenantId(), company = account.getCompanyId();
        String creator = account.getCreator();
        String hash = TkSocialAuthService.hash(channel + ":" + TkChannelHttp.JSON.valueToTree(request).toString());
        TkSocialPublishTaskDO existing = find(tenant, creator, request.getIdempotencyKey());
        if (existing != null) return existingId(existing, hash);
        boolean generated = request.getGenerationTaskId() != null;
        TkSocialMediaDO source = generated ? media.importGenerated(request.getGenerationTaskId())
                : media.getOwnedVideoUpload(request.getMediaId());
        try {
            verifyVideo(channel, source);
            if (!Objects.equals(tenant, source.getTenantId()) || !Objects.equals(company, source.getCompanyId())
                    || (!generated && !Objects.equals(creator, source.getCreator())))
                throw new IllegalArgumentException("Video is outside the publishing account scope");
            return new TransactionTemplate(transactions).execute(status -> {
                TkSocialPublishTaskDO duplicate = find(tenant, creator, request.getIdempotencyKey());
                if (duplicate != null) return existingId(duplicate, hash);
                media.lockReady(source.getId(), tenant);
                TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
                task.setTenantId(tenant); task.setCompanyId(company); task.setCreator(creator);
                task.setChannel(channel); task.setMediaId(source.getId()); task.setChannelText(request.getText());
                task.setTitle(TkChannelProperties.X.equals(channel) ? source.getFileName() : request.getTitle());
                task.setChannelPrivacy(request.getPrivacy()); task.setChannelForKids(request.getForKids()); task.setChannelSynthetic(request.getSynthetic());
                task.setIdempotencyKey(request.getIdempotencyKey()); task.setRequestHash(hash);
                task.setTargetCount(1); task.setPendingCount(1); task.setSuccessCount(0); task.setFailedCount(0); task.setStatus("PENDING");
                tasks.insert(task);
                TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();
                detail.setTenantId(tenant); detail.setCompanyId(company); detail.setCreator(creator);
                detail.setPublishTaskId(task.getId()); detail.setSocialAccountId(account.getId()); detail.setPlatform(channel);
                detail.setAccountName(account.getAccountName()); detail.setStatus("PENDING"); detail.setRetryCount(0); detail.setPollCount(0);
                detail.setNextRetryTime(LocalDateTime.now()); details.insert(detail);
                return String.valueOf(task.getId());
            });
        } catch (DuplicateKeyException ex) {
            TkSocialPublishTaskDO duplicate = find(tenant, creator, request.getIdempotencyKey());
            if (duplicate == null) throw ex;
            return existingId(duplicate, hash);
        } finally {
            if (generated) {
                try { media.cleanupUnreferenced(source.getId(), tenant); }
                catch (RuntimeException ignored) { /* Existing storage sweep retries. */ }
            }
        }
    }

    public PageResult<Map<String, Object>> page(String channel, PageParam page) {
        TkUserScope user = scope.getCurrentScope();
        if (!user.hasTenantScope() || user.getUserId() == null) return new PageResult<>(Collections.emptyList(), 0L);
        PageResult<TkSocialPublishDetailDO> result = details.selectPage(page, new QueryWrapper<TkSocialPublishDetailDO>()
                .eq("tenant_id", user.getTenantId()).eq("company_id", scope.getWritableCompanyId(null))
                .eq("creator", user.getUserIdString()).eq("platform", channel).orderByDesc("id"));
        if (result.getList().isEmpty()) return new PageResult<>(Collections.emptyList(), result.getTotal());
        List<Long> ids = result.getList().stream().map(TkSocialPublishDetailDO::getPublishTaskId).collect(Collectors.toList());
        Map<Long,TkSocialPublishTaskDO> matching = tasks.selectList(new QueryWrapper<TkSocialPublishTaskDO>()
                        .eq("tenant_id", user.getTenantId()).eq("company_id", scope.getWritableCompanyId(null))
                        .eq("creator", user.getUserIdString()).eq("channel", channel).in("id", ids))
                .stream().collect(Collectors.toMap(TkSocialPublishTaskDO::getId, t -> t));
        List<Map<String, Object>> safe = new ArrayList<>();
        for (TkSocialPublishDetailDO item : result.getList()) {
            TkSocialPublishTaskDO task = matching.get(item.getPublishTaskId());
            if (task == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(item.getId())); row.put("title", task.getTitle()); row.put("accountName", item.getAccountName());
            row.put("status", item.getStatus()); row.put("stage", item.getPlatformStatus()); row.put("url", item.getPublishUrl());
            row.put("errorCode", item.getErrorCode()); row.put("createTime", item.getCreateTime());
            safe.add(row);
        }
        return new PageResult<>(safe, result.getTotal());
    }

    public void resume(String channel, Long id) {
        if (!accounts.ready(channel)) throw new IllegalArgumentException("Channel publishing is not configured");
        TkSocialPublishDetailDO item = details.selectById(id);
        if (item == null || !channel.equals(item.getPlatform())) throw new IllegalArgumentException("Publish record not found");
        TkSocialAccountDO account = accounts.owned(channel, item.getSocialAccountId());
        if (!Objects.equals(account.getTenantId(), item.getTenantId()) || !Objects.equals(account.getCompanyId(), item.getCompanyId())
                || !Objects.equals(account.getCreator(), item.getCreator()) || !"AUTHORIZED".equals(account.getStatus()))
            throw new IllegalArgumentException("Reconnect the publishing account");
        if (details.update(null, new UpdateWrapper<TkSocialPublishDetailDO>().eq("id", id).eq("tenant_id", item.getTenantId())
                .eq("status", "REAUTH_REQUIRED").set("status", "PENDING").set("retry_count", 0)
                .set("error_code", null).set("error_message", null).set("next_retry_time", LocalDateTime.now())) != 1)
            throw new IllegalArgumentException("This record cannot be resumed");
    }

    static void validate(String channel, Create request) {
        if ((request.getGenerationTaskId() == null) == (request.getMediaId() == null))
            throw new IllegalArgumentException("请选择一个视频来源");
        if (TkChannelProperties.X.equals(channel)) {
            if (request.getText() != null && request.getText().length() > 280)
                throw new IllegalArgumentException("X post text exceeds 280 characters");
        } else if (request.getTitle() == null || request.getTitle().trim().isEmpty() || request.getForKids() == null
                || request.getSynthetic() == null || !Arrays.asList("private", "unlisted", "public").contains(request.getPrivacy())) {
            throw new IllegalArgumentException("YouTube metadata is incomplete");
        }
    }

    private static void verifyVideo(String channel, TkSocialMediaDO source) {
        if (!"VIDEO".equals(source.getMediaType()) || !"READY".equals(source.getStatus())
                || !"VERIFIED".equals(source.getMetadataStatus()) || source.getFileSize() == null
                || source.getFileSize() <= 0 || source.getPublishObjectKey() == null) throw new IllegalArgumentException("Verified MP4 required");
        if (TkChannelProperties.X.equals(channel) && (source.getFileSize() > X_MAX_VIDEO_BYTES
                || source.getDurationSeconds() == null || source.getDurationSeconds() < 0.5 || source.getDurationSeconds() > 140))
            throw new IllegalArgumentException("X video must be 0.5 to 140 seconds and at most 512 MB");
    }

    public static long maxUploadBytes(String channel) {
        return TkChannelProperties.X.equals(channel) ? X_MAX_VIDEO_BYTES : TkSocialMediaService.MAX_VIDEO_BYTES;
    }
    private TkSocialPublishTaskDO find(Long tenant, String creator, String key) {
        return tasks.selectOne(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id", tenant).eq("creator", creator).eq("idempotency_key", key));
    }
    private static String existingId(TkSocialPublishTaskDO task, String hash) {
        if (!hash.equals(task.getRequestHash())) throw new IllegalArgumentException("Request key is already in use");
        return String.valueOf(task.getId());
    }
}
