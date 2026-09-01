package cn.iocoder.yudao.module.tk.controller.admin.reference;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.security.PermitAll;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertNotNull(method.getAnnotation(TenantIgnore.class));
        assertFalse(method.isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void openTranscriptExtractShouldRequirePermissionsAndTenantScope() throws Exception {
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
        assertFalse(createMethod.isAnnotationPresent(PermitAll.class));
        assertFalse(createMethod.isAnnotationPresent(TenantIgnore.class));
        PreAuthorize createPermission = createMethod.getAnnotation(PreAuthorize.class);
        assertNotNull(createPermission);
        assertEquals("@ss.hasPermission('tk:reference:analyze')", createPermission.value());

        Method syncMethod = controllerClass.getMethod(
                "extractAndWait",
                cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO.class);
        PostMapping syncPostMapping = syncMethod.getAnnotation(PostMapping.class);
        assertNotNull(syncPostMapping);
        assertTrue(Arrays.asList(syncPostMapping.value()).contains("/extract-sync"));
        assertFalse(syncMethod.isAnnotationPresent(PermitAll.class));
        assertFalse(syncMethod.isAnnotationPresent(TenantIgnore.class));
        PreAuthorize syncPermission = syncMethod.getAnnotation(PreAuthorize.class);
        assertNotNull(syncPermission);
        assertEquals("@ss.hasPermission('tk:reference:analyze')", syncPermission.value());

        Method getMethod = controllerClass.getMethod("getExtractTask", Long.class);
        org.springframework.web.bind.annotation.GetMapping getMapping =
                getMethod.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
        assertNotNull(getMapping);
        assertTrue(Arrays.asList(getMapping.value()).contains("/extract/{taskId}"));
        assertFalse(getMethod.isAnnotationPresent(PermitAll.class));
        assertFalse(getMethod.isAnnotationPresent(TenantIgnore.class));
        PreAuthorize queryPermission = getMethod.getAnnotation(PreAuthorize.class);
        assertNotNull(queryPermission);
        assertEquals("@ss.hasPermission('tk:reference:query')", queryPermission.value());
    }

}
