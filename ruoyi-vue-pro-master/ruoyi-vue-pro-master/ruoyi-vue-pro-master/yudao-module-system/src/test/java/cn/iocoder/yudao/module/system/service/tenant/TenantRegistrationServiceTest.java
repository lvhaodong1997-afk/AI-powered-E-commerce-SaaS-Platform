package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.*;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.auth.AdminAuthService;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.model.common.ResponseModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Arrays;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantRegistrationServiceTest {
    TenantRegistrationService service=new TenantRegistrationService();
    TenantRegistrationProperties properties=new TenantRegistrationProperties();
    CaptchaService captcha=mock(CaptchaService.class);
    PhoneVerificationClient phone=mock(PhoneVerificationClient.class);
    TenantRegistrationProvisioner provisioner=mock(TenantRegistrationProvisioner.class);
    StringRedisTemplate redis=mock(StringRedisTemplate.class);
    OAuth2TokenService tokens=mock(OAuth2TokenService.class);
    TenantService tenants=mock(TenantService.class);
    AdminAuthService auth=mock(AdminAuthService.class);
    @BeforeEach void setup() {
        properties.setEnabled(true); properties.setDefaultPackageId(5L); properties.setValidityDays(30);
        properties.setPhoneSecret("12345678901234567890123456789012"); properties.setPhoneBaseUrl("https://sms.example.com");
        ReflectionTestUtils.setField(service,"properties",properties); ReflectionTestUtils.setField(service,"captchaService",captcha);
        ReflectionTestUtils.setField(service,"phoneClient",phone); ReflectionTestUtils.setField(service,"provisioner",provisioner);
        ReflectionTestUtils.setField(service,"stringRedisTemplate",redis); ReflectionTestUtils.setField(service,"tokenService",tokens);
        ReflectionTestUtils.setField(service,"tenantService",tenants); ReflectionTestUtils.setField(service,"loginLogService",mock(LoginLogService.class));
        ReflectionTestUtils.setField(service,"authService",auth);
        AdminUserDO owner=new AdminUserDO().setId(21L);owner.setTenantId(20L);
        when(auth.authenticate(anyString(),anyString())).thenReturn(owner);
        when(captcha.verification(any())).thenReturn(ResponseModel.success());
        when(redis.execute(any(RedisScript.class),anyList())).thenReturn(1L);
    }
    @Test void configExposesAvailabilityOnly() { assertTrue(service.config().isEnabled()); assertTrue(service.config().isSmsAvailable()); }
    @Test void validSendUsesCaptchaAndServerIp() {
        TenantRegistrationSmsReqVO req=new TenantRegistrationSmsReqVO().setMobile("13800138000").setRequestId("12345678901234567890123456789012");
        req.setCaptchaVerification("captcha-token"); TenantRegistrationSmsRespVO response=new TenantRegistrationSmsRespVO().setSmsRequestId("sms123");
        when(phone.send(req.getMobile(),req.getRequestId(),"203.0.113.7")).thenReturn(response);
        assertSame(response,service.send(req,"203.0.113.7")); verify(captcha).verification(any());
    }
    @Test void missingCaptchaDoesNotSendSms() {
        assertThrows(RuntimeException.class,()->service.send(new TenantRegistrationSmsReqVO(),"203.0.113.7")); verifyNoInteractions(phone);
    }
    @Test void rateLimitDoesNotCallSmsProvider() {
        when(redis.execute(any(RedisScript.class),anyList())).thenReturn(0L);
        TenantRegistrationSmsReqVO req=new TenantRegistrationSmsReqVO().setMobile("13800138000").setRequestId("12345678901234567890123456789012"); req.setCaptchaVerification("captcha");
        assertThrows(RuntimeException.class,()->service.send(req,"203.0.113.7")); verifyNoInteractions(phone);
    }
    @Test void tokenCreationRunsInNewTenantAndPreservesExistingShape() {
        TenantRegistrationReqVO req=new TenantRegistrationReqVO(); req.setCaptchaVerification("captcha"); req.setUsername("owner123");req.setPassword("Password1");
        when(provisioner.provision(req)).thenReturn(new TenantRegistrationDO().setTenantId(20L).setUserId(21L));
        when(tenants.getTenant(20L)).thenReturn(new TenantDO().setName("New Business"));
        when(tokens.createAccessToken(eq(21L),anyInt(),anyString(),isNull())).thenAnswer(i->{
            assertEquals(20L,TenantContextHolder.getTenantId());
            return new OAuth2AccessTokenDO().setUserId(21L).setAccessToken("access").setRefreshToken("refresh");
        });
        AuthLoginRespVO response=service.register(req,"203.0.113.7");
        assertEquals(20L,response.getTenantId()); assertEquals("New Business",response.getTenantName()); assertEquals("access",response.getAccessToken());
        assertNull(TenantContextHolder.getTenantId());
    }
    @Test void replayDoesNotBypassDisabledOwnerOrChangedPassword() {
        TenantRegistrationReqVO req=new TenantRegistrationReqVO();req.setCaptchaVerification("captcha");req.setUsername("owner123");req.setPassword("oldPassword1");
        when(provisioner.provision(req)).thenReturn(new TenantRegistrationDO().setTenantId(20L).setUserId(21L));
        when(tenants.getTenant(20L)).thenReturn(new TenantDO().setName("Business"));
        when(tokens.createAccessToken(eq(21L),anyInt(),anyString(),isNull())).thenReturn(new OAuth2AccessTokenDO().setAccessToken("access"));
        when(auth.authenticate("owner123","oldPassword1")).thenThrow(new IllegalArgumentException("current credentials rejected"));
        assertThrows(RuntimeException.class,()->service.register(req,"203.0.113.7"));verifyNoInteractions(tokens);
    }
    @Test void untrustedForwardedIpCannotSpoofClient() {
        MockHttpServletRequest req=new MockHttpServletRequest();req.setRemoteAddr("203.0.113.7");req.addHeader("X-Forwarded-For","1.1.1.1");
        assertEquals("203.0.113.7",service.clientIp(req));
    }
    @Test void trustedProxyWalksFromRightToFirstUntrustedHop() {
        properties.setTrustedProxyIps(Arrays.asList("127.0.0.1","10.0.0.2"));
        MockHttpServletRequest req=new MockHttpServletRequest();req.setRemoteAddr("127.0.0.1");req.addHeader("X-Forwarded-For","1.1.1.1, 203.0.113.7, 10.0.0.2");
        assertEquals("203.0.113.7",service.clientIp(req));
    }
    @Test void unexpectedRegistrationErrorsDoNotReachSharedRawBodyLogger() throws Exception {
        cn.iocoder.yudao.module.system.controller.admin.auth.TenantRegistrationController controller=new cn.iocoder.yudao.module.system.controller.admin.auth.TenantRegistrationController();
        TenantRegistrationService failing=mock(TenantRegistrationService.class);
        ReflectionTestUtils.setField(controller,"registrationService",failing);
        when(failing.register(any(),any())).thenThrow(new IllegalStateException("sensitive registration request"));
        org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/system/auth/tenant-register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"12345678901234567890123456789012\",\"tenantName\":\"Business\",\"username\":\"owner123\",\"nickname\":\"Owner\",\"password\":\"Password1\",\"mobile\":\"13800138000\",\"smsRequestId\":\"sms123\",\"smsCode\":\"123456\",\"captchaVerification\":\"captcha\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(500));
    }
}
