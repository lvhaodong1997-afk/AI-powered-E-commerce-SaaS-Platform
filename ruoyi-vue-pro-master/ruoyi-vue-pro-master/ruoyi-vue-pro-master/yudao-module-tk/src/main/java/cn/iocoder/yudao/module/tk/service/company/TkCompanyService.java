package cn.iocoder.yudao.module.tk.service.company;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanyPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCompanyDO;

import java.util.List;

public interface TkCompanyService {

    Long createCompany(TkCompanySaveReqVO createReqVO);

    void updateCompany(TkCompanySaveReqVO updateReqVO);

    void deleteCompany(Long id);

    TkCompanyDO getCompany(Long id);

    PageResult<TkCompanyDO> getCompanyPage(TkCompanyPageReqVO pageReqVO);

    List<TkCompanyDO> getReadableCompanyList();

    TkCompanyDO validateWritableCompany(Long id);

}
