package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationOpeningUploadCompleteRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import org.springframework.web.multipart.MultipartFile;

public interface TkGenerationOpeningUploadService {

    TkUploadSessionRespVO createSession(Long libraryId, String fileName, Long fileSize, String contentType);

    TkUploadSessionStatusRespVO getSessionStatus(String uploadId);

    void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk);

    TkGenerationOpeningUploadCompleteRespVO complete(String uploadId);

    TkGenerationOpeningUploadCompleteRespVO validateCompletedUpload(String uploadId, Long libraryId);

    void cancel(String uploadId);
}
