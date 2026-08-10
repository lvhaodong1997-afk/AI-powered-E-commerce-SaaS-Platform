package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkUploadSessionAccessPolicyTest {

    @Test
    void rejectsExpiredOrForeignSession() {
        TkUploadSessionDO session = new TkUploadSessionDO()
                .setCompanyId(20L)
                .setExpiresAt(LocalDateTime.now().minusMinutes(1));
        session.setTenantId(10L);
        session.setCreator("30");

        assertFalse(TkUploadSessionAccessPolicy.canAccess(session, 10L, 20L, "30", LocalDateTime.now()));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        assertFalse(TkUploadSessionAccessPolicy.canAccess(session, 11L, 20L, "30", LocalDateTime.now()));
    }
}
