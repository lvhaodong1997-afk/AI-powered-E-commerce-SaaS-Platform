package cn.iocoder.yudao.module.tk.controller.admin.reference;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkReferenceAnalysisControllerTest {

    @Test
    void downloadVideoShouldExposeStandaloneRoute() {
        boolean matched = Arrays.stream(TkReferenceAnalysisController.class.getDeclaredMethods())
                .filter(method -> "downloadVideo".equals(method.getName()))
                .map(method -> method.getAnnotation(PostMapping.class))
                .anyMatch(mapping -> mapping != null
                        && Arrays.asList(mapping.value()).contains("/video/download"));

        assertTrue(matched);
    }

    @Test
    void openDownloadVideoShouldExposePermitAllRoute() throws Exception {
        Class<?> controllerClass = Class.forName(
                "cn.iocoder.yudao.module.tk.controller.admin.reference.TkOpenVideoDownloadController");

        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        assertNotNull(requestMapping);
        assertTrue(Arrays.asList(requestMapping.value()).contains("/tk/open/video"));

        Method method = controllerClass.getMethod(
                "downloadVideo",
                cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkReferenceVideoDownloadReqVO.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertTrue(Arrays.asList(postMapping.value()).contains("/download"));
        assertNotNull(method.getAnnotation(PermitAll.class));
        assertFalse(method.isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void openTranscriptExtractShouldExposePermitAllRoutes() throws Exception {
        Class<?> controllerClass = Class.forName(
                "cn.iocoder.yudao.module.tk.controller.admin.reference.TkOpenVideoTranscriptExtractController");

        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        assertNotNull(requestMapping);
        assertTrue(Arrays.asList(requestMapping.value()).contains("/tk/open/video/transcript"));

        Method createMethod = controllerClass.getMethod(
                "createExtractTask",
                cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO.class);
        PostMapping postMapping = createMethod.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertTrue(Arrays.asList(postMapping.value()).contains("/extract"));
        assertNotNull(createMethod.getAnnotation(PermitAll.class));
        assertFalse(createMethod.isAnnotationPresent(PreAuthorize.class));

        Method getMethod = controllerClass.getMethod("getExtractTask", Long.class);
        org.springframework.web.bind.annotation.GetMapping getMapping =
                getMethod.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertNotNull(getMapping);
        assertTrue(Arrays.asList(getMapping.value()).contains("/extract/{taskId}"));
        assertNotNull(getMethod.getAnnotation(PermitAll.class));
        assertFalse(getMethod.isAnnotationPresent(PreAuthorize.class));
    }

}
