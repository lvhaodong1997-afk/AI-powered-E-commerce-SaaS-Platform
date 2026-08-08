package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import org.springframework.web.multipart.MultipartFile;

public interface TkMaterialChunkUploadService {

    TkUploadSessionRespVO createMaterialVideoSession(Long libraryId, String fileName, Long fileSize, String contentType);

    TkUploadSessionStatusRespVO getSessionStatus(String uploadId);

    void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk);

    Long completeMaterialVideoUpload(String uploadId, String tags, String usagePhase, String segmentType);

    void cancel(String uploadId);

}
