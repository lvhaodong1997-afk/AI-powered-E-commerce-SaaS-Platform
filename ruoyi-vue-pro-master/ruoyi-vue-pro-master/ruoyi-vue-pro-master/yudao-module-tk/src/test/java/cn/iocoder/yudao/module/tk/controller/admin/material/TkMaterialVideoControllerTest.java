package cn.iocoder.yudao.module.tk.controller.admin.material;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkMaterialVideoControllerTest {

    @Test
    void updateSegmentTypeShouldExposeStableAndCompatibleRoutes() throws Exception {
        Method method = TkMaterialVideoController.class.getMethod(
                "updateSegmentType",
                cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoSegmentTypeUpdateReqVO.class);

        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertTrue(Arrays.asList(mapping.value()).contains("/segment-type"));
        assertTrue(Arrays.asList(mapping.value()).contains("/segment-type/update"));
    }

}
