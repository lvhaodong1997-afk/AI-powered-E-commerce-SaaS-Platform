package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkTiktokAccountStatsControllerMappingTest {

    @Test
    void keepsOverviewReadPermissionAndUsesContentSyncPermissionForVideoList() throws Exception {
        PreAuthorize overviewPermission = TkTiktokAccountStatsController.class
                .getDeclaredMethod("getOverview")
                .getAnnotation(PreAuthorize.class);
        PreAuthorize syncPermission = TkTiktokAccountStatsController.class
                .getDeclaredMethod("sync")
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasPermission('tk:tiktok-account-stats:query')", overviewPermission.value());
        assertEquals("@ss.hasPermission('tk:tiktok-content-display:sync')", syncPermission.value());
    }
}
