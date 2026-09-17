package cn.iocoder.yudao.module.tk.service.social.stats;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.social.auth.*;
import cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformClient;
import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkSocialStatsSecurityTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void readOnlyStatisticsTokenDoesNotRequirePublishingOrMarkAccountReauth() throws Exception {
        TkSocialProperties config=new TkSocialProperties(); config.setEnabled(true);
        TkSocialAccountMapper mapper=mock(TkSocialAccountMapper.class);
        TkSocialPlatformClient platform=mock(TkSocialPlatformClient.class);
        TkSocialTokenCipher cipher=mock(TkSocialTokenCipher.class);
        TkSocialAccountDO row=new TkSocialAccountDO(); row.setId(4L); row.setTenantId(2L); row.setCompanyId(3L);
        row.setExternalAccountId("42"); row.setPlatform("FACEBOOK_PAGE"); row.setStatus("AUTHORIZED");
        row.setAccessTokenCiphertext("fixture-ciphertext"); row.setScopes("pages_read_engagement");
        when(mapper.selectById(4L)).thenReturn(row);
        when(cipher.decrypt("fixture-ciphertext",TkSocialAccountService.accountContext(row))).thenReturn("fixture-token");
        TkSocialAccountService accounts=new TkSocialAccountService(config,mapper,mock(TkDataScopeService.class),platform,cipher);
        Method read=assertDoesNotThrow(()->TkSocialAccountService.class.getMethod("getStatisticsToken",TkSocialAccountDO.class));
        TenantContextHolder.setTenantId(2L);
        assertEquals("fixture-token",read.invoke(accounts,row));
        verifyNoInteractions(platform);
        TenantContextHolder.setTenantId(99L);
        InvocationTargetException denied=assertThrows(InvocationTargetException.class,()->read.invoke(accounts,row));
        assertTrue(denied.getCause() instanceof IllegalStateException);
        verify(mapper,times(1)).selectById(4L);
    }

    @Test void expiredStatisticsTokenFailsWithoutMutatingAccount() throws Exception {
        TkSocialProperties config=new TkSocialProperties(); config.setEnabled(true);
        TkSocialAccountMapper mapper=mock(TkSocialAccountMapper.class);
        TkSocialAccountDO row=new TkSocialAccountDO(); row.setId(4L); row.setTenantId(2L); row.setExternalAccountId("42");
        row.setStatus("AUTHORIZED"); row.setTokenExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(mapper.selectById(4L)).thenReturn(row);
        TkSocialTokenCipher cipher=mock(TkSocialTokenCipher.class);
        TkSocialAccountService accounts=new TkSocialAccountService(config,mapper,mock(TkDataScopeService.class),mock(TkSocialPlatformClient.class),cipher);
        Method read=assertDoesNotThrow(()->TkSocialAccountService.class.getMethod("getStatisticsToken",TkSocialAccountDO.class));
        TenantContextHolder.setTenantId(2L);
        assertThrows(InvocationTargetException.class,()->read.invoke(accounts,row));
        verifyNoInteractions(cipher); verify(mapper).selectById(4L); verifyNoMoreInteractions(mapper);
    }

    @Test void optionalInstagramProfileCountsCannotBreakBasicAuthorization() throws Exception {
        TkSocialProperties p=new TkSocialProperties(); p.setEnabled(true); p.getInstagram().setAppId("fixture-app"); p.getInstagram().setAppSecret("fixture-secret");
        com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.List<String> fields=new java.util.ArrayList<>();
        TkSocialPlatformClient client=new TkSocialPlatformClient(p,(method,url,params,token,mutation)->{
            try {
                if(url.contains("api.instagram.com")) return json.readTree("{\"access_token\":\"fixture-short\",\"user_id\":\"42\",\"permissions\":[\"instagram_business_basic\",\"instagram_business_content_publish\",\"instagram_business_manage_insights\"]}");
                if(url.endsWith("/access_token")) return json.readTree("{\"access_token\":\"fixture-long\",\"expires_in\":5000}");
                fields.add(params.get("fields"));
                if(params.get("fields").contains("followers_count")) throw new cn.iocoder.yudao.module.tk.service.social.platform.TkSocialPlatformException("META_100","optional field unavailable",false,false,false);
                return json.readTree("{\"id\":\"42\",\"user_id\":\"42\",\"username\":\"fixture\",\"account_type\":\"BUSINESS\"}");
            } catch(java.io.IOException e) { throw new AssertionError(e); }
        });
        assertEquals("42",client.authorizeInstagram("fixture-code","https://example.invalid/callback").getExternalId());
        assertEquals(2,fields.size()); assertFalse(fields.get(1).contains("followers_count"));
    }
}
