package cn.iocoder.yudao.module.tk.service.social;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAccountService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TkSocialPublishService {
    private final TkSocialPublishTaskMapper tasks;
    private final TkSocialPublishDetailMapper details;
    private final TkSocialAccountService accounts;
    private final TkSocialMediaService media;
    private final TkDataScopeService scope;
    private final TransactionTemplate transaction;
    private final Validator validator;
    @Value("${tk.social.enabled:false}") private boolean enabled;

    public TkSocialPublishService(TkSocialPublishTaskMapper tasks, TkSocialPublishDetailMapper details,
            TkSocialAccountService accounts, TkSocialMediaService media, TkDataScopeService scope,
            PlatformTransactionManager manager, Validator validator) {
        this.tasks = tasks; this.details = details; this.accounts = accounts; this.media = media; this.scope = scope;
        this.transaction = new TransactionTemplate(manager); this.validator = validator;
    }

    public boolean isEnabled() { return enabled; }
    public void requireEnabled() { if (!enabled) throw new IllegalArgumentException("Meta 发布尚未启用，请先完成后台配置"); }

    public Long create(TkSocialPublishCreateReqVO request) {
        requireEnabled();
        if (!validator.validate(request).isEmpty()) throw new IllegalArgumentException("发布参数不完整或超过限制");
        if (request.getMediaId() != null && request.getGenerationTaskId() != null) throw new IllegalArgumentException("只能选择一种媒体来源");
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!current.hasTenantScope() || companyId == null) throw new IllegalArgumentException("请先选择租户");
        String hash = requestHash(request);
        TkSocialPublishTaskDO existing = findIdempotent(current, request.getIdempotencyKey());
        if (existing != null) return existingId(existing, hash);
        List<Long> ids = request.getAccountIds().stream().distinct().sorted().collect(Collectors.toList());
        List<TkSocialAccountDO> targets = ids.stream().map(accounts::requireReadable).collect(Collectors.toList());
        for (TkSocialAccountDO account : targets) {
            if (!Objects.equals(current.getTenantId(), account.getTenantId()) || !Objects.equals(companyId, account.getCompanyId())) {
                throw new IllegalArgumentException("目标账号不属于当前租户或公司");
            }
            if (!"AUTHORIZED".equals(account.getStatus())) throw new IllegalArgumentException("目标账号需要重新授权");
            if (!Arrays.asList("INSTAGRAM", "FACEBOOK_PAGE").contains(account.getPlatform())) throw new IllegalArgumentException("不支持的发布平台");
        }
        boolean hasMedia = request.getMediaId() != null || request.getGenerationTaskId() != null;
        if (!hasMedia && targets.stream().anyMatch(a -> "INSTAGRAM".equals(a.getPlatform()))) throw new IllegalArgumentException("Instagram 发布需要图片或视频");
        if (!hasMedia && StrUtil.isBlank(request.getFacebookMessage())) throw new IllegalArgumentException("请输入 Facebook 发布文案");
        TkSocialMediaDO source = request.getMediaId() != null ? media.requireReadable(request.getMediaId()) :
                request.getGenerationTaskId() != null ? media.importGenerated(request.getGenerationTaskId()) : null;
        try {
            if (source != null && (!Objects.equals(current.getTenantId(), source.getTenantId()) || !Objects.equals(companyId, source.getCompanyId()))) {
                throw new IllegalArgumentException("媒体不属于当前租户或公司");
            }
            if (source != null) media.readUrl(source);
            for (TkSocialAccountDO target : targets) TkSocialMediaService.validateForPlatform(source,target.getPlatform());
            return transaction.execute(status -> {
                TkSocialPublishTaskDO duplicate = findIdempotent(current, request.getIdempotencyKey());
                if (duplicate != null) return existingId(duplicate, hash);
                if (source!=null) media.lockReady(source.getId(),current.getTenantId());
                TkSocialPublishTaskDO task = new TkSocialPublishTaskDO();
                task.setTenantId(current.getTenantId()); task.setCompanyId(companyId); task.setCreator(current.getUserIdString());
                task.setTitle(request.getTitle()); task.setInstagramCaption(request.getInstagramCaption()); task.setFacebookMessage(request.getFacebookMessage());
                task.setMediaId(source == null ? null : source.getId()); task.setIdempotencyKey(request.getIdempotencyKey()); task.setRequestHash(hash);
                task.setStatus("PENDING"); task.setTargetCount(targets.size()); task.setPendingCount(targets.size()); task.setSuccessCount(0); task.setFailedCount(0);
                tasks.insert(task);
                for (TkSocialAccountDO account : targets) {
                    TkSocialPublishDetailDO detail = new TkSocialPublishDetailDO();
                    detail.setTenantId(current.getTenantId()); detail.setCompanyId(companyId); detail.setCreator(current.getUserIdString());
                    detail.setPublishTaskId(task.getId()); detail.setSocialAccountId(account.getId()); detail.setAccountName(account.getAccountName());
                    detail.setPlatform(account.getPlatform()); detail.setStatus("PENDING"); detail.setRetryCount(0); detail.setPollCount(0);
                    detail.setNextRetryTime(LocalDateTime.now()); details.insert(detail);
                }
                return task.getId();
            });
        } catch (DuplicateKeyException ex) {
            // This lookup runs after the failed transaction has rolled back.
            TkSocialPublishTaskDO duplicate = findIdempotent(current, request.getIdempotencyKey());
            if (duplicate == null) throw ex;
            return existingId(duplicate, hash);
        } finally {
            if (request.getGenerationTaskId()!=null && source!=null) {
                try { media.cleanupUnreferenced(source.getId(),current.getTenantId()); }
                catch (RuntimeException ignored) { /* The periodic sweep retries; a committed task protects its media reference. */ }
            }
        }
    }

    private TkSocialPublishTaskDO findIdempotent(TkUserScope current, String key) {
        return tasks.selectOne(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id", current.getTenantId())
                .eq("creator", current.getUserIdString()).eq("idempotency_key", key));
    }

    static Long existingId(TkSocialPublishTaskDO task, String hash) {
        if (!Objects.equals(task.getRequestHash(), hash)) throw new IllegalArgumentException("相同请求编号不能用于不同发布内容");
        return task.getId();
    }

    static String requestHash(TkSocialPublishCreateReqVO request) {
        Map<String,Object> values = new LinkedHashMap<>();
        values.put("accountIds", request.getAccountIds().stream().distinct().sorted().collect(Collectors.toList()));
        values.put("mediaId", request.getMediaId()); values.put("generationTaskId", request.getGenerationTaskId());
        values.put("title", request.getTitle()); values.put("instagramCaption", request.getInstagramCaption()); values.put("facebookMessage", request.getFacebookMessage());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(values).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format("%02x", b));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public PageResult<TkSocialPublishTaskDO> taskPage(TkSocialPublishPageReqVO request) {
        requireEnabled();
        TkUserScope current = scope.getCurrentScope();
        return tasks.selectPage(request, new LambdaQueryWrapperX<TkSocialPublishTaskDO>()
                .eqIfPresent(TkSocialPublishTaskDO::getTenantId, current.isGlobalPlatformView() ? null : current.getTenantId())
                .eqIfPresent(TkSocialPublishTaskDO::getCreator, current.canReadAllTenantRecords() ? null : current.getUserIdString())
                .eqIfPresent(TkSocialPublishTaskDO::getStatus, request.getStatus())
                .orderByDesc(TkSocialPublishTaskDO::getId));
    }

    public PageResult<TkSocialPublishDetailDO> detailPage(TkSocialPublishPageReqVO request) {
        TkSocialPublishTaskDO task = requireTask(request.getTaskId());
        return details.selectPage(request, new LambdaQueryWrapperX<TkSocialPublishDetailDO>()
                .eq(TkSocialPublishDetailDO::getTenantId, task.getTenantId())
                .eq(TkSocialPublishDetailDO::getPublishTaskId, task.getId())
                .orderByAsc(TkSocialPublishDetailDO::getId));
    }

    public TkSocialPublishTaskDO requireTask(Long id) {
        requireEnabled();
        if (id == null) throw new IllegalArgumentException("请提供发布任务编号");
        TkSocialPublishTaskDO task = tasks.selectById(id);
        if (task == null) throw new IllegalArgumentException("发布任务不存在");
        scope.validateReadable(task.getTenantId(), task.getCompanyId(), task.getCreator());
        return task;
    }

    public void retry(Long id) {
        requireEnabled();
        TkSocialPublishDetailDO detail = details.selectById(id);
        if (detail == null) throw new IllegalArgumentException("发布明细不存在");
        TkSocialPublishTaskDO task = requireTask(detail.getPublishTaskId());
        if (!TkSocialPublishPolicy.canRetry(detail.getStatus())) throw new IllegalArgumentException("仅能重试明确失败的明细；结果待核实时请先核实平台帖子");
        TkSocialAccountDO account = accounts.requireReadable(detail.getSocialAccountId());
        if (!"AUTHORIZED".equals(account.getStatus())) throw new IllegalArgumentException("请先重新授权账号");
        transaction.executeWithoutResult(status -> {
            tasks.lockTask(task.getId(), task.getTenantId());
            TkSocialPublishDetailDO latest=details.selectById(id);
            if (latest==null || !Objects.equals(latest.getPublishTaskId(),task.getId())
                    || !Objects.equals(latest.getTenantId(),task.getTenantId()) || !TkSocialPublishPolicy.canRetry(latest.getStatus())) {
                throw new IllegalArgumentException("该明细已在处理中");
            }
            UpdateWrapper<TkSocialPublishDetailDO> update = new UpdateWrapper<TkSocialPublishDetailDO>()
                    .eq("id", id).eq("tenant_id", task.getTenantId()).in("status", "FAILED", "REAUTH_REQUIRED")
                    .set("status", "PENDING").set("next_retry_time", LocalDateTime.now()).set("poll_count", 0)
                    .set("retry_count",0).set("error_code", null).set("error_message", null).set("lease_token", null).set("lease_until", null);
            if (Arrays.asList("IG_CONTAINER_ERROR","IG_CONTAINER_EXPIRED","FB_VIDEO_PROCESSING_FAILED","FB_VIDEO_ERROR")
                    .contains(latest.getErrorCode())) {
                update.set("external_container_id",null).set("platform_status",null)
                        .set("external_media_id",null).set("external_post_id",null).set("publish_url",null);
            }
            int count = details.update(null, update);
            if (count != 1) throw new IllegalArgumentException("该明细已在处理中");
            aggregate(task.getId(), task.getTenantId());
        });
    }

    public void sync(Long taskId) {
        TkSocialPublishTaskDO task = requireTask(taskId);
        details.update(null, new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("publish_task_id", task.getId()).eq("tenant_id", task.getTenantId()).eq("status", "PENDING")
                .set("next_retry_time", LocalDateTime.now()));
        details.update(null,new UpdateWrapper<TkSocialPublishDetailDO>()
                .eq("publish_task_id",task.getId()).eq("tenant_id",task.getTenantId()).eq("status","UNKNOWN")
                .isNotNull("external_container_id").isNull("lease_token")
                .set("poll_count",0).set("retry_count",0).set("next_retry_time",LocalDateTime.now()));
        // UNKNOWN stays UNKNOWN and only calls the read-only reconciliation path.
    }

    public void aggregate(Long taskId, Long tenantId) {
        transaction.executeWithoutResult(status -> {
            TkSocialPublishTaskDO task = tasks.lockTask(taskId, tenantId);
            if (task == null) return;
            List<TkSocialPublishDetailDO> rows = details.selectList(new QueryWrapper<TkSocialPublishDetailDO>()
                    .eq("publish_task_id", taskId).eq("tenant_id", tenantId));
            if (rows.isEmpty()) return;
            List<String> states = rows.stream().map(TkSocialPublishDetailDO::getStatus).collect(Collectors.toList());
            int success = (int)states.stream().filter("SUCCESS"::equals).count();
            int pending = (int)states.stream().filter(s -> "PENDING".equals(s) || "PROCESSING".equals(s)).count();
            tasks.update(null, new UpdateWrapper<TkSocialPublishTaskDO>().eq("id", taskId).eq("tenant_id", tenantId)
                    .set("status", TkSocialPublishPolicy.aggregate(states)).set("success_count", success)
                    .set("pending_count", pending).set("failed_count", states.size() - success - pending));
        });
    }

    public void recordOutcome(Long taskId,Long tenantId,UpdateWrapper<TkSocialPublishDetailDO> outcome) {
        transaction.executeWithoutResult(status -> {
            if (tasks.lockTask(taskId,tenantId)==null) throw new IllegalStateException("发布任务不存在");
            if (details.update(null,outcome)==1) aggregate(taskId,tenantId);
        });
    }
}
