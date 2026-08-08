package cn.iocoder.yudao.module.tk.controller.admin.generation;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchDetailRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationBatchRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationBatchDO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Admin - TK generation batches")
@RestController
@RequestMapping("/tk/generation-batch")
@Validated
public class TkGenerationBatchController {

    @Resource
    private TkGenerationBatchService batchService;

    @GetMapping("/page")
    @Operation(summary = "Get generation batch page")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<PageResult<TkGenerationBatchRespVO>> getBatchPage(@Valid TkGenerationBatchPageReqVO pageReqVO) {
        PageResult<TkGenerationBatchDO> pageResult = batchService.getBatchPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkGenerationBatchRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "Get generation batch detail")
    @PreAuthorize("@ss.hasPermission('tk:generation:query')")
    public CommonResult<TkGenerationBatchDetailRespVO> getBatchDetail(@RequestParam("id") Long id) {
        return success(batchService.getBatchDetail(id));
    }

    @PostMapping("/retry-failed")
    @Operation(summary = "Retry failed generation tasks in a batch")
    @PreAuthorize("@ss.hasPermission('tk:generation:create')")
    public CommonResult<Integer> retryFailedTasks(@RequestParam("id") Long id) {
        return success(batchService.retryFailedTasks(id));
    }
}
