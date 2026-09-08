package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokContentDisplayControllerMappingTest {

    @Test
    void exposesDisplayPageAndSyncRoutesWithDedicatedPermissions() throws Exception {
        assertTrue(Arrays.asList(TkTiktokContentDisplayController.class.getDeclaredMethod("getPage",
                TkTiktokContentVideoPageReqVO.class).getAnnotation(GetMapping.class).value()).contains("/page"));
        assertEquals("@ss.hasPermission('tk:tiktok-content-display:query')",
                TkTiktokContentDisplayController.class.getDeclaredMethod("getPage",
                        TkTiktokContentVideoPageReqVO.class).getAnnotation(PreAuthorize.class).value());
        assertTrue(Arrays.asList(TkTiktokContentDisplayController.class.getDeclaredMethod("sync", Long.class)
                .getAnnotation(PostMapping.class).value()).contains("/sync"));
    }
}
