package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountGroupDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountGroupRelDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountGroupMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountGroupRelMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_USER_SCOPE_NOT_CONFIGURED;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_TIKTOK_ACCOUNT_GROUP_NOT_EXISTS;

@Service
@Validated
public class TkTiktokAccountGroupServiceImpl implements TkTiktokAccountGroupService {

    @Resource
    private TkTiktokAccountGroupMapper groupMapper;
    @Resource
    private TkTiktokAccountGroupRelMapper groupRelMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkTiktokAccountService accountService;

    @Override
    public PageResult<TkTiktokAccountGroupRespVO> getGroupPage(TkTiktokAccountGroupPageReqVO reqVO) {
        PageResult<TkTiktokAccountGroupDO> pageResult = selectGroupPage(reqVO);
        List<Long> groupIds = pageResult.getList().stream().map(TkTiktokAccountGroupDO::getId).collect(Collectors.toList());
        Map<Long, List<Long>> accountIdMap = getGroupAccountIdMap(groupIds);
        List<TkTiktokAccountGroupRespVO> list = pageResult.getList().stream()
                .map(group -> {
                    TkTiktokAccountGroupRespVO respVO = BeanUtils.toBean(group, TkTiktokAccountGroupRespVO.class);
                    List<Long> accountIds = accountIdMap.getOrDefault(group.getId(), Collections.emptyList());
                    respVO.setAccountIds(accountIds);
                    respVO.setAccountCount(accountIds.size());
                    return respVO;
                })
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    public Long saveGroup(TkTiktokAccountGroupSaveReqVO reqVO) {
        if (reqVO.getId() == null) {
            TkUserScope scope = dataScopeService.getCurrentScope();
            Long tenantId = resolveWritableTenantId(scope);
            Long companyId = resolveCompatibleCompanyId(reqVO.getCompanyId(), scope);
            Long[] result = new Long[1];
            TenantUtils.execute(tenantId, () -> result[0] = createGroupWithinTenant(reqVO, tenantId, companyId));
            return result[0];
        }

        TkTiktokAccountGroupDO group = validateGroupReadable(reqVO.getId());
        dataScopeService.validateWritable(group.getTenantId(), group.getCompanyId());
        Long tenantId = group.getTenantId();
        Long[] result = new Long[1];
        TenantUtils.execute(tenantId, () -> result[0] = updateGroupWithinTenant(reqVO, group, tenantId));
        return result[0];
    }

    private Long createGroupWithinTenant(TkTiktokAccountGroupSaveReqVO reqVO, Long tenantId, Long companyId) {
        TkTiktokAccountGroupDO group = BeanUtils.toBean(reqVO, TkTiktokAccountGroupDO.class);
        group.setCompanyId(companyId);
        group.setStatus(group.getStatus() == null ? 0 : group.getStatus());
        group.setTenantId(tenantId);
        groupMapper.insert(group);
        saveRelations(tenantId, companyId, group.getId(), reqVO.getAccountIds());
        return group.getId();
    }

    private Long updateGroupWithinTenant(TkTiktokAccountGroupSaveReqVO reqVO, TkTiktokAccountGroupDO group, Long tenantId) {
        Long companyId = group.getCompanyId();
        group.setName(reqVO.getName());
        group.setScene(reqVO.getScene());
        group.setLabels(reqVO.getLabels());
        group.setRemark(reqVO.getRemark());
        group.setStatus(reqVO.getStatus() == null ? group.getStatus() : reqVO.getStatus());
        groupMapper.updateById(group);
        groupRelMapper.deleteByGroupId(group.getId());
        saveRelations(tenantId, companyId, group.getId(), reqVO.getAccountIds());
        return group.getId();
    }

    @Override
    public void deleteGroup(Long id) {
        TkTiktokAccountGroupDO group = validateGroupReadable(id);
        dataScopeService.validateWritable(group.getTenantId(), group.getCompanyId());
        Long tenantId = group.getTenantId();
        TenantUtils.execute(tenantId, () -> {
            groupRelMapper.deleteByGroupId(id);
            groupMapper.deleteById(id);
        });
    }

    @Override
    public TkTiktokAccountGroupDO validateGroupReadable(Long id) {
        TkTiktokAccountGroupDO group = selectGroupById(id);
        if (group == null) {
            throw exception(TK_TIKTOK_ACCOUNT_GROUP_NOT_EXISTS);
        }
        dataScopeService.validateReadable(group.getTenantId(), group.getCompanyId(), null);
        return group;
    }

    @Override
    public Map<Long, List<Long>> getGroupAccountIdMap(Collection<Long> groupIds) {
        if (CollUtil.isEmpty(groupIds)) {
            return Collections.emptyMap();
        }
        return selectRelationsByGroupIds(groupIds).stream()
                .collect(Collectors.groupingBy(TkTiktokAccountGroupRelDO::getGroupId,
                        Collectors.mapping(TkTiktokAccountGroupRelDO::getAccountId, Collectors.toList())));
    }

    private void saveRelations(Long tenantId, Long companyId, Long groupId, List<Long> accountIds) {
        if (CollUtil.isEmpty(accountIds)) {
            return;
        }
        Map<Long, TkTiktokAccountDO> readableAccounts = accountService.getReadableAccounts(accountIds).stream()
                .collect(Collectors.toMap(TkTiktokAccountDO::getId, Function.identity()));
        for (Long accountId : new LinkedHashSet<>(accountIds)) {
            TkTiktokAccountDO account = readableAccounts.get(accountId);
            if (account == null || !tenantId.equals(account.getTenantId())) {
                continue;
            }
            TkTiktokAccountGroupRelDO rel = TkTiktokAccountGroupRelDO.builder()
                    .companyId(companyId)
                    .groupId(groupId)
                    .accountId(accountId)
                    .build();
            rel.setTenantId(tenantId);
            groupRelMapper.insert(rel);
        }
    }

    private Long resolveWritableTenantId(TkUserScope scope) {
        if (scope.getTenantId() == null || scope.getTenantId() <= 0) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        return scope.getTenantId();
    }

    private Long resolveCompatibleCompanyId(Long requestedCompanyId, TkUserScope scope) {
        Long companyId = dataScopeService.getWritableCompanyId(requestedCompanyId);
        return companyId == null ? scope.getTenantId() : companyId;
    }

    private TkTiktokAccountGroupDO selectGroupById(Long id) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> groupMapper.selectById(id));
        }
        return groupMapper.selectById(id);
    }

    private PageResult<TkTiktokAccountGroupDO> selectGroupPage(TkTiktokAccountGroupPageReqVO reqVO) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> groupMapper.selectPage(reqVO, dataScopeService.getCurrentScope()));
        }
        return groupMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
    }

    private List<TkTiktokAccountGroupRelDO> selectRelationsByGroupIds(Collection<Long> groupIds) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> groupRelMapper.selectListByGroupIds(groupIds));
        }
        return groupRelMapper.selectListByGroupIds(groupIds);
    }

}
