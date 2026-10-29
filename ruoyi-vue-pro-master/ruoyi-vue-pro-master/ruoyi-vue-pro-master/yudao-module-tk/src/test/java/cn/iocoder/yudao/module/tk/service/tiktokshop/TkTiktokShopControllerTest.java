package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.TkTiktokShopAuthController;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopAuthVO.*;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import javax.annotation.security.PermitAll;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopControllerTest {
    @Test void callbackHasOnlySafeMessageAndStrictHeadersAndExactTarget() throws Exception {
        TkTiktokShopAuthService auth=mock(TkTiktokShopAuthService.class);
        when(auth.callback("raw-code","raw-state","raw-error")).thenReturn(new CallbackResult(5L,true));
        TkTiktokShopAuthController controller=new TkTiktokShopAuthController(auth,TkTiktokShopTokenCipherTest.properties(),new ObjectMapper());
        ResponseEntity<String> response=controller.callback("raw-code","raw-state","raw-error");
        assertEquals(java.nio.charset.StandardCharsets.UTF_8,response.getHeaders().getContentType().getCharset());
        assertEquals("no-referrer",response.getHeaders().getFirst("Referrer-Policy"));
        String csp=response.getHeaders().getFirst("Content-Security-Policy");assertTrue(csp.contains("default-src 'none'"));assertTrue(csp.contains("script-src 'nonce-"));assertFalse(csp.contains("unsafe-inline"));
        String html=response.getBody();assertTrue(html.contains("tiktok-shop-auth"));assertTrue(html.contains("https://ui.example"));assertTrue(html.contains("sessionId"));
        assertFalse(html.contains("raw-code"));assertFalse(html.contains("raw-state"));assertFalse(html.contains("raw-error"));assertFalse(html.contains("fake-secret"));assertFalse(html.contains("postMessage('*'"));
        Method callback=TkTiktokShopAuthController.class.getMethod("callback",String.class,String.class,String.class);
        assertNotNull(callback.getAnnotation(PermitAll.class));assertNotNull(callback.getAnnotation(TenantIgnore.class));assertFalse(callback.getAnnotation(ApiAccessLog.class).enable());
        for(Method m:TkTiktokShopAuthController.class.getDeclaredMethods())if(java.lang.reflect.Modifier.isPublic(m.getModifiers()) && !m.getName().equals("callback"))assertNotNull(m.getAnnotation(PreAuthorize.class),m.getName());
        assertFalse(TkTiktokShopAuthController.class.getMethod("createSession",SessionRequest.class).getAnnotation(ApiAccessLog.class).enable());
    }
    @Test void safeCapabilitiesExcludeSettingsAndExposeCallbackOriginOnly() throws Exception {
        String json=new ObjectMapper().writeValueAsString(new Capabilities(TkTiktokShopTokenCipherTest.properties()));
        assertTrue(json.contains("https://callback.example"));assertFalse(json.contains("/auth/callback"));
        assertFalse(json.contains("appSecret"));assertFalse(json.contains("fake-secret"));assertFalse(json.contains("tokenEncryptionKey"));assertFalse(json.contains("fake-key"));assertTrue(json.contains("524288000"));
    }
    @Test void atomicSqlClaimsAndCasAreFencedAndDisconnectClearsCiphers() {
        String claim=sql(TkTiktokShopAuthSessionMapper.class,"claim");
        for(String condition:new String[]{"state_hash=#{hash}","status='WAITING'","expire_time > #{now}","deleted=0"})assertTrue(claim.contains(condition),condition);
        for(String name:new String[]{"replaceTokens","reauthorize","markReauthorization"}){
            String sql=sql(TkTiktokShopAccountMapper.class,name);assertTrue(sql.contains("token_version=#{version}"));assertTrue(sql.contains("token_version=token_version+1"));assertTrue(sql.contains("tenant_id="));assertTrue(sql.contains("deleted=0"));
        }
        String disconnect=sql(TkTiktokShopAccountMapper.class,"disconnect");assertTrue(disconnect.contains("access_token_cipher=NULL"));assertTrue(disconnect.contains("refresh_token_cipher=NULL"));assertTrue(disconnect.contains("token_version=token_version+1"));assertTrue(disconnect.contains("auth_status='DISCONNECTED'"));
    }
    String sql(Class<?> type,String name){for(Method m:type.getMethods())if(m.getName().equals(name))return String.join(" ",m.getAnnotation(Update.class).value());throw new AssertionError(name);}
}
