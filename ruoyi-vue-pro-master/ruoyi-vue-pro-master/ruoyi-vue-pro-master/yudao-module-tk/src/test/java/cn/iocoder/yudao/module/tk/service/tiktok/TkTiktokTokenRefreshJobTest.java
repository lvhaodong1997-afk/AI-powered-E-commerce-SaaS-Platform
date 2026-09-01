package cn.iocoder.yudao.module.tk.service.tiktok;

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

        job.refreshExpiringTokens();

        verify(tokenService).refreshExpiringActiveAccounts(50);
    }

}
