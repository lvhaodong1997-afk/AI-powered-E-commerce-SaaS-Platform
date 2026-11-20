package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.module.tk.controller.admin.openapi.TkOpenApiClientAdminController;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXProperties;
import cn.iocoder.yudao.module.tk.service.open.admin.TkOpenApiClientAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Catches the production 98787dd failure: multiple constructors without {@code @Autowired}
 * make Spring look for a no-arg constructor and abort context refresh.
 */
class TkOpenApiSpringWiringTest {

    @Test
    void adminServiceAndControllerRefreshInSpringContext() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(TkOpenApiClientMapper.class, () -> mock(TkOpenApiClientMapper.class));
            ctx.registerBean(TkOpenApiSecretCipher.class, () -> mock(TkOpenApiSecretCipher.class));
            ctx.registerBean(TkOpenXProperties.class, TkOpenXProperties::new);
            ctx.register(TkOpenApiClientAdminService.class, TkOpenApiClientAdminController.class);
            ctx.refresh();
            assertNotNull(ctx.getBean(TkOpenApiClientAdminService.class));
            assertNotNull(ctx.getBean(TkOpenApiClientAdminController.class));
        }
    }

    @Test
    void openApiAndChannelComponentsHaveUnambiguousConstructors() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        List<String> failures = new ArrayList<>();
        for (String base : List.of(
                "cn.iocoder.yudao.module.tk.service.open",
                "cn.iocoder.yudao.module.tk.service.channel",
                "cn.iocoder.yudao.module.tk.controller.open",
                "cn.iocoder.yudao.module.tk.controller.admin.openapi",
                "cn.iocoder.yudao.module.tk.framework.openapi")) {
            for (BeanDefinition bd : scanner.findCandidateComponents(base)) {
                Class<?> type;
                try {
                    type = Class.forName(bd.getBeanClassName());
                } catch (ClassNotFoundException ex) {
                    failures.add("missing " + bd.getBeanClassName());
                    continue;
                }
                Constructor<?>[] ctors = type.getDeclaredConstructors();
                if (ctors.length <= 1) {
                    continue;
                }
                boolean autowired = Arrays.stream(ctors).anyMatch(ctor -> ctor.isAnnotationPresent(Autowired.class));
                if (!autowired) {
                    failures.add(type.getName() + " has " + ctors.length
                            + " constructors and none is @Autowired: "
                            + Arrays.stream(ctors)
                            .map(ctor -> Arrays.toString(ctor.getParameterTypes()))
                            .collect(Collectors.joining("; ")));
                }
            }
        }
        assertTrue(failures.isEmpty(), () -> String.join("\n", failures));
    }
}