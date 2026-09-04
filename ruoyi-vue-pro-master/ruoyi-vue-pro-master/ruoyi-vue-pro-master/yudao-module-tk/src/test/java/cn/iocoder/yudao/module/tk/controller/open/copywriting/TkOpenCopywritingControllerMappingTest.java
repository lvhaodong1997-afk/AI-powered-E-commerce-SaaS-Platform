package cn.iocoder.yudao.module.tk.controller.open.copywriting;

import cn.iocoder.yudao.module.tk.controller.admin.open.copywriting.TkOpenCopywritingController;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenCopywritingControllerMappingTest {

    @Test
    void controllerIsUnderAdminPackageSoAdminApiPrefixIsApplied() {
        assertTrue(new AntPathMatcher(".").match(
                "**.controller.admin.**",
                TkOpenCopywritingController.class.getPackage().getName()));
    }

}
