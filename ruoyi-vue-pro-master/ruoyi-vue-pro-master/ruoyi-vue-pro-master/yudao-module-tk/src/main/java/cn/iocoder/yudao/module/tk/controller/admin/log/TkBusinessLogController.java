package cn.iocoder.yudao.module.tk.controller.admin.log;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.log.vo.TkBusinessLogPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.log.vo.TkBusinessLogRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkBusinessLogDO;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 业务日志")
@RestController
@RequestMapping("/tk/business-log")
@Validated
public class TkBusinessLogController {

    @Resource
    private TkBusinessLogQueryService businessLogQueryService;

    @GetMapping("/page")
    @Operation(summary = "获得 TK 业务日志分页")
    @PreAuthorize("@ss.hasPermission('tk:business-log:query') and @ss.hasRole('super_admin')")
    public CommonResult<PageResult<TkBusinessLogRespVO>> getBusinessLogPage(@Valid TkBusinessLogPageReqVO pageReqVO) {
        PageResult<TkBusinessLogDO> pageResult = businessLogQueryService.getBusinessLogPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkBusinessLogRespVO.class));
    }

}
