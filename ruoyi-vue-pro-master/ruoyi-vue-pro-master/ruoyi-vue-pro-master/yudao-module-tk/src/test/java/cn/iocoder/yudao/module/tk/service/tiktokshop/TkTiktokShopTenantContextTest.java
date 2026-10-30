package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopAuthController;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAccountMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAuthSessionMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokShopTenantContextTest {
    AnnotationConfigApplicationContext context;
    TkDataScopeService scope;
    TkTiktokShopAccountMapper accounts;
    TkTiktokShopAuthController controller;

    @BeforeEach void start() {
        TenantContextHolder.clear();
        context=new AnnotationConfigApplicationContext(Config.class);
        scope=context.getBean(TkDataScopeService.class);
        accounts=context.getBean(TkTiktokShopAccountMapper.class);
        controller=context.getBean(TkTiktokShopAuthController.class);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,165L,"TENANT_ADMIN",165L));
        when(scope.getWritableCompanyId(null)).thenReturn(165L);
        when(accounts.selectList(any())).thenAnswer(call->{
            assertEquals(165L,TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return Collections.emptyList();
        });
    }

    @AfterEach void stop() { context.close();TenantContextHolder.clear(); }

    @Test void accountEndpointWorksWithoutGlobalTenantWebFilters() {
        assertTrue(controller.accounts().getData().isEmpty());
        verify(accounts).selectList(any());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test void matchingExistingContextIsPreserved() {
        TenantContextHolder.setTenantId(165L);
        assertTrue(controller.accounts().getData().isEmpty());
        assertEquals(165L,TenantContextHolder.getTenantId());
    }

    @Test void mismatchedContextAndIgnoredContextRemainDenied() {
        TenantContextHolder.setTenantId(999L);
        assertThrows(IllegalArgumentException.class,()->controller.accounts());
        assertEquals(999L,TenantContextHolder.getTenantId());
        TenantContextHolder.setTenantId(165L);TenantContextHolder.setIgnore(true);
        assertThrows(IllegalArgumentException.class,()->controller.accounts());
        assertTrue(TenantContextHolder.isIgnore());
        verifyNoInteractions(accounts);
    }

    @Test void platformAdminMustSelectAConcreteTenant() {
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L,null,"PLATFORM_ADMIN",null));
        assertThrows(IllegalArgumentException.class,()->controller.accounts());
        verifyNoInteractions(accounts);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test void failedAccountReadRestoresContext() {
        doThrow(new IllegalStateException("storage unavailable")).when(accounts).selectList(any());
        assertThrows(IllegalStateException.class,()->controller.accounts());
        assertNull(TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test void capabilitiesAndAnonymousCallbackDoNotRequireUserScope() {
        assertNotNull(controller.capabilities().getData());
        assertTrue(controller.callback(null,null,null).getStatusCode().is2xxSuccessful());
        verifyNoInteractions(scope,accounts);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass=true)
    @ComponentScan(basePackages="cn.iocoder.yudao.module.tk.framework.config",useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*TkTiktokShopTenantAspect"))
    static class Config {
        @Bean TkDataScopeService scope(){return mock(TkDataScopeService.class);}
        @Bean TkTiktokShopAccountMapper accounts(){return mock(TkTiktokShopAccountMapper.class);}
        @Bean TkTiktokShopAuthService auth(TkDataScopeService scope,TkTiktokShopAccountMapper accounts){
            return new TkTiktokShopAuthService(TkTiktokShopTokenCipherTest.properties(),
                    mock(TkTiktokShopAuthSessionMapper.class),accounts,scope,mock(TkTiktokShopApiClient.class),
                    new TkTiktokShopTokenCipher(TkTiktokShopTokenCipherTest.properties()),new ObjectMapper(),
                    mock(PlatformTransactionManager.class));
        }
        @Bean TkTiktokShopAuthController controller(TkTiktokShopAuthService auth){
            return new TkTiktokShopAuthController(auth,TkTiktokShopTokenCipherTest.properties(),new ObjectMapper());
        }
    }
}
