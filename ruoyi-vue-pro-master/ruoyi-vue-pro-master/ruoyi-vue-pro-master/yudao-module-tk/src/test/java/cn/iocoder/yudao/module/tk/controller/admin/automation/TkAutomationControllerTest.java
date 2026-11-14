package cn.iocoder.yudao.module.tk.controller.admin.automation;

import cn.iocoder.yudao.module.tk.service.automation.TkAutomationService;
import cn.iocoder.yudao.module.tk.controller.admin.automation.vo.TkAutomationVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TkAutomationControllerTest {
    private final TkAutomationService service = mock(TkAutomationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TkAutomationController(service)).build();

    private String body(String extra) {
        return "{\"libraryId\":10,\"scriptText\":\"完整口播\",\"ttsProvider\":\"MINIMAX\","
                + "\"voiceCode\":\"voice-1\",\"bgmAssetId\":20" + extra + "}";
    }

    @Test void acceptsCompleteRequestAndReturnsTaskId() throws Exception {
        TkAutomationVO.TaskResp task = new TkAutomationVO.TaskResp();
        task.setTaskId(99L);
        when(service.create(any())).thenReturn(task);
        mvc.perform(post("/tk/automation/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body("")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(99));
    }

    @Test void unsupportedOutputTranslationAndVolumeAreRejectedBeforeSubmission() throws Exception {
        for (String extra : new String[]{",\"width\":720", ",\"fps\":60", ",\"translationEnabled\":true",
                ",\"bgmVolume\":40", ",\"bgmVolume\":null", ",\"targetRegion\":\"US\"",
                ",\"durationMode\":\"FIXED\"", ",\"voiceCode\":\"\""}) {
            mvc.perform(post("/tk/automation/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body(extra)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void routesRetainPermissionChecksAndDoNotDeclareAnonymousAccess() {
        for (Method method : TkAutomationController.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
            assertNotNull(permission, method.getName());
            assertFalse(permission.value().contains("permitAll"));
            assertTrue(permission.value().contains(method.getName().equals("create") ? ":create" : ":query"));
        }
    }

    @Test void realMethodSecurityBlocksAnonymousAndMissingPermissionBeforeServiceCalls() throws Exception {
        org.springframework.context.support.StaticApplicationContext beans =
                new org.springframework.context.support.StaticApplicationContext();
        beans.getBeanFactory().registerSingleton("ss", new PermissionGate());
        org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler handler =
                new org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler();
        handler.setApplicationContext(beans);
        org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice advice =
                new org.springframework.security.access.expression.method.ExpressionBasedPreInvocationAdvice();
        advice.setExpressionHandler(handler);
        org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor interceptor =
                new org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor();
        interceptor.setSecurityMetadataSource(new org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource(
                new org.springframework.security.access.expression.method.ExpressionBasedAnnotationAttributeFactory(handler)));
        interceptor.setAccessDecisionManager(new org.springframework.security.access.vote.AffirmativeBased(
                java.util.Collections.singletonList(new org.springframework.security.access.prepost.PreInvocationAuthorizationAdviceVoter(advice))));
        interceptor.setAuthenticationManager(authentication -> authentication);
        interceptor.afterPropertiesSet();
        org.springframework.aop.framework.ProxyFactory factory =
                new org.springframework.aop.framework.ProxyFactory(new TkAutomationController(service));
        factory.addAdvice(interceptor);
        TkAutomationController secured = (TkAutomationController) factory.getProxy();
        try {
            assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                    secured::credits);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", "", java.util.Collections.emptyList()));
            assertThrows(org.springframework.security.access.AccessDeniedException.class, secured::credits);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> secured.create(new TkAutomationVO.CreateReq()));
            verifyNoInteractions(service);
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "user", "", java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("tk:generation:query"))));
            secured.credits();
            verify(service).credits();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            beans.close();
        }
    }

    public static class PermissionGate {
        public boolean hasPermission(String permission) {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(permission));
        }
    }
}
