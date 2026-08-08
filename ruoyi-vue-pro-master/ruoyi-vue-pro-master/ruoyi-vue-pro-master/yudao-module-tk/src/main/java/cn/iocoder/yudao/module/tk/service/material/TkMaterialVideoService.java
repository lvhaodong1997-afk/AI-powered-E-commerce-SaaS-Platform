package cn.iocoder.yudao.module.tk.service.material;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface TkMaterialVideoService {

    Long uploadMaterialVideo(Long libraryId, MultipartFile file, String tags, String usagePhase, String segmentType);

    PageResult<TkMaterialVideoDO> getMaterialVideoPage(TkMaterialVideoPageReqVO pageReqVO);

    TkMaterialVideoDO getMaterialVideo(Long id);

    Map<String, Long> getSegmentSummary(Long libraryId);

    void updateUsagePhase(java.util.List<Long> ids, String usagePhase);

    void updateSegmentType(java.util.List<Long> ids, String segmentType);

    void deleteMaterialVideo(Long id);

}
