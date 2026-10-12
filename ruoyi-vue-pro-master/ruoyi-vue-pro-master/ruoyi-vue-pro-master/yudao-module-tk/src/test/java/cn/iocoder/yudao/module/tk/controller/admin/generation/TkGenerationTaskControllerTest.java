package cn.iocoder.yudao.module.tk.controller.admin.generation;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationRespVO;
import cn.iocoder.yudao.module.tk.service.generation.TkManualScriptTranslationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter;
import org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice;
import org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TkGenerationTaskControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void translationEndpointHasCreatePermissionAndUserRateLimit() throws Exception {
        Method method = translationMethod();
        assertEquals("/translate-script", method.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('tk:generation:create')", method.getAnnotation(PreAuthorize.class).value());
        Annotation limiter = java.util.Arrays.stream(method.getAnnotations())
                .filter(annotation -> annotation.annotationType().getSimpleName().equals("RateLimiter"))
                .findFirst().orElse(null);
        assertNotNull(limiter);
        assertEquals(10, limiter.annotationType().getMethod("count").invoke(limiter));
        assertEquals(60, limiter.annotationType().getMethod("time").invoke(limiter));
        String expression = (String) limiter.annotationType().getMethod("keyArg").invoke(limiter);
        assertTrue(expression.contains("getLoginUserId()"));
        assertFalse(expression.contains("sourceText"));
    }

    @Test
    void translationRequestStringDoesNotExposeSourceInRateLimitLogs() {
        String privateSource = "private script with unpublished brand details";
        TkManualScriptTranslationReqVO request = new TkManualScriptTranslationReqVO();
        request.setSourceText(privateSource);
        request.setTargetLanguage("en");

        String loggedArguments = java.util.Arrays.toString(new Object[] {request});

        assertFalse(loggedArguments.contains(privateSource));
        assertFalse(loggedArguments.contains("sourceText="));
        assertTrue(loggedArguments.contains("targetLanguage=en"));
        assertEquals(privateSource, request.getSourceText());
    }

    @Test
    void translationEndpointDoesNotRecordOriginalOrTranslatedBodiesInAccessLogs() {
        ApiAccessLog accessLog = translationMethod().getAnnotation(ApiAccessLog.class);
        assertNotNull(accessLog);
        assertFalse(accessLog.requestEnable());
        assertFalse(accessLog.responseEnable());
    }

    @Test
    void translationEndpointReturnsDedicatedTranslationContract() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TkGenerationTaskController controller = translationController(calls);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/tk/generation/translate-script")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceText\":\"原始文案\",\"targetLanguage\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceText").value("原始文案"))
                .andExpect(jsonPath("$.data.targetLanguage").value("en"))
                .andExpect(jsonPath("$.data.translatedText").value("Translated copy"));
        assertEquals(1, calls.get());
    }

    @Test
    void translationEndpointRejectsAnonymousAndMissingPermissionBeforeCallingService() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TkGenerationTaskController controller = securedController(translationController(calls));
        Object request = translationMethod().getParameterTypes()[0].getDeclaredConstructor().newInstance();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> invokeTranslation(controller, request));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "", Collections.emptyList()));
        assertThrows(AccessDeniedException.class, () -> invokeTranslation(controller, request));
        assertEquals(0, calls.get());

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "",
                Collections.singletonList(new SimpleGrantedAuthority("tk:generation:create"))));
        assertNotNull(((CommonResult<?>) invokeTranslation(controller, request)).getData());
        assertEquals(1, calls.get());
    }

    private Method translationMethod() {
        return assertDoesNotThrow(() -> {
            Class<?> request = Class.forName("cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationReqVO");
            return TkGenerationTaskController.class.getMethod("translateScript", request);
        });
    }

    private TkGenerationTaskController translationController(AtomicInteger calls) throws Exception {
        translationMethod();
        TkGenerationTaskController controller = new TkGenerationTaskController();
        TkManualScriptTranslationRespVO response = new TkManualScriptTranslationRespVO();
        response.setSourceText("原始文案");
        response.setTargetLanguage("en");
        response.setTranslatedText("Translated copy");
        TkManualScriptTranslationService service = new TkManualScriptTranslationService() {
            @Override
            public TkManualScriptTranslationRespVO translate(TkManualScriptTranslationReqVO request) {
                calls.incrementAndGet();
                return response;
            }
        };
        ReflectionTestUtils.setField(controller, "manualScriptTranslationService", service);
        return controller;
    }

    private TkGenerationTaskController securedController(TkGenerationTaskController controller) throws Exception {
        StaticApplicationContext beans = new StaticApplicationContext();
        beans.getBeanFactory().registerSingleton("ss", new PermissionGate());
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setApplicationContext(beans);
        ExpressionBasedPreInvocationAdvice advice = new ExpressionBasedPreInvocationAdvice();
        advice.setExpressionHandler(handler);
        MethodSecurityInterceptor interceptor = new MethodSecurityInterceptor();
        interceptor.setSecurityMetadataSource(new PrePostAnnotationSecurityMetadataSource(new ExpressionBasedAnnotationAttributeFactory(handler)));
        interceptor.setAccessDecisionManager(new AffirmativeBased(Collections.singletonList(new PreInvocationAuthorizationAdviceVoter(advice))));
        interceptor.setAuthenticationManager(authentication -> authentication);
        interceptor.afterPropertiesSet();
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.addAdvice(interceptor);
        return (TkGenerationTaskController) proxy.getProxy();
    }

    private Object invokeTranslation(TkGenerationTaskController controller, Object request) throws Exception {
        try {
            return translationMethod().invoke(controller, request);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw (Exception) ex.getCause();
        }
    }

    public static class PermissionGate {
        public boolean hasPermission(String permission) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(permission));
        }
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
