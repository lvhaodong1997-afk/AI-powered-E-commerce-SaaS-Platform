package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountDefaultConfigReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountGroupRelMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_TIKTOK_ACCOUNT_NOT_EXISTS;

@Service
@Validated
public class TkTiktokAccountServiceImpl implements TkTiktokAccountService {

    @Resource
    private TkTiktokAccountMapper accountMapper;
    @Resource
    private TkTiktokAccountGroupRelMapper groupRelMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public PageResult<TkTiktokAccountDO> getAccountPage(TkTiktokAccountPageReqVO reqVO) {
        return accountMapper.selectPage(reqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public List<TkTiktokAccountDO> getEnabledAccounts() {
        return accountMapper.selectEnabledList(dataScopeService.getCurrentScope());
    }

    @Override
    public List<TkTiktokAccountDO> getReadableAccounts(Collection<Long> accountIds) {
        if (CollUtil.isEmpty(accountIds)) {
            return Collections.emptyList();
        }
        return accountMapper.selectListByIds(accountIds, dataScopeService.getCurrentScope());
    }

    @Override
    public void updateDefaultConfig(TkTiktokAccountDefaultConfigReqVO reqVO) {
        TkTiktokAccountDO account = validateAccountReadable(reqVO.getId());
        dataScopeService.validateWritable(account.getTenantId(), account.getCompanyId());
        if (reqVO.getDisplayName() != null) {
            account.setDisplayName(StrUtil.trimToNull(reqVO.getDisplayName()));
        }
        if (reqVO.getDefaultPrivacyLevel() != null) {
            account.setDefaultPrivacyLevel(reqVO.getDefaultPrivacyLevel());
        }
        if (reqVO.getAllowComment() != null) {
            account.setAllowComment(reqVO.getAllowComment());
        }
        if (reqVO.getAllowDuet() != null) {
            account.setAllowDuet(reqVO.getAllowDuet());
        }
        if (reqVO.getAllowStitch() != null) {
            account.setAllowStitch(reqVO.getAllowStitch());
        }
        if (reqVO.getCommercialContent() != null) {
            account.setCommercialContent(reqVO.getCommercialContent());
        }
        if (reqVO.getBrandContent() != null) {
            account.setBrandContent(reqVO.getBrandContent());
        }
        if (reqVO.getAigcContent() != null) {
            account.setAigcContent(reqVO.getAigcContent());
        }
        if (reqVO.getLabels() != null) {
            account.setLabels(reqVO.getLabels());
        }
        if (reqVO.getStatus() != null) {
            account.setStatus(reqVO.getStatus());
        }
        accountMapper.updateById(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindAccount(Long id) {
        TkTiktokAccountDO account = validateAccountReadable(id);
        dataScopeService.validateWritable(account.getTenantId(), account.getCompanyId());
        account.setAccessTokenCipher(null);
        account.setRefreshTokenCipher(null);
        account.setAccessTokenExpireTime(null);
        account.setRefreshTokenExpireTime(null);
        account.setTokenStatus("INVALID");
        account.setAuthStatus("UNAUTHORIZED");
        account.setStatus(1);
        account.setFailReason("用户已解绑 TikTok 授权");
        groupRelMapper.deleteByAccountId(account.getId());
        accountMapper.updateById(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long id) {
        TkTiktokAccountDO account = validateAccountReadable(id);
        dataScopeService.validateWritable(account.getTenantId(), account.getCompanyId());
        groupRelMapper.deleteByAccountId(account.getId());
        accountMapper.deleteById(account.getId());
    }

    @Override
    public TkTiktokAccountDO validateAccountReadable(Long id) {
        TkTiktokAccountDO account = accountMapper.selectById(id);
        if (account == null) {
            throw exception(TK_TIKTOK_ACCOUNT_NOT_EXISTS);
        }
        dataScopeService.validateReadable(account.getTenantId(), account.getCompanyId(), null);
        return account;
    }

}
