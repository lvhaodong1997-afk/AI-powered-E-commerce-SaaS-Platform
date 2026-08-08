package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountGroupRelMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokAccountMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokAccountServiceImplTest {

    @Test
    void unbindAccountClearsTokensAndRemovesGroupRelations() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokAccountGroupRelMapper groupRelMapper = mock(TkTiktokAccountGroupRelMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokAccountServiceImpl service = createService(accountMapper, groupRelMapper, dataScopeService);
        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(88L)
                .companyId(200L)
                .openId("open-88")
                .displayName("Demo account")
                .accessTokenCipher("access-token")
                .refreshTokenCipher("refresh-token")
                .tokenStatus("VALID")
                .authStatus("AUTHORIZED")
                .failReason(null)
                .status(0)
                .build();
        account.setTenantId(166L);
        when(accountMapper.selectById(88L)).thenReturn(account);

        service.unbindAccount(88L);

        verify(dataScopeService).validateReadable(166L, 200L, null);
        verify(dataScopeService).validateWritable(166L, 200L);
        verify(groupRelMapper).deleteByAccountId(88L);
        ArgumentCaptor<TkTiktokAccountDO> captor = ArgumentCaptor.forClass(TkTiktokAccountDO.class);
        verify(accountMapper).updateById(captor.capture());
        TkTiktokAccountDO updated = captor.getValue();
        assertNull(updated.getAccessTokenCipher());
        assertNull(updated.getRefreshTokenCipher());
        assertNull(updated.getAccessTokenExpireTime());
        assertNull(updated.getRefreshTokenExpireTime());
        assertEquals("INVALID", updated.getTokenStatus());
        assertEquals("UNAUTHORIZED", updated.getAuthStatus());
        assertEquals(1, updated.getStatus());
        assertEquals("用户已解绑 TikTok 授权", updated.getFailReason());
    }

    @Test
    void updateDefaultConfigSavesTrimmedDisplayName() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokAccountGroupRelMapper groupRelMapper = mock(TkTiktokAccountGroupRelMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokAccountServiceImpl service = createService(accountMapper, groupRelMapper, dataScopeService);
        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(89L)
                .companyId(201L)
                .displayName("Old name")
                .build();
        account.setTenantId(166L);
        when(accountMapper.selectById(89L)).thenReturn(account);
        cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountDefaultConfigReqVO reqVO =
                new cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountDefaultConfigReqVO();
        reqVO.setId(89L);
        reqVO.setDisplayName("  Main US account  ");

        service.updateDefaultConfig(reqVO);

        ArgumentCaptor<TkTiktokAccountDO> captor = ArgumentCaptor.forClass(TkTiktokAccountDO.class);
        verify(accountMapper).updateById(captor.capture());
        assertEquals("Main US account", captor.getValue().getDisplayName());
    }

    @Test
    void deleteAccountRemovesGroupRelationsAndDeletesRecord() {
        TkTiktokAccountMapper accountMapper = mock(TkTiktokAccountMapper.class);
        TkTiktokAccountGroupRelMapper groupRelMapper = mock(TkTiktokAccountGroupRelMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokAccountServiceImpl service = createService(accountMapper, groupRelMapper, dataScopeService);
        TkTiktokAccountDO account = TkTiktokAccountDO.builder()
                .id(90L)
                .companyId(202L)
                .openId("open-90")
                .build();
        account.setTenantId(166L);
        when(accountMapper.selectById(90L)).thenReturn(account);

        service.deleteAccount(90L);

        verify(dataScopeService).validateReadable(166L, 202L, null);
        verify(dataScopeService).validateWritable(166L, 202L);
        verify(groupRelMapper).deleteByAccountId(90L);
        verify(accountMapper).deleteById(90L);
        verify(accountMapper, never()).updateById(account);
    }

    private TkTiktokAccountServiceImpl createService(TkTiktokAccountMapper accountMapper,
                                                     TkTiktokAccountGroupRelMapper groupRelMapper,
                                                     TkDataScopeService dataScopeService) {
        TkTiktokAccountServiceImpl service = new TkTiktokAccountServiceImpl();
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "groupRelMapper", groupRelMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }

}
