package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionCompleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;

public interface TkMaterialOssUploadService {

    TkUploadSessionRespVO createMaterialVideoSession(Long libraryId, String fileName, Long fileSize, String contentType);

    Long completeMaterialVideoUpload(TkUploadSessionCompleteReqVO reqVO);

    boolean isEnabled();

    boolean isManagedUrl(String url);

    void deleteByUrl(String url);

}
