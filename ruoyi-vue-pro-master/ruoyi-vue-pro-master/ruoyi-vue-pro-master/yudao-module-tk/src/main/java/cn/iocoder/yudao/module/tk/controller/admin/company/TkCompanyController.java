package cn.iocoder.yudao.module.tk.controller.admin.company;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanyPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanyRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanySaveReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.company.vo.TkCompanySimpleRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCompanyDO;
import cn.iocoder.yudao.module.tk.service.company.TkCompanyService;
import cn.iocoder.yudao.module.system.util.TkPlatformAdminUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 公司")
@RestController
@RequestMapping("/tk/company")
@Validated
public class TkCompanyController {

    @Resource
    private TkCompanyService companyService;

    @PostMapping("/create")
    @Operation(summary = "创建 TK 公司")
    @PreAuthorize("@ss.hasPermission('tk:company:create')")
    public CommonResult<Long> createCompany(@Valid @RequestBody TkCompanySaveReqVO createReqVO) {
        TkPlatformAdminUtils.validatePlatformAdmin();
        return success(companyService.createCompany(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新 TK 公司")
    @PreAuthorize("@ss.hasPermission('tk:company:update')")
    public CommonResult<Boolean> updateCompany(@Valid @RequestBody TkCompanySaveReqVO updateReqVO) {
        TkPlatformAdminUtils.validatePlatformAdmin();
        companyService.updateCompany(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除 TK 公司")
    @PreAuthorize("@ss.hasPermission('tk:company:delete')")
    public CommonResult<Boolean> deleteCompany(@RequestParam("id") Long id) {
        TkPlatformAdminUtils.validatePlatformAdmin();
        companyService.deleteCompany(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得 TK 公司")
    @PreAuthorize("@ss.hasPermission('tk:company:query')")
    public CommonResult<TkCompanyRespVO> getCompany(@RequestParam("id") Long id) {
        TkPlatformAdminUtils.validatePlatformAdmin();
        return success(BeanUtils.toBean(companyService.getCompany(id), TkCompanyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得 TK 公司分页")
    @PreAuthorize("@ss.hasPermission('tk:company:query')")
    public CommonResult<PageResult<TkCompanyRespVO>> getCompanyPage(@Valid TkCompanyPageReqVO pageReqVO) {
        TkPlatformAdminUtils.validatePlatformAdmin();
        PageResult<TkCompanyDO> pageResult = companyService.getCompanyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkCompanyRespVO.class));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得 TK 公司精简列表")
    @PreAuthorize("@ss.hasPermission('tk:video-publish-center:query') or @ss.hasPermission('tk:company:query') or @ss.hasPermission('tk:material-library:query')")
    public CommonResult<List<TkCompanySimpleRespVO>> getCompanySimpleList() {
        TkPlatformAdminUtils.validatePlatformAdmin();
        return success(BeanUtils.toBean(companyService.getReadableCompanyList(), TkCompanySimpleRespVO.class));
    }

}
