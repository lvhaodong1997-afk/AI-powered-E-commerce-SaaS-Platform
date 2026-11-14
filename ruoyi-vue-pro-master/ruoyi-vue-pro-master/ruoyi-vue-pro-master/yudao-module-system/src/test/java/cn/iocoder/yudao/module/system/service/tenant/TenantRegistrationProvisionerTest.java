package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.controller.admin.auth.vo.TenantRegistrationReqVO;
import cn.iocoder.yudao.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.*;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantRegistrationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TenantRegistrationProvisionerTest {
    TenantRegistrationProvisioner service = new TenantRegistrationProvisioner();
    TenantRegistrationMapper mapper = mock(TenantRegistrationMapper.class);
    TenantService tenants = mock(TenantService.class);
    TenantInviteService invites = mock(TenantInviteService.class);
    PhoneVerificationClient phone = mock(PhoneVerificationClient.class);
    TenantRegistrationProperties properties = new TenantRegistrationProperties();
    @BeforeEach void setup() {
        properties.setEnabled(true); properties.setDefaultPackageId(5L); properties.setValidityDays(30);
        properties.setPhoneSecret("12345678901234567890123456789012");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "registrationMapper", mapper);
        ReflectionTestUtils.setField(service, "tenantService", tenants);
        ReflectionTestUtils.setField(service, "inviteService", invites);
        ReflectionTestUtils.setField(service, "phoneClient", phone);
        when(tenants.createTenant(any())).thenReturn(20L);
        when(tenants.getTenant(20L)).thenReturn(new TenantDO().setId(20L).setContactUserId(21L));
        when(invites.validateInvite(isNull())).thenReturn(null);
    }
    TenantRegistrationReqVO request() {
        TenantRegistrationReqVO r = new TenantRegistrationReqVO();
        r.setRequestId("12345678901234567890123456789012"); r.setTenantName("New Business");
        r.setMobile("13800138000"); r.setSmsRequestId("sms123"); r.setSmsCode("123456");
        r.setUsername("owner123"); r.setNickname("Owner"); r.setPassword("Password1");
        return r;
    }
    @Test void createsZeroCreditTenantAndOwnerBoundToPhone() {
        TenantRegistrationDO result = service.provision(request());
        ArgumentCaptor<TenantSaveReqVO> c = ArgumentCaptor.forClass(TenantSaveReqVO.class);
        verify(tenants).createTenant(c.capture());
        assertEquals(0, c.getValue().getAccountCount()); assertEquals(1, c.getValue().getMemberLimit());
        assertEquals("13800138000", c.getValue().getContactMobile());
        assertEquals(21L, result.getUserId()); verify(phone).consume(any()); verify(invites).createCode(20L);
        verify(invites, never()).bind(anyLong(), anyLong());
    }
    @Test void invalidInviteDoesNotConsumeOtpOrCreateTenant() {
        TenantRegistrationReqVO r = request(); r.setInviteCode("INVALID123");
        when(invites.validateInvite("INVALID123")).thenThrow(new IllegalArgumentException("invalid invite"));
        assertThrows(RuntimeException.class, () -> service.provision(r));
        verifyNoInteractions(phone, tenants); verify(mapper, never()).insert(any(TenantRegistrationDO.class));
    }
    @Test void remoteFailurePreventsTenantCreation() {
        doThrow(new IllegalArgumentException("bad OTP")).when(phone).consume(any());
        assertThrows(RuntimeException.class, () -> service.provision(request()));
        verify(tenants, never()).createTenant(any());
    }
    @Test void sameRequestRecoversExistingOwnerWithoutConsumingAgain() {
        TenantRegistrationReqVO r = request(); TenantRegistrationDO existing = new TenantRegistrationDO();
        existing.setRequestId(r.getRequestId()); existing.setMobile(r.getMobile()); existing.setPayloadHash(service.payloadHash(r));
        existing.setTenantId(20L); existing.setUserId(21L);
        when(mapper.selectByRequestId(r.getRequestId())).thenReturn(existing);
        assertSame(existing, service.provision(r)); verifyNoInteractions(phone, tenants, invites);
    }
    @Test void sameRequestCannotChangePassword() {
        TenantRegistrationReqVO r = request(); TenantRegistrationDO existing = new TenantRegistrationDO();
        existing.setMobile(r.getMobile()); existing.setPayloadHash(service.payloadHash(r)); existing.setUserId(21L);
        when(mapper.selectByRequestId(r.getRequestId())).thenReturn(existing); r.setPassword("changed123");
        assertThrows(RuntimeException.class, () -> service.provision(r)); verifyNoInteractions(phone, tenants, invites);
    }
    @Test void duplicateSelfRegistrationMobileRejectedBeforeOtp() {
        when(mapper.selectByMobile("13800138000")).thenReturn(new TenantRegistrationDO());
        assertThrows(RuntimeException.class, () -> service.provision(request())); verifyNoInteractions(phone, tenants);
    }
    @Test void validReferralIsBoundAfterNewTenantCreation() {
        TenantRegistrationReqVO r = request(); r.setInviteCode("ABCDEFGHIJ");
        when(invites.validateInvite("ABCDEFGHIJ")).thenReturn(9L);
        service.provision(r); verify(invites).bind(9L,20L);
    }
    @Test void memberLimitDoesNotTrackCreditBalance() {
        assertEquals(1, new TenantDO().setAccountCount(0).setMemberLimit(1).resolveMemberLimit(0));
        assertEquals(25, new TenantDO().setAccountCount(25).resolveMemberLimit(3));
        assertEquals(30, new TenantDO().setAccountCount(25).resolveMemberLimit(30));
    }
}
