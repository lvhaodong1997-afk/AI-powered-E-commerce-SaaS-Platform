package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokConnectionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokTokenRefreshJobTest {

    @Test
    void scheduledRefreshDelegatesWithBoundedBatchAndContainsFailures() {
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        when(tokenService.refreshExpiringActiveAccounts(50)).thenThrow(new IllegalStateException("temporary"));
        TkTiktokTokenRefreshJob job = new TkTiktokTokenRefreshJob();
        ReflectionTestUtils.setField(job, "tokenService", tokenService);
        ReflectionTestUtils.setField(job, "openConnectionTokenService", mock(TkOpenTiktokConnectionTokenService.class));

        job.refreshExpiringTokens();

        verify(tokenService).refreshExpiringActiveAccounts(50);
    }

    @Test
    void scheduledRefreshAlsoRefreshesOpenConnections() {
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkOpenTiktokConnectionTokenService openConnectionTokenService = mock(TkOpenTiktokConnectionTokenService.class);
        when(tokenService.refreshExpiringActiveAccounts(50)).thenReturn(2);
        when(openConnectionTokenService.refreshExpiringAuthorizedConnections(50)).thenReturn(3);
        TkTiktokTokenRefreshJob job = new TkTiktokTokenRefreshJob();
        ReflectionTestUtils.setField(job, "tokenService", tokenService);
        ReflectionTestUtils.setField(job, "openConnectionTokenService", openConnectionTokenService);

        job.refreshExpiringTokens();

        verify(tokenService).refreshExpiringActiveAccounts(50);
        verify(openConnectionTokenService).refreshExpiringAuthorizedConnections(50);
    }

    @Test
    void openConnectionRefreshStillRunsWhenLegacyAccountBatchFails() {
        TkTiktokTokenService tokenService = mock(TkTiktokTokenService.class);
        TkOpenTiktokConnectionTokenService openConnectionTokenService = mock(TkOpenTiktokConnectionTokenService.class);
        when(tokenService.refreshExpiringActiveAccounts(50)).thenThrow(new IllegalStateException("temporary"));
        when(openConnectionTokenService.refreshExpiringAuthorizedConnections(50)).thenReturn(1);
        TkTiktokTokenRefreshJob job = new TkTiktokTokenRefreshJob();
        ReflectionTestUtils.setField(job, "tokenService", tokenService);
        ReflectionTestUtils.setField(job, "openConnectionTokenService", openConnectionTokenService);

        job.refreshExpiringTokens();

        verify(openConnectionTokenService).refreshExpiringAuthorizedConnections(50);
    }

}
