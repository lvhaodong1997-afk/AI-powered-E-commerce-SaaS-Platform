package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;

import java.time.LocalDateTime;

public final class TkUploadSessionAccessPolicy {

    private TkUploadSessionAccessPolicy() {
    }

    public static boolean canAccess(TkUploadSessionDO session, LocalDateTime now) {
        return session != null
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now)
                && "UPLOADING".equalsIgnoreCase(session.getStatus());
    }
}
