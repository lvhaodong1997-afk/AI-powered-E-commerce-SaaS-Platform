package cn.iocoder.yudao.module.tk.service.open.admin;

import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiClientAdminVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TkOpenApiClientAdminServiceTest {

    @Test
    void shouldReturnPlainSecretsOnceAndPersistOnlyCipherText() {
        TkOpenApiClientMapper mapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "cipher:" + invocation.getArgument(0));
        TkOpenApiClientAdminService service = new TkOpenApiClientAdminService(mapper, cipher);

        TkOpenApiClientAdminVO.CredentialResp response = service.create(createRequest());

        assertTrue(response.getClientId().startsWith("client_"));
        assertNotNull(response.getClientSecret());
        assertNotNull(response.getCallbackSecret());
        ArgumentCaptor<TkOpenApiClientDO> captor = ArgumentCaptor.forClass(TkOpenApiClientDO.class);
        verify(mapper).insert(captor.capture());
        TkOpenApiClientDO stored = captor.getValue();
        assertEquals("cipher:" + response.getClientSecret(), stored.getClientSecretCipher());
        assertEquals("cipher:" + response.getCallbackSecret(), stored.getCallbackSecretCipher());
        assertNotEquals(response.getClientSecret(), stored.getClientSecretCipher());
        assertNotEquals(response.getCallbackSecret(), stored.getCallbackSecretCipher());
    }

    @Test
    void shouldRotateOnlyRequestedSecret() {
        TkOpenApiClientMapper mapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher cipher = mock(TkOpenApiSecretCipher.class);
        when(mapper.selectByClientId("client_b")).thenReturn(new TkOpenApiClientDO().setId(1L)
                .setClientId("client_b").setClientSecretCipher("old-client").setCallbackSecretCipher("old-callback"));
        when(cipher.encrypt(anyString())).thenAnswer(invocation -> "cipher:" + invocation.getArgument(0));
        TkOpenApiClientAdminService service = new TkOpenApiClientAdminService(mapper, cipher);

        TkOpenApiClientAdminVO.CredentialResp response = service.rotateSecret("client_b", "CLIENT");

        assertNotNull(response.getClientSecret());
        assertNull(response.getCallbackSecret());
        ArgumentCaptor<TkOpenApiClientDO> captor = ArgumentCaptor.forClass(TkOpenApiClientDO.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("cipher:" + response.getClientSecret(), captor.getValue().getClientSecretCipher());
        assertNull(captor.getValue().getCallbackSecretCipher());
    }

    private TkOpenApiClientAdminVO.CreateReq createRequest() {
        TkOpenApiClientAdminVO.CreateReq request = new TkOpenApiClientAdminVO.CreateReq();
        request.setClientName("Application B");
        request.setPermissions("auth,media,publish");
        request.setRateLimitPerMinute(120);
        request.setDailyQuota(10000);
        request.setStatus(0);
        return request;
    }
}
