package cn.iocoder.yudao.module.tk.service.tiktokone;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokOnePublishDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokOnePublishMapper;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.invalidParamException;

@Service
@RequiredArgsConstructor
public class TkTiktokOnePublishService {

    private final TkTiktokOnePublishMapper mapper;
    private final TkGenerationTaskService generationTasks;
    private final TkDataScopeService dataScope;

    public Long create(Create request) {
        TkUserScope scope = tenantScope();
        TkGenerationTaskDO source = generationTasks.getGenerationTask(request.getGenerationTaskId());
        if (!scope.getTenantId().equals(source.getTenantId()) || !"SUCCESS".equals(source.getStatus())
                || source.getOutputUrl() == null || source.getOutputUrl().trim().isEmpty()) {
            throw invalidParamException("请选择当前租户已生成的成片");
        }
        dataScope.validateWritable(source.getTenantId(), source.getCompanyId());
        String anchor = request.getAnchorName() == null ? "" : request.getAnchorName().trim();
        if (request.isAnchorRequired() && (anchor.isEmpty() || !request.isAnchorApproved())) {
            throw invalidParamException("请确认项目已配置审核通过的网页锚点并填写锚点名称");
        }
        TkTiktokOnePublishDO task = new TkTiktokOnePublishDO();
        task.setTenantId(scope.getTenantId());
        task.setCompanyId(source.getCompanyId());
        task.setGenerationTaskId(source.getId());
        task.setCreator(scope.getUserIdString());
        task.setTiktokUsername(request.getTiktokUsername().replaceFirst("^@", ""));
        task.setProjectName(request.getProjectName().trim());
        task.setAnchorName(request.isAnchorRequired() ? anchor : null);
        task.setProjectConfirmed(false);
        task.setAnchorConfirmed(false);
        TenantUtils.execute(scope.getTenantId(), () -> { mapper.insert(task); });
        return task.getId();
    }

    public PageResult<Record> page(PageParam param) {
        TkUserScope scope = tenantScope();
        PageResult<TkTiktokOnePublishDO> page = TenantUtils.execute(scope.getTenantId(), () ->
                mapper.selectPage(param, visible(scope).orderByDesc(TkTiktokOnePublishDO::getId)));
        return new PageResult<>(page.getList().stream().map(row -> new Record(row.getId(),
                row.getGenerationTaskId(), row.getTiktokUsername(), row.getProjectName(), row.getAnchorName(),
                row.getVideoUrl(), row.getProjectConfirmed(), row.getAnchorConfirmed(), row.getCreateTime()))
                .collect(Collectors.toList()), page.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Confirmation request) {
        TkUserScope scope = tenantScope();
        TenantUtils.execute(scope.getTenantId(), () -> {
            TkTiktokOnePublishDO task = mapper.selectOne(visible(scope).eq(TkTiktokOnePublishDO::getId, id)
                    .last("FOR UPDATE"));
            if (task == null) {
                throw invalidParamException("合作发布任务不存在");
            }
            dataScope.validateWritable(task.getTenantId(), task.getCompanyId());
            TkTiktokOnePublishRules.Video video;
            try {
                video = TkTiktokOnePublishRules.video(request.getVideoUrl(), task.getTiktokUsername());
            } catch (IllegalArgumentException e) {
                throw invalidParamException("请输入对应达人账号的 TikTok 视频完整链接");
            }
            if (task.getVideoId() != null && !task.getVideoId().equals(video.getId())) {
                throw invalidParamException("此任务已登记其他视频");
            }
            boolean project = Boolean.TRUE.equals(task.getProjectConfirmed()) || request.isProjectConfirmed();
            boolean anchor = Boolean.TRUE.equals(task.getAnchorConfirmed()) || request.isAnchorConfirmed();
            try {
                TkTiktokOnePublishRules.checkConfirmation(task.getAnchorName() != null, project, anchor);
            } catch (IllegalArgumentException e) {
                throw invalidParamException("请先确认项目关联，且任务必须要求网页锚点");
            }
            task.setVideoId(video.getId());
            task.setVideoUrl(video.getUrl());
            task.setProjectConfirmed(project);
            task.setAnchorConfirmed(anchor);
            mapper.updateById(task);
        });
    }

    private TkUserScope tenantScope() {
        TkUserScope scope = dataScope.getCurrentScope();
        if (!scope.hasTenantScope()) {
            throw invalidParamException("请先选择租户");
        }
        return scope;
    }

    private LambdaQueryWrapperX<TkTiktokOnePublishDO> visible(TkUserScope scope) {
        return new LambdaQueryWrapperX<TkTiktokOnePublishDO>()
                .eqIfPresent(TkTiktokOnePublishDO::getTenantId, scope.getTenantId())
                .eqIfPresent(TkTiktokOnePublishDO::getCreator,
                        scope.canReadAllTenantRecords() ? null : scope.getUserIdString());
    }

    @Data
    public static class Create {
        @NotNull @Positive
        private Long generationTaskId;
        @NotBlank @Pattern(regexp = "@?[A-Za-z0-9_.]{1,24}")
        private String tiktokUsername;
        @NotBlank @Size(max = 160)
        private String projectName;
        private boolean anchorRequired;
        @Size(max = 160)
        private String anchorName;
        private boolean anchorApproved;
    }

    @Data
    public static class Confirmation {
        @NotBlank @Size(max = 2048)
        private String videoUrl;
        private boolean projectConfirmed;
        private boolean anchorConfirmed;
    }

    @Value
    public static class Record {
        Long id;
        Long generationTaskId;
        String tiktokUsername;
        String projectName;
        String anchorName;
        String videoUrl;
        Boolean projectConfirmed;
        Boolean anchorConfirmed;
        LocalDateTime createTime;
    }
}
