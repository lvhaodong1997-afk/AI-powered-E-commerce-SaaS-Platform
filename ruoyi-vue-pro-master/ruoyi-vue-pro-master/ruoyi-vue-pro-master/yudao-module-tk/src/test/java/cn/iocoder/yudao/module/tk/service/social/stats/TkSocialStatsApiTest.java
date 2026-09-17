package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.social.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkSocialStatsApiTest {
    @Test void publicStatusesUseProcessingAndPartialSuccessInBothGetAndSync() {
        TkSocialStatsRepository repo=mock(TkSocialStatsRepository.class);
        TkSocialAccountService accounts=mock(TkSocialAccountService.class);
        TkSocialStatsProperties config=new TkSocialStatsProperties(); config.setEnabled(true);
        TkSocialAccountDO account=new TkSocialAccountDO(); account.setId(4L); account.setTenantId(2L); account.setCompanyId(3L);
        account.setPlatform("INSTAGRAM"); account.setStatus("AUTHORIZED");
        when(accounts.requireReadable(4L)).thenReturn(account);
        TkSocialStatsDO row=new TkSocialStatsDO(); row.setSyncStatus("RUNNING"); row.setMetricsJson("[]");
        when(repo.find(false,2L,4L)).thenReturn(row);
        TkSocialStatsService service=new TkSocialStatsService(repo,accounts,mock(TkSocialAccountMapper.class),
                mock(TkSocialPublishDetailMapper.class),mock(TkSocialPublishTaskMapper.class),mock(TkSocialMediaMapper.class),
                mock(TkDataScopeService.class),mock(TkSocialStatsCollector.class),config);
        assertEquals("PROCESSING",service.accountStats(4L).getSyncStatus());
        assertEquals("PROCESSING",service.syncAccount(4L).getSyncStatus());
        row.setSyncStatus("PARTIAL");
        assertEquals("PARTIAL_SUCCESS",service.accountStats(4L).getSyncStatus());
        assertEquals("PARTIAL_SUCCESS",service.syncAccount(4L).getSyncStatus());
        account.setTokenExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        assertEquals("AUTH_REQUIRED",service.accountStats(4L).getSyncStatus());
        assertThrows(IllegalArgumentException.class,()->service.syncAccount(4L));
    }

    private Class<?> required(String name) { return assertDoesNotThrow(()->Class.forName(name)); }
    @Test void endpointsReuseExistingPermissionsIncludingQueryPermissionForDetailSync() throws Exception {
        Class<?> type=required("cn.iocoder.yudao.module.tk.controller.admin.social.TkSocialStatsController");
        assertEquals("@ss.hasPermission('tk:social-account:query')",type.getMethod("account",Long.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('tk:social-account:update')",type.getMethod("syncAccount",Long.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('tk:social-publish:query')",type.getMethod("media",Long.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('tk:social-publish:query')",type.getMethod("syncMedia",Long.class).getAnnotation(PreAuthorize.class).value());
        assertEquals("/tk/social-account/stats",type.getMethod("account",Long.class).getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/tk/social-publish/detail/stats/sync",type.getMethod("syncMedia",Long.class).getAnnotation(PostMapping.class).value()[0]);
    }

    @Test void deniedCreatorCannotReadOrEnqueueDetailStatistics() throws Exception {
        Class<?> type=required("cn.iocoder.yudao.module.tk.service.social.stats.TkSocialStatsService");
        TkDataScopeService scope=mock(TkDataScopeService.class);
        TkSocialPublishDetailMapper details=mock(TkSocialPublishDetailMapper.class);
        TkSocialPublishDetailDO d=new TkSocialPublishDetailDO(); d.setId(8L); d.setTenantId(2L); d.setCompanyId(3L); d.setCreator("7"); d.setStatus("SUCCESS");
        when(details.selectById(8L)).thenReturn(d);
        doThrow(new IllegalArgumentException("denied")).when(scope).validateReadable(2L,3L,"7");
        Constructor<?> constructor=type.getConstructors()[0]; Class<?>[] parameters=constructor.getParameterTypes(); Object[] args=new Object[parameters.length];
        for(int i=0;i<parameters.length;i++) args[i]=parameters[i]==TkDataScopeService.class?scope:
                parameters[i]==TkSocialPublishDetailMapper.class?details:mock(parameters[i]);
        Object service=constructor.newInstance(args);
        for(String method:new String[]{"mediaStats","syncMedia"}) {
            InvocationTargetException error=assertThrows(InvocationTargetException.class,()->type.getMethod(method,Long.class).invoke(service,8L));
            assertEquals("denied",error.getCause().getMessage());
        }
        for(Object arg:args) if(arg!=scope && arg!=details) verifyNoInteractions(arg);
    }
}
