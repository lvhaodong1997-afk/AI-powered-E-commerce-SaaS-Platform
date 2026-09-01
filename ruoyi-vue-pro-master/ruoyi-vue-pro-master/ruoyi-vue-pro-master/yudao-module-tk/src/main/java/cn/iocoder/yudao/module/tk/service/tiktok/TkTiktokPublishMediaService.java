package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import org.springframework.web.multipart.MultipartFile;

public interface TkTiktokPublishMediaService {
    TkTiktokPublishMediaDO uploadVideo(MultipartFile file);
}
