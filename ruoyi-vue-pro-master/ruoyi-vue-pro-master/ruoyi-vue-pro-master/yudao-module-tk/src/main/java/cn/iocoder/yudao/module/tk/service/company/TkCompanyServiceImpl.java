package cn.iocoder.yudao.module.tk.service.company;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanyPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCompanyDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCompanyMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.ENABLE;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_COMPANY_DISABLED;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_COMPANY_NOT_EXISTS;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_FORBIDDEN_WRITE_COMPANY_DATA;

@Service
@Validated
public class TkCompanyServiceImpl implements TkCompanyService {

    @Resource
    private TkCompanyMapper companyMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public Long createCompany(TkCompanySaveReqVO createReqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (!scope.isPlatformAdmin() && !scope.isTenantAdmin()) {
            throw exception(TK_FORBIDDEN_WRITE_COMPANY_DATA);
        }
        TkCompanyDO company = BeanUtils.toBean(createReqVO, TkCompanyDO.class);
        company.setStatus(company.getStatus() == null ? ENABLE.getStatus() : company.getStatus());
        if (!scope.isPlatformAdmin()) {
            company.setTenantId(scope.getTenantId());
        }
        companyMapper.insert(company);
        return company.getId();
    }

    @Override
    public void updateCompany(TkCompanySaveReqVO updateReqVO) {
        TkCompanyDO oldCompany = getCompany(updateReqVO.getId());
        dataScopeService.validateWritable(oldCompany.getTenantId(), oldCompany.getId());
        TkCompanyDO updateObj = BeanUtils.toBean(updateReqVO, TkCompanyDO.class);
        updateObj.setTenantId(oldCompany.getTenantId());
        companyMapper.updateById(updateObj);
    }

    @Override
    public void deleteCompany(Long id) {
        TkCompanyDO company = getCompany(id);
        dataScopeService.validateWritable(company.getTenantId(), company.getId());
        companyMapper.deleteById(id);
    }

    @Override
    public TkCompanyDO getCompany(Long id) {
        TkCompanyDO company = selectById(id);
        if (company == null) {
            throw exception(TK_COMPANY_NOT_EXISTS);
        }
        dataScopeService.validateReadable(company.getTenantId(), company.getId(), null);
        return company;
    }

    @Override
    public PageResult<TkCompanyDO> getCompanyPage(TkCompanyPageReqVO pageReqVO) {
        return companyMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public List<TkCompanyDO> getReadableCompanyList() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> companyMapper.selectSimpleList(scope));
        }
        return companyMapper.selectSimpleList(scope);
    }

    @Override
    public TkCompanyDO validateWritableCompany(Long id) {
        Long companyId = dataScopeService.getWritableCompanyId(id);
        TkCompanyDO company = selectById(companyId);
        if (company == null) {
            throw exception(TK_COMPANY_NOT_EXISTS);
        }
        if (!ENABLE.getStatus().equals(company.getStatus())) {
            throw exception(TK_COMPANY_DISABLED);
        }
        return company;
    }

    private TkCompanyDO selectById(Long id) {
        if (TenantContextHolder.getTenantId() == null) {
            return TenantUtils.executeIgnore(() -> companyMapper.selectById(id));
        }
        return companyMapper.selectById(id);
    }

}
