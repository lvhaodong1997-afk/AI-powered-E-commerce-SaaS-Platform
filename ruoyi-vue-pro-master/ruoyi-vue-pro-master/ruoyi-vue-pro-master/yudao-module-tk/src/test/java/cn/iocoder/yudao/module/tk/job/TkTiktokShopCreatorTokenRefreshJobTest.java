package cn.iocoder.yudao.module.tk.job;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.TkTiktokShopAccountMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import cn.iocoder.yudao.module.tk.service.tiktokshop.TkTiktokShopTokenService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopCreatorTokenRefreshJobTest {
    @Test void scansWithoutTenantButRefreshesInEachTenantAndContinuesAfterFailure() {
        TkTiktokShopProperties props = new TkTiktokShopProperties();
        props.setAppKey("test-key");
        TkTiktokShopAccountMapper accounts = mock(TkTiktokShopAccountMapper.class);
        TkTiktokShopTokenService tokens = mock(TkTiktokShopTokenService.class);
        when(accounts.dueForRefresh(eq("test-key"), any(), any())).thenAnswer(call -> {
            assertTrue(TenantContextHolder.isIgnore());
            assertEquals(((LocalDateTime) call.getArgument(1)).plusMinutes(30), call.getArgument(2));
            return Arrays.asList(account(1L, 2L, 3L), account(4L, 5L, 6L));
        });
        doAnswer(call -> {
            assertFalse(TenantContextHolder.isIgnore());
            assertEquals(1L, TenantContextHolder.getTenantId());
            throw new IllegalStateException("fixture");
        }).when(tokens).refreshInBackground(1L, 2L, 3L, 30);
        doAnswer(call -> {
            assertFalse(TenantContextHolder.isIgnore());
            assertEquals(4L, TenantContextHolder.getTenantId());
            return null;
        }).when(tokens).refreshInBackground(4L, 5L, 6L, 30);
        TkTiktokShopCreatorTokenRefreshJob job = new TkTiktokShopCreatorTokenRefreshJob(props, accounts, tokens, 30);
        try {
            job.scan();
            verify(tokens).refreshInBackground(1L, 2L, 3L, 30);
            verify(tokens).refreshInBackground(4L, 5L, 6L, 30);
            assertNull(TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
        } finally {
            job.close();
            TenantContextHolder.clear();
        }
    }

    private TkTiktokShopAccountDO account(long tenant, long company, long id) {
        TkTiktokShopAccountDO account = new TkTiktokShopAccountDO();
        account.setTenantId(tenant);
        account.setCompanyId(company);
        account.setId(id);
        return account;
    }
}
