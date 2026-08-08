package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.string.StrUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class TkTiktokPublishServiceImpl implements TkTiktokPublishService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String POST_MODE_MANUAL_REGISTER = "MANUAL_REGISTER";
    private static final String MANUAL_REGISTER_ACCOUNT_NAME = "手动登记";
    private static final int DEFAULT_UPLOAD_CHUNK_SIZE = 64 * 1024 * 1024;
    private static final long STATUS_SYNC_INTERVAL_MINUTES = 2L;

    private final ExecutorService retryExecutorService = Executors.newFixedThreadPool(2);

    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkTiktokPublishTaskMapper publishTaskMapper;
    @Resource
    private TkTiktokPublishDetailMapper publishDetailMapper;
    @Resource
    private TkGenerationTaskMapper generationTaskMapper;
    @Resource
    private TkGenerationTaskService generationTaskService;
    @Resource
    private TkTiktokAccountService accountService;
    @Resource
    private TkTiktokAccountGroupService groupService;
    @Resource
    private TkTiktokApiClient apiClient;
    @Resource
    private TkTiktokTokenCipher tokenCipher;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkBusinessLogService businessLogService;

    @Override
    public TkTiktokOverviewRespVO getOverview() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        TkTiktokOverviewRespVO respVO = new TkTiktokOverviewRespVO();
        respVO.setAuthorizedAccountCount(accountMapper.selectAuthorizedCount(scope));
        respVO.setPendingPublishCount(publishTaskMapper.selectPendingCount(scope));
        respVO.setFailedPublishCount(publishTaskMapper.selectFailedCount(scope));
        respVO.setTokenAbnormalCount(accountMapper.selectTokenAbnormalCount(scope));
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPublishTask(TkTiktokPublishCreateReqVO reqVO) {
        TkGenerationTaskDO generationTask = generationTaskService.getGenerationTask(reqVO.getGenerationTaskId());
        if (!"SUCCESS".equals(generationTask.getStatus()) || StrUtil.isBlank(generationTask.getOutputUrl())) {
            throw exception(TK_TIKTOK_PUBLISH_VIDEO_REQUIRED);
        }
        List<TkTiktokAccountDO> accounts = resolveAccounts(reqVO);
        if (CollUtil.isEmpty(accounts)) {
            throw exception(TK_TIKTOK_PUBLISH_ACCOUNT_REQUIRED);
        }
        Long tenantId = generationTask.getTenantId();
        Long[] result = new Long[1];
        TenantUtils.execute(tenantId, () -> result[0] = createPublishTaskWithinTenant(reqVO, generationTask, accounts));
        return result[0];
    }

    @Override
    public PageResult<TkTiktokPublishTaskRespVO> getTaskPage(TkTiktokPublishTaskPageReqVO reqVO) {
        PageResult<TkTiktokPublishTaskDO> pageResult = publishTaskMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
        return new PageResult<>(BeanUtils.toBean(pageResult.getList(), TkTiktokPublishTaskRespVO.class), pageResult.getTotal());
    }

    @Override
    public PageResult<TkTiktokPublishDetailRespVO> getDetailPage(TkTiktokPublishDetailPageReqVO reqVO) {
        PageResult<TkTiktokPublishDetailDO> pageResult = publishDetailMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
        Set<Long> syncedTaskIds = syncStaleProcessingDetails(pageResult.getList());
        if (CollUtil.isNotEmpty(syncedTaskIds)) {
            pageResult = publishDetailMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
        }
        return new PageResult<>(BeanUtils.toBean(pageResult.getList(), TkTiktokPublishDetailRespVO.class), pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TkTiktokPublishUrlRespVO registerPublishUrl(TkTiktokPublishUrlRegisterReqVO reqVO) {
        TkGenerationTaskDO generationTask = generationTaskService.getGenerationTask(reqVO.getGenerationTaskId());
        if (!STATUS_SUCCESS.equals(generationTask.getStatus()) || StrUtil.isBlank(generationTask.getOutputUrl())) {
            throw exception(TK_TIKTOK_PUBLISH_VIDEO_REQUIRED);
        }
        dataScopeService.validateWritable(generationTask.getTenantId(), generationTask.getCompanyId());
        String publishUrl = normalizePublishUrl(reqVO.getPublishUrl());
        TkTiktokPublishDetailDO detail = resolvePublishUrlTarget(reqVO, generationTask);
        LocalDateTime now = LocalDateTime.now();
        detail.setPublishUrl(publishUrl);
        detail.setPublishUrlRegisteredTime(now);
        detail.setLastSyncTime(now);
        publishDetailMapper.updateById(new TkTiktokPublishDetailDO()
                .setId(detail.getId())
                .setPublishUrl(publishUrl)
                .setPublishUrlRegisteredTime(now)
                .setLastSyncTime(now));
        return toPublishUrlResp(detail);
    }

    @Override
    public Map<Long, TkTiktokPublishUrlRespVO> getLatestPublishUrlMap(Collection<Long> generationTaskIds) {
        List<TkTiktokPublishDetailDO> details = publishDetailMapper.selectRegisteredByGenerationTaskIds(generationTaskIds);
        Map<Long, TkTiktokPublishUrlRespVO> result = new LinkedHashMap<>();
        for (TkTiktokPublishDetailDO detail : details) {
            if (detail.getGenerationTaskId() != null && !result.containsKey(detail.getGenerationTaskId())) {
                result.put(detail.getGenerationTaskId(), toPublishUrlResp(detail));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long detailId) {
        TkTiktokPublishDetailDO detail = validateDetailReadable(detailId);
        if (!STATUS_FAILED.equals(detail.getStatus())) {
            throw exception(TK_TIKTOK_PUBLISH_RETRY_STATUS_INVALID);
        }
        detail.setStatus(STATUS_PENDING);
        detail.setTiktokStatus("RETRY_PENDING");
        detail.setPublishId(null);
        detail.setFailReason(null);
        detail.setRetryCount(detail.getRetryCount() == null ? 1 : detail.getRetryCount() + 1);
        detail.setLastSyncTime(LocalDateTime.now());
        updateDetailClearingFailReason(detail);
        refreshTaskSummary(detail.getPublishTaskId());
        submitRetryAfterCommit(detail.getTenantId(), detail.getId(), detail.getPublishTaskId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncStatus(Long taskId) {
        TkTiktokPublishTaskDO task = validateTaskReadable(taskId);
        List<TkTiktokPublishDetailDO> details = publishDetailMapper.selectListByTaskId(task.getId());
        for (TkTiktokPublishDetailDO detail : details) {
            if (STATUS_PENDING.equals(detail.getStatus())) {
                processDetail(detail);
            } else if (STATUS_PROCESSING.equals(detail.getStatus())) {
                syncProcessingDetail(detail);
            }
        }
        refreshTaskSummary(task.getId());
    }

    private TkTiktokPublishDetailDO resolvePublishUrlTarget(TkTiktokPublishUrlRegisterReqVO reqVO,
                                                            TkGenerationTaskDO generationTask) {
        if (reqVO.getPublishDetailId() != null) {
            TkTiktokPublishDetailDO detail = validateDetailReadable(reqVO.getPublishDetailId());
            if (!generationTask.getId().equals(detail.getGenerationTaskId())) {
                throw new IllegalArgumentException("发布明细不属于当前生成任务");
            }
            return detail;
        }
        TkTiktokPublishDetailDO latest = publishDetailMapper.selectLatestRegisteredTargetByGenerationTaskId(generationTask.getId());
        if (latest != null) {
            dataScopeService.validateReadable(latest.getTenantId(), latest.getCompanyId(), latest.getCreator());
            return latest;
        }
        return createManualPublishDetail(generationTask);
    }

    private TkTiktokPublishDetailDO createManualPublishDetail(TkGenerationTaskDO generationTask) {
        TkTiktokPublishTaskDO task = TkTiktokPublishTaskDO.builder()
                .businessTraceId(generationTask.getBusinessTraceId())
                .companyId(generationTask.getCompanyId())
                .generationTaskId(generationTask.getId())
                .title(StrUtil.blankToDefault(generationTask.getTitle(), "TikTok 视频手动登记"))
                .caption(generationTask.getScriptText())
                .videoUrl(generationTask.getOutputUrl())
                .postMode(POST_MODE_MANUAL_REGISTER)
                .privacyLevel(null)
                .accountCount(1)
                .successCount(1)
                .failedCount(0)
                .pendingCount(0)
                .status(STATUS_SUCCESS)
                .build();
        task.setTenantId(generationTask.getTenantId());
        publishTaskMapper.insert(task);
        TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                .businessTraceId(generationTask.getBusinessTraceId())
                .companyId(generationTask.getCompanyId())
                .publishTaskId(task.getId())
                .generationTaskId(generationTask.getId())
                .accountId(null)
                .accountDisplayName(MANUAL_REGISTER_ACCOUNT_NAME)
                .status(STATUS_SUCCESS)
                .tiktokStatus("MANUAL_REGISTERED")
                .postMode(POST_MODE_MANUAL_REGISTER)
                .retryCount(0)
                .build();
        detail.setTenantId(generationTask.getTenantId());
        publishDetailMapper.insert(detail);
        return detail;
    }

    private String normalizePublishUrl(String publishUrl) {
        String normalized = StrUtil.trim(publishUrl);
        if (!StrUtil.startWithIgnoreCase(normalized, "http://")
                && !StrUtil.startWithIgnoreCase(normalized, "https://")) {
            throw new IllegalArgumentException("发布链接必须是 http 或 https 地址");
        }
        return normalized;
    }

    private TkTiktokPublishUrlRespVO toPublishUrlResp(TkTiktokPublishDetailDO detail) {
        TkTiktokPublishUrlRespVO respVO = new TkTiktokPublishUrlRespVO();
        respVO.setGenerationTaskId(detail.getGenerationTaskId());
        respVO.setPublishTaskId(detail.getPublishTaskId());
        respVO.setPublishDetailId(detail.getId());
        respVO.setAccountId(detail.getAccountId());
        respVO.setAccountDisplayName(detail.getAccountDisplayName());
        respVO.setPublishUrl(detail.getPublishUrl());
        respVO.setPublishUrlRegisteredTime(detail.getPublishUrlRegisteredTime());
        return respVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncStaleProcessingStatus(int limit) {
        List<TkTiktokPublishDetailDO> details = publishDetailMapper.selectStaleProcessingList(getStatusSyncDeadline(), limit);
        return syncProcessingDetails(details).size();
    }

    private Long createPublishTaskWithinTenant(TkTiktokPublishCreateReqVO reqVO, TkGenerationTaskDO generationTask,
                                               List<TkTiktokAccountDO> accounts) {
        String postMode = StrUtil.blankToDefault(reqVO.getPostMode(), apiClient.getDefaultPostMode());
        String businessTraceId = generationTask.getBusinessTraceId();
        TkTiktokPublishTaskDO publishTask = TkTiktokPublishTaskDO.builder()
                .businessTraceId(businessTraceId)
                .companyId(generationTask.getCompanyId())
                .generationTaskId(generationTask.getId())
                .title(StrUtil.blankToDefault(reqVO.getTitle(), StrUtil.blankToDefault(generationTask.getTitle(), "TikTok 视频发布")))
                .caption(reqVO.getCaption())
                .videoUrl(generationTask.getOutputUrl())
                .postMode(postMode)
                .privacyLevel(reqVO.getPrivacyLevel())
                .accountCount(accounts.size())
                .successCount(0)
                .failedCount(0)
                .pendingCount(accounts.size())
                .status(STATUS_PENDING)
                .build();
        publishTask.setTenantId(generationTask.getTenantId());
        publishTaskMapper.insert(publishTask);
        businessLogService.info(businessTraceId, "TIKTOK_PUBLISH", publishTask.getId(), "CREATE", STATUS_PENDING,
                StrUtil.format("创建 TikTok 发布任务：{} 个账号", accounts.size()), publishTask);

        for (TkTiktokAccountDO account : accounts) {
            TkTiktokPublishDetailDO detail = TkTiktokPublishDetailDO.builder()
                    .businessTraceId(businessTraceId)
                    .companyId(generationTask.getCompanyId())
                    .publishTaskId(publishTask.getId())
                    .generationTaskId(generationTask.getId())
                    .accountId(account.getId())
                    .accountDisplayName(StrUtil.blankToDefault(account.getDisplayName(), account.getUsername()))
                    .status(STATUS_PENDING)
                    .tiktokStatus("LOCAL_PENDING")
                    .postMode(postMode)
                    .privacyLevel(StrUtil.blankToDefault(reqVO.getPrivacyLevel(), account.getDefaultPrivacyLevel()))
                    .allowComment(defaultBool(reqVO.getAllowComment(), account.getAllowComment(), true))
                    .allowDuet(defaultBool(reqVO.getAllowDuet(), account.getAllowDuet(), false))
                    .allowStitch(defaultBool(reqVO.getAllowStitch(), account.getAllowStitch(), false))
                    .commercialContent(defaultBool(reqVO.getCommercialContent(), account.getCommercialContent(), false))
                    .brandContent(defaultBool(reqVO.getBrandContent(), account.getBrandContent(), false))
                    .aigcContent(defaultBool(reqVO.getAigcContent(), account.getAigcContent(), true))
                    .retryCount(0)
                    .build();
            detail.setTenantId(generationTask.getTenantId());
            publishDetailMapper.insert(detail);
            processDetail(detail);
        }
        refreshTaskSummary(publishTask.getId());
        return publishTask.getId();
    }

    private List<TkTiktokAccountDO> resolveAccounts(TkTiktokPublishCreateReqVO reqVO) {
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        if (CollUtil.isNotEmpty(reqVO.getAccountIds())) {
            accountIds.addAll(reqVO.getAccountIds());
        }
        if (CollUtil.isNotEmpty(reqVO.getGroupIds())) {
            for (List<Long> ids : groupService.getGroupAccountIdMap(reqVO.getGroupIds()).values()) {
                accountIds.addAll(ids);
            }
        }
        List<TkTiktokAccountDO> accounts = accountService.getReadableAccounts(accountIds);
        Map<Long, TkTiktokAccountDO> accountMap = accounts.stream()
                .collect(Collectors.toMap(TkTiktokAccountDO::getId, account -> account));
        return accountIds.stream().map(accountMap::get).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private void processDetail(TkTiktokPublishDetailDO detail) {
        if (!claimPendingDetail(detail)) {
            return;
        }
        TkTiktokAccountDO account = accountMapper.selectById(detail.getAccountId());
        TkGenerationTaskDO generationTask = generationTaskMapper.selectById(detail.getGenerationTaskId());
        if (account == null) {
            failDetail(detail, "TikTok 账号不存在");
            return;
        }
        if (!"AUTHORIZED".equals(account.getAuthStatus()) || !"VALID".equals(account.getTokenStatus())) {
            failDetail(detail, "TikTok 账号未授权或 Token 已失效");
            return;
        }
        if (generationTask == null || StrUtil.isBlank(generationTask.getOutputUrl())) {
            failDetail(detail, "生成视频不存在或没有输出文件");
            return;
        }
        if (!apiClient.isConfigured()) {
            failDetail(detail, "请先配置 TIKTOK/client-key 与 client-secret 后再发布");
            return;
        }
        try {
            String accessToken = tokenCipher.decrypt(account.getAccessTokenCipher());
            TkTiktokApiClient.CreatorInfo creatorInfo = apiClient.queryCreatorInfo(accessToken);
            if (!creatorInfo.isSuccess()) {
                failDetail(detail, creatorInfo.getFailReason());
                return;
            }
            UploadSource uploadSource = resolveUploadSource(generationTask.getOutputUrl());
            TkTiktokApiClient.PublishResult result = apiClient.initVideoPost(accessToken, detail.getPostMode(),
                    buildPublishPayload(detail, generationTask, uploadSource, creatorInfo));
            if (!result.isSuccess()) {
                failDetail(detail, result.getFailReason());
                return;
            }
            if (uploadSource.getVideoBytes() != null) {
                if (StrUtil.isBlank(result.getUploadUrl())) {
                    failDetail(detail, "TikTok 未返回文件上传地址");
                    return;
                }
                apiClient.uploadVideoChunks(result.getUploadUrl(), uploadSource.getVideoBytes(),
                        uploadSource.getChunkSize(), uploadSource.getTotalChunkCount());
            }
            detail.setPublishId(result.getPublishId());
            detail.setTiktokStatus(StrUtil.isBlank(result.getPublishId()) ? "SEND_TO_USER_INBOX" : "PROCESSING");
            detail.setStatus(STATUS_PROCESSING);
            detail.setFailReason(null);
            detail.setLastSyncTime(LocalDateTime.now());
            updateDetailClearingFailReason(detail);
            account.setLastPublishTime(LocalDateTime.now());
            accountMapper.updateById(account);
            businessLogService.info(detail.getBusinessTraceId(), "TIKTOK_PUBLISH", detail.getPublishTaskId(), "DETAIL_PROCESSING", STATUS_PROCESSING,
                    StrUtil.format("账号 {} 发布任务已提交", detail.getAccountDisplayName()), detail);
        } catch (Exception ex) {
            failDetail(detail, "TikTok 发布调用失败：" + ex.getMessage());
        }
    }

    private boolean claimPendingDetail(TkTiktokPublishDetailDO detail) {
        if (!STATUS_PENDING.equals(detail.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = publishDetailMapper.update(null, Wrappers.lambdaUpdate(TkTiktokPublishDetailDO.class)
                .eq(TkTiktokPublishDetailDO::getId, detail.getId())
                .eq(TkTiktokPublishDetailDO::getStatus, STATUS_PENDING)
                .set(TkTiktokPublishDetailDO::getStatus, STATUS_PROCESSING)
                .set(TkTiktokPublishDetailDO::getTiktokStatus, "LOCAL_PROCESSING")
                .set(TkTiktokPublishDetailDO::getLastSyncTime, now)
                .set(TkTiktokPublishDetailDO::getFailReason, null));
        if (updated <= 0) {
            return false;
        }
        detail.setStatus(STATUS_PROCESSING);
        detail.setTiktokStatus("LOCAL_PROCESSING");
        detail.setLastSyncTime(now);
        detail.setFailReason(null);
        return true;
    }

    private void syncProcessingDetail(TkTiktokPublishDetailDO detail) {
        if (StrUtil.isBlank(detail.getPublishId())) {
            return;
        }
        TkTiktokAccountDO account = accountMapper.selectById(detail.getAccountId());
        if (account == null || StrUtil.isBlank(account.getAccessTokenCipher())) {
            failDetail(detail, "TikTok 账号不存在或缺少 Access Token");
            return;
        }
        try {
            String accessToken = tokenCipher.decrypt(account.getAccessTokenCipher());
            TkTiktokApiClient.PostStatusResult result = apiClient.fetchPostStatus(accessToken, detail.getPublishId());
            if (!result.isSuccess()) {
                failDetail(detail, result.getFailReason());
                return;
            }
            String tiktokStatus = StrUtil.blankToDefault(result.getStatus(), STATUS_PROCESSING);
            detail.setTiktokStatus(tiktokStatus);
            detail.setLastSyncTime(LocalDateTime.now());
            if (isTikTokPublishSuccess(tiktokStatus)) {
                detail.setStatus(STATUS_SUCCESS);
                detail.setFailReason(null);
                updateDetailClearingFailReason(detail);
            } else if (isTikTokPublishFailed(tiktokStatus)) {
                failDetail(detail, StrUtil.blankToDefault(result.getFailReason(), "TikTok 发布失败：" + tiktokStatus));
            } else {
                publishDetailMapper.updateById(detail);
            }
        } catch (Exception ex) {
            failDetail(detail, "TikTok 状态同步失败：" + ex.getMessage());
        }
    }

    private Set<Long> syncStaleProcessingDetails(List<TkTiktokPublishDetailDO> details) {
        if (CollUtil.isEmpty(details)) {
            return Collections.emptySet();
        }
        LocalDateTime deadline = getStatusSyncDeadline();
        List<TkTiktokPublishDetailDO> staleDetails = details.stream()
                .filter(detail -> STATUS_PROCESSING.equals(detail.getStatus()))
                .filter(detail -> StrUtil.isNotBlank(detail.getPublishId()))
                .filter(detail -> detail.getLastSyncTime() == null || !detail.getLastSyncTime().isAfter(deadline))
                .collect(Collectors.toList());
        return syncProcessingDetails(staleDetails);
    }

    private Set<Long> syncProcessingDetails(List<TkTiktokPublishDetailDO> details) {
        if (CollUtil.isEmpty(details)) {
            return Collections.emptySet();
        }
        Set<Long> taskIds = new HashSet<>();
        for (TkTiktokPublishDetailDO detail : details) {
            syncProcessingDetail(detail);
            if (detail.getPublishTaskId() != null) {
                taskIds.add(detail.getPublishTaskId());
            }
        }
        taskIds.forEach(this::refreshTaskSummary);
        return taskIds;
    }

    private void submitRetryAfterCommit(Long tenantId, Long detailId, Long publishTaskId) {
        Runnable submitTask = () -> retryExecutorService.submit(
                () -> TenantUtils.execute(tenantId, () -> processRetryDetail(detailId, publishTaskId)));
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitTask.run();
                }
            });
            return;
        }
        submitTask.run();
    }

    private void processRetryDetail(Long detailId, Long publishTaskId) {
        try {
            TkTiktokPublishDetailDO detail = publishDetailMapper.selectById(detailId);
            if (detail == null || !STATUS_PENDING.equals(detail.getStatus())) {
                return;
            }
            processDetail(detail);
        } catch (Exception ex) {
            log.error("[processRetryDetail][detailId({}) TikTok 发布重试失败]", detailId, ex);
            TkTiktokPublishDetailDO detail = publishDetailMapper.selectById(detailId);
            if (detail != null) {
                failDetail(detail, "TikTok 发布重试失败：" + ex.getMessage());
            }
        } finally {
            refreshTaskSummary(publishTaskId);
        }
    }

    private LocalDateTime getStatusSyncDeadline() {
        return LocalDateTime.now().minusMinutes(STATUS_SYNC_INTERVAL_MINUTES);
    }

    private boolean isTikTokPublishSuccess(String tiktokStatus) {
        return StrUtil.equalsAnyIgnoreCase(tiktokStatus, "PUBLISH_COMPLETE", "SUCCESS", "SEND_TO_USER_INBOX");
    }

    private boolean isTikTokPublishFailed(String tiktokStatus) {
        return StrUtil.containsIgnoreCase(tiktokStatus, "FAIL") || StrUtil.containsIgnoreCase(tiktokStatus, "REJECT");
    }

    private void updateDetailClearingFailReason(TkTiktokPublishDetailDO detail) {
        publishDetailMapper.update(null, Wrappers.lambdaUpdate(TkTiktokPublishDetailDO.class)
                .eq(TkTiktokPublishDetailDO::getId, detail.getId())
                .set(TkTiktokPublishDetailDO::getStatus, detail.getStatus())
                .set(TkTiktokPublishDetailDO::getTiktokStatus, detail.getTiktokStatus())
                .set(TkTiktokPublishDetailDO::getPublishId, detail.getPublishId())
                .set(TkTiktokPublishDetailDO::getRetryCount, detail.getRetryCount())
                .set(TkTiktokPublishDetailDO::getLastSyncTime, detail.getLastSyncTime())
                .set(TkTiktokPublishDetailDO::getFailReason, null));
    }

    private Map<String, Object> buildPublishPayload(TkTiktokPublishDetailDO detail, TkGenerationTaskDO task,
                                                    UploadSource uploadSource,
                                                    TkTiktokApiClient.CreatorInfo creatorInfo) {
        Map<String, Object> postInfo = new LinkedHashMap<>();
        postInfo.put("title", StrUtil.blankToDefault(task.getTitle(), "TikTok 视频发布"));
        postInfo.put("privacy_level", resolvePrivacyLevel(detail.getPrivacyLevel(), creatorInfo));
        postInfo.put("disable_comment", creatorInfo.isCommentDisabled() || !Boolean.TRUE.equals(detail.getAllowComment()));
        postInfo.put("disable_duet", creatorInfo.isDuetDisabled() || !Boolean.TRUE.equals(detail.getAllowDuet()));
        postInfo.put("disable_stitch", creatorInfo.isStitchDisabled() || !Boolean.TRUE.equals(detail.getAllowStitch()));
        postInfo.put("video_cover_timestamp_ms", 1000);
        postInfo.put("brand_content_toggle", Boolean.TRUE.equals(detail.getBrandContent()));
        postInfo.put("brand_organic_toggle", Boolean.TRUE.equals(detail.getCommercialContent()));
        postInfo.put("is_aigc", Boolean.TRUE.equals(detail.getAigcContent()));

        Map<String, Object> sourceInfo = new LinkedHashMap<>();
        sourceInfo.put("source", uploadSource.isPullFromUrl() ? "PULL_FROM_URL" : "FILE_UPLOAD");
        if (uploadSource.isPullFromUrl()) {
            sourceInfo.put("video_url", task.getOutputUrl());
        } else {
            sourceInfo.put("video_size", uploadSource.getVideoBytes().length);
            sourceInfo.put("chunk_size", uploadSource.getChunkSize());
            sourceInfo.put("total_chunk_count", uploadSource.getTotalChunkCount());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("post_info", postInfo);
        payload.put("source_info", sourceInfo);
        return payload;
    }

    private String resolvePrivacyLevel(String requestedPrivacyLevel, TkTiktokApiClient.CreatorInfo creatorInfo) {
        List<String> options = creatorInfo.getPrivacyLevelOptions();
        String requested = StrUtil.blankToDefault(requestedPrivacyLevel, "SELF_ONLY");
        if (CollUtil.isEmpty(options)) {
            return requested;
        }
        if (options.stream().anyMatch(option -> StrUtil.equalsIgnoreCase(option, requested))) {
            return requested;
        }
        Optional<String> selfOnly = options.stream()
                .filter(option -> StrUtil.equalsIgnoreCase(option, "SELF_ONLY"))
                .findFirst();
        return selfOnly.orElse(options.get(0));
    }

    private void failDetail(TkTiktokPublishDetailDO detail, String reason) {
        detail.setStatus(STATUS_FAILED);
        detail.setTiktokStatus("FAILED");
        detail.setFailReason(StrUtils.maxLength(reason, 512));
        detail.setLastSyncTime(LocalDateTime.now());
        publishDetailMapper.updateById(detail);
        businessLogService.error(detail.getBusinessTraceId(), "TIKTOK_PUBLISH", detail.getPublishTaskId(), "DETAIL_FAILED", STATUS_FAILED,
                StrUtil.format("账号 {} 发布失败：{}", detail.getAccountDisplayName(), reason), detail);
    }

    private void refreshTaskSummary(Long taskId) {
        TkTiktokPublishTaskDO task = publishTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        List<TkTiktokPublishDetailDO> details = publishDetailMapper.selectListByTaskId(taskId);
        int success = (int) details.stream().filter(detail -> STATUS_SUCCESS.equals(detail.getStatus())).count();
        int failed = (int) details.stream().filter(detail -> STATUS_FAILED.equals(detail.getStatus())).count();
        int pending = details.size() - success - failed;
        task.setSuccessCount(success);
        task.setFailedCount(failed);
        task.setPendingCount(pending);
        if (failed == 0 && pending == 0) {
            task.setStatus(STATUS_SUCCESS);
        } else if (failed > 0 && pending == 0 && success > 0) {
            task.setStatus(STATUS_PARTIAL_SUCCESS);
        } else if (failed > 0 && pending == 0) {
            task.setStatus(STATUS_FAILED);
        } else {
            task.setStatus(STATUS_PROCESSING);
        }
        publishTaskMapper.updateById(task);
        if (STATUS_FAILED.equals(task.getStatus()) || STATUS_PARTIAL_SUCCESS.equals(task.getStatus())) {
            businessLogService.warn(task.getBusinessTraceId(), "TIKTOK_PUBLISH", task.getId(), "SUMMARY", task.getStatus(),
                    StrUtil.format("发布任务异常：成功 {}，失败 {}，待处理 {}", success, failed, pending), task);
        } else if (STATUS_SUCCESS.equals(task.getStatus())) {
            businessLogService.info(task.getBusinessTraceId(), "TIKTOK_PUBLISH", task.getId(), "SUMMARY", task.getStatus(),
                    StrUtil.format("发布任务完成：成功 {}", success), task);
        }
    }

    private TkTiktokPublishTaskDO validateTaskReadable(Long id) {
        TkTiktokPublishTaskDO task = publishTaskMapper.selectById(id);
        if (task == null) {
            throw exception(TK_TIKTOK_PUBLISH_TASK_NOT_EXISTS);
        }
        dataScopeService.validateReadable(task.getTenantId(), task.getCompanyId(), task.getCreator());
        return task;
    }

    private TkTiktokPublishDetailDO validateDetailReadable(Long id) {
        TkTiktokPublishDetailDO detail = publishDetailMapper.selectById(id);
        if (detail == null) {
            throw exception(TK_TIKTOK_PUBLISH_DETAIL_NOT_EXISTS);
        }
        dataScopeService.validateReadable(detail.getTenantId(), detail.getCompanyId(), detail.getCreator());
        return detail;
    }

    private boolean defaultBool(Boolean requested, Boolean accountDefault, boolean fallback) {
        if (requested != null) {
            return requested;
        }
        return accountDefault == null ? fallback : accountDefault;
    }

    @PreDestroy
    public void destroy() {
        retryExecutorService.shutdown();
    }

    private UploadSource resolveUploadSource(String outputUrl) {
        if (isVerifiedPullUrl(outputUrl)) {
            return UploadSource.pullFromUrl();
        }
        if (!StrUtil.startWithIgnoreCase(outputUrl, "http://") && !StrUtil.startWithIgnoreCase(outputUrl, "https://")) {
            throw new IllegalStateException("输出视频不是公网 HTTPS，且服务端无法读取本地相对地址：" + outputUrl);
        }
        try (HttpResponse response = HttpRequest.get(outputUrl).timeout(120_000).execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException("服务端下载输出视频失败，HTTP " + response.getStatus());
            }
            return UploadSource.fileUpload(response.bodyBytes());
        }
    }

    private boolean isVerifiedPullUrl(String outputUrl) {
        String verifiedPullDomain = StrUtil.trim(apiClient.getVerifiedPullDomain());
        if (!StrUtil.startWithIgnoreCase(outputUrl, "https://") || StrUtil.isBlank(verifiedPullDomain)) {
            return false;
        }
        try {
            URI outputUri = URI.create(outputUrl);
            String outputHost = StrUtil.emptyToDefault(outputUri.getHost(), "").toLowerCase(Locale.ROOT);
            if (StrUtil.startWithIgnoreCase(verifiedPullDomain, "https://")) {
                URI verifiedUri = URI.create(verifiedPullDomain);
                String verifiedHost = StrUtil.emptyToDefault(verifiedUri.getHost(), "").toLowerCase(Locale.ROOT);
                String verifiedPath = StrUtil.emptyToDefault(verifiedUri.getPath(), "");
                return StrUtil.isNotBlank(verifiedHost)
                        && outputHost.equals(verifiedHost)
                        && StrUtil.startWith(outputUri.getPath(), verifiedPath);
            }
            String normalizedDomain = verifiedPullDomain.toLowerCase(Locale.ROOT);
            return outputHost.equals(normalizedDomain) || outputHost.endsWith("." + normalizedDomain);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static class UploadSource {

        private final boolean pullFromUrl;
        private final byte[] videoBytes;
        private final int chunkSize;
        private final int totalChunkCount;

        private UploadSource(boolean pullFromUrl, byte[] videoBytes, int chunkSize, int totalChunkCount) {
            this.pullFromUrl = pullFromUrl;
            this.videoBytes = videoBytes;
            this.chunkSize = chunkSize;
            this.totalChunkCount = totalChunkCount;
        }

        private static UploadSource pullFromUrl() {
            return new UploadSource(true, null, 0, 0);
        }

        private static UploadSource fileUpload(byte[] videoBytes) {
            int chunkCount = videoBytes.length <= DEFAULT_UPLOAD_CHUNK_SIZE
                    ? 1
                    : (int) Math.ceil(videoBytes.length * 1.0 / DEFAULT_UPLOAD_CHUNK_SIZE);
            int chunkSize = chunkCount == 1 ? videoBytes.length : DEFAULT_UPLOAD_CHUNK_SIZE;
            return new UploadSource(false, videoBytes, chunkSize, Math.max(1, chunkCount));
        }

        private boolean isPullFromUrl() {
            return pullFromUrl;
        }

        private byte[] getVideoBytes() {
            return videoBytes;
        }

        private int getChunkSize() {
            return chunkSize;
        }

        private int getTotalChunkCount() {
            return totalChunkCount;
        }

    }

}
