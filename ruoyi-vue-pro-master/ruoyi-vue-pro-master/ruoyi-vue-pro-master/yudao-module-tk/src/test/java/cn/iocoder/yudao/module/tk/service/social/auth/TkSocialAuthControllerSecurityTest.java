package cn.iocoder.yudao.module.tk.service.social.auth;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.social.TkSocialAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import javax.annotation.security.PermitAll;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialAuthControllerSecurityTest {
    @Test void publicCallbacksDisableSensitiveQueryLoggingAndResolveTenantFromState() throws Exception {
        for (String name:new String[]{"instagramCallback","facebookCallback"}) {
            Method callback=TkSocialAuthController.class.getMethod(name,String.class,String.class,String.class);
            assertNotNull(callback.getAnnotation(PermitAll.class));
            assertNotNull(callback.getAnnotation(TenantIgnore.class));
            assertFalse(callback.getAnnotation(ApiAccessLog.class).enable());
        }
        assertTrue(TkSocialAuthController.class.getMethod("session",String.class).getAnnotation(PreAuthorize.class)
                .value().contains("tk:social-account:authorize"));
    }
}
