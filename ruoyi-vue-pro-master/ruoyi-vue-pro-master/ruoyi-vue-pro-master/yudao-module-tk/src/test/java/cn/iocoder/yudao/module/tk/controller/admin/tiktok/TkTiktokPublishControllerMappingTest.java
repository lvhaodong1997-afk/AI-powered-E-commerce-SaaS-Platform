package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishPostPageReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TkTiktokPublishControllerMappingTest {

    @Test
    void exposesLinkSyncAndPublicPostPageWithReadPermission() throws Exception {
        assertTrue(Arrays.asList(TkTiktokPublishController.class.getDeclaredMethod("syncPublishLinks", Long.class)
                .getAnnotation(PostMapping.class).value()).contains("/link/sync"));
        assertTrue(Arrays.asList(TkTiktokPublishController.class.getDeclaredMethod("getPostPage",
                TkTiktokPublishPostPageReqVO.class).getAnnotation(GetMapping.class).value()).contains("/post-page"));
        assertEquals("@ss.hasPermission('tk:video-publish-center:query')",
                TkTiktokPublishController.class.getDeclaredMethod("syncPublishLinks", Long.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
