package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import org.springframework.web.multipart.MultipartFile;

public interface TkTiktokPublishMediaUploadService {

    TkUploadSessionRespVO createSession(String fileName, Long fileSize, String contentType);

    String refreshReadUrl(String fileUrl);

    TkUploadSessionStatusRespVO getSessionStatus(String uploadId);

    void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk);

    TkTiktokPublishMediaDO complete(String uploadId, String coverUrl);

    void cancel(String uploadId);
}
