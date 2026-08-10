package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;

import java.time.LocalDateTime;

public final class TkUploadSessionAccessPolicy {

    private TkUploadSessionAccessPolicy() {
    }

    public static boolean canAccess(TkUploadSessionDO session, Long tenantId, Long companyId,
                                    String creator, LocalDateTime now) {
        return session != null
                && equals(session.getTenantId(), tenantId)
                && equals(session.getCompanyId(), companyId)
                && equals(session.getCreator(), creator)
                && session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(now)
                && "UPLOADING".equalsIgnoreCase(session.getStatus());
    }

    private static boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
