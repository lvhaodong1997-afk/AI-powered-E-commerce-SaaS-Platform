package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;

/** Separate transactional boundary so a stale worker cannot leave an orphan READY admin media. */
@Service
public class TkPhotoConversionStore {
    @Resource private TkUploadSessionMapper sessions;
    @Resource private TkTiktokPublishMediaMapper media;

    @Transactional(rollbackFor=Exception.class)
    public Long finishAdmin(TkUploadSessionDO session,String token,String url,TkPhotoNormalizationService.Result result) {
        java.util.concurrent.atomic.AtomicReference<Long> resultId=new java.util.concurrent.atomic.AtomicReference<>();
        TenantUtils.executeIgnore(()->{
            TkTiktokPublishMediaDO m=TkTiktokPublishMediaDO.builder().companyId(session.getCompanyId())
                    .fileName(session.getUploadId()+".jpg").fileUrl(url).fileSize(result.getBytes()).mimeType("image/jpeg").status("READY").build();
            m.setTenantId(session.getTenantId());m.setCreator(session.getCreator());media.insert(m);
            if(sessions.completePhoto(session.getId(),token,m.getId(),result.getWidth(),result.getHeight(),result.getSha256())!=1)
                throw new StaleClaimException();
            resultId.set(m.getId());
        });
        return resultId.get();
    }
    public static class StaleClaimException extends IllegalStateException {
        public StaleClaimException(){super("图片转换会话已取消或租约已失效");}
    }
}
