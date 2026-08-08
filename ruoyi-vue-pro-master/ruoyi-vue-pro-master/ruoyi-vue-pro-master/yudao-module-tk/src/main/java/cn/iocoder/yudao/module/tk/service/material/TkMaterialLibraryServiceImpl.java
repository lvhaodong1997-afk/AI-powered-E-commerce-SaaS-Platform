package cn.iocoder.yudao.module.tk.service.material;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibrarySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkMaterialLibraryServiceImpl implements TkMaterialLibraryService {

    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public Long createMaterialLibrary(TkMaterialLibrarySaveReqVO createReqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long tenantId = resolveWritableTenantId(scope);
        Long companyId = dataScopeService.getWritableCompanyId(createReqVO.getCompanyId());
        TkMaterialLibraryDO library = BeanUtils.toBean(createReqVO, TkMaterialLibraryDO.class)
                .setCompanyId(companyId)
                .setMaterialPurpose(TkGeminiPromptConfig.normalizeMaterialPurpose(createReqVO.getMaterialPurpose()))
                .setVideoCount(0)
                .setTotalSize(0L)
                .setDefaulted(Boolean.TRUE.equals(createReqVO.getDefaulted()))
                .setStatus(createReqVO.getStatus() == null ? CommonStatusEnum.ENABLE.getStatus() : createReqVO.getStatus());
        library.setTenantId(tenantId);
        libraryMapper.insert(library);
        return library.getId();
    }

    @Override
    public void updateMaterialLibrary(TkMaterialLibrarySaveReqVO updateReqVO) {
        TkMaterialLibraryDO oldLibrary = validateMaterialLibraryWritable(updateReqVO.getId());
        TkMaterialLibraryDO updateObj = BeanUtils.toBean(updateReqVO, TkMaterialLibraryDO.class)
                .setCompanyId(oldLibrary.getCompanyId())
                .setMaterialPurpose(TkGeminiPromptConfig.normalizeMaterialPurpose(updateReqVO.getMaterialPurpose()));
        libraryMapper.updateById(updateObj);
    }

    @Override
    public void deleteMaterialLibrary(Long id) {
        validateMaterialLibraryWritable(id);
        if (videoMapper.existsByLibraryId(id)) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EMPTY);
        }
        libraryMapper.deleteById(id);
    }

    @Override
    public TkMaterialLibraryDO getMaterialLibrary(Long id) {
        return validateMaterialLibraryReadable(id);
    }

    @Override
    public PageResult<TkMaterialLibraryDO> getMaterialLibraryPage(TkMaterialLibraryPageReqVO pageReqVO) {
        return libraryMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public TkMaterialLibraryDO validateMaterialLibraryReadable(Long id) {
        TkMaterialLibraryDO library = libraryMapper.selectById(id);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateReadable(library.getTenantId(), library.getCompanyId(), null);
        return library;
    }

    private TkMaterialLibraryDO validateMaterialLibraryWritable(Long id) {
        TkMaterialLibraryDO library = libraryMapper.selectById(id);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateWritable(library.getTenantId(), library.getCompanyId());
        return library;
    }

    private Long resolveWritableTenantId(TkUserScope scope) {
        if (scope.getTenantId() == null || scope.getTenantId() <= 0) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        return scope.getTenantId();
    }

}
