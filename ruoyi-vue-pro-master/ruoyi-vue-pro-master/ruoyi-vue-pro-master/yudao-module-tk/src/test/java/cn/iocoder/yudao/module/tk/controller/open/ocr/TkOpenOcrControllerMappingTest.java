package cn.iocoder.yudao.module.tk.controller.open.ocr;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenOcrControllerMappingTest {

    @Test
    void exposesBothPublicApiPrefixes() {
        assertTrue(Arrays.asList(TkOpenOcrController.class.getAnnotation(RequestMapping.class).value())
                .contains("/admin-api/tk/open/v1/ocr"));
        assertTrue(Arrays.asList(TkOpenOcrController.class.getAnnotation(RequestMapping.class).value())
                .contains("/tk/open/v1/ocr"));
    }

    @Test
    void doesNotRequireUserLoginOrTenantContext() {
        assertTrue(TkOpenOcrController.class.isAnnotationPresent(PermitAll.class));
        assertTrue(TkOpenOcrController.class.isAnnotationPresent(TenantIgnore.class));
    }

    @Test
    void acceptsOnlyOneMultipartFileParameter() throws Exception {
        Method method = TkOpenOcrController.class.getDeclaredMethod("imageToText", MultipartFile.class);
        RequestParam requestParam = (RequestParam) method.getParameterAnnotations()[0][0];

        assertEquals("file", requestParam.value());
        assertTrue(method.isAnnotationPresent(PostMapping.class));
    }
}
