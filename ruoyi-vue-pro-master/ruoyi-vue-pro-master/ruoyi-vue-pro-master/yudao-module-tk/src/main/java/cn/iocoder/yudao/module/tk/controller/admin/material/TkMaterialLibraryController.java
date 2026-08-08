package cn.iocoder.yudao.module.tk.controller.admin.material;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibrarySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - TK 素材库")
@RestController
@RequestMapping("/tk/material-library")
@Validated
public class TkMaterialLibraryController {

    @Resource
    private TkMaterialLibraryService materialLibraryService;
    @Resource
    private TkMaterialVideoMapper materialVideoMapper;

    @PostMapping("/create")
    @Operation(summary = "创建素材库")
    @PreAuthorize("@ss.hasPermission('tk:material-library:create')")
    public CommonResult<Long> createMaterialLibrary(@Valid @RequestBody TkMaterialLibrarySaveReqVO createReqVO) {
        return success(materialLibraryService.createMaterialLibrary(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新素材库")
    @PreAuthorize("@ss.hasPermission('tk:material-library:update')")
    public CommonResult<Boolean> updateMaterialLibrary(@Valid @RequestBody TkMaterialLibrarySaveReqVO updateReqVO) {
        materialLibraryService.updateMaterialLibrary(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除素材库")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:material-library:delete')")
    public CommonResult<Boolean> deleteMaterialLibrary(@RequestParam("id") Long id) {
        materialLibraryService.deleteMaterialLibrary(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得素材库")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('tk:material-library:query')")
    public CommonResult<TkMaterialLibraryRespVO> getMaterialLibrary(@RequestParam("id") Long id) {
        TkMaterialLibraryDO library = materialLibraryService.getMaterialLibrary(id);
        return success(convertLibrary(library));
    }

    @GetMapping("/page")
    @Operation(summary = "获得素材库分页")
    @PreAuthorize("@ss.hasPermission('tk:material-library:query')")
    public CommonResult<PageResult<TkMaterialLibraryRespVO>> getMaterialLibraryPage(@Valid TkMaterialLibraryPageReqVO pageReqVO) {
        PageResult<TkMaterialLibraryDO> pageResult = materialLibraryService.getMaterialLibraryPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, TkMaterialLibraryRespVO.class, this::fillLibraryPreview));
    }

    private TkMaterialLibraryRespVO convertLibrary(TkMaterialLibraryDO library) {
        TkMaterialLibraryRespVO respVO = BeanUtils.toBean(library, TkMaterialLibraryRespVO.class);
        fillLibraryPreview(respVO);
        return respVO;
    }

    private void fillLibraryPreview(TkMaterialLibraryRespVO library) {
        if (library == null || library.getId() == null) {
            return;
        }
        TkMaterialVideoDO firstVideo = materialVideoMapper.selectFirstByLibraryId(library.getId());
        if (firstVideo == null) {
            return;
        }
        library.setCoverUrl(StrUtil.blankToDefault(firstVideo.getCoverUrl(), library.getCoverUrl()));
        library.setPreviewVideoUrl(firstVideo.getFileUrl());
    }

}
