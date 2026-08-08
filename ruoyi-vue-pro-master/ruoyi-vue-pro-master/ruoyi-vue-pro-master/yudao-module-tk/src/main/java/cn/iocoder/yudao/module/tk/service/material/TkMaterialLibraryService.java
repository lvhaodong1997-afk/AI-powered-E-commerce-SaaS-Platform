package cn.iocoder.yudao.module.tk.service.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibraryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialLibrarySaveReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;

public interface TkMaterialLibraryService {

    Long createMaterialLibrary(TkMaterialLibrarySaveReqVO createReqVO);

    void updateMaterialLibrary(TkMaterialLibrarySaveReqVO updateReqVO);

    void deleteMaterialLibrary(Long id);

    TkMaterialLibraryDO getMaterialLibrary(Long id);

    PageResult<TkMaterialLibraryDO> getMaterialLibraryPage(TkMaterialLibraryPageReqVO pageReqVO);

    TkMaterialLibraryDO validateMaterialLibraryReadable(Long id);

}
