package cn.iocoder.yudao.module.tk.controller.admin.generation;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TkGenerationTaskControllerTest {

    @Test
    void createWithOpeningShouldAllowMissingOpeningVideoFile() throws Exception {
        Method method = TkGenerationTaskController.class.getMethod(
                "createGenerationTaskWithOpening",
                cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO.class,
                org.springframework.web.multipart.MultipartFile.class);

        RequestParam requestParam = findParameterAnnotation(method, 1, RequestParam.class);
        assertNotNull(requestParam);
        assertFalse(requestParam.required());
    }

    private static <T extends Annotation> T findParameterAnnotation(Method method, int parameterIndex, Class<T> annotationType) {
        for (Annotation annotation : method.getParameterAnnotations()[parameterIndex]) {
            if (annotationType.isInstance(annotation)) {
                return annotationType.cast(annotation);
            }
        }
        return null;
    }

}
