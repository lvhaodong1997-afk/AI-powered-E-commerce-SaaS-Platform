package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountRespVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkTiktokAccountControllerTest {

    @Test
    void derivesValidAutoRefreshAndExpiredStatesWithoutExposingTokens() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 23, 22, 0);
        TkTiktokAccountRespVO valid = account("AUTHORIZED", "VALID", now.plusHours(2), now.plusDays(30));
        TkTiktokAccountRespVO autoRefresh = account("AUTHORIZED", "VALID", now.plusMinutes(4), now.plusDays(30));
        TkTiktokAccountRespVO expired = account("AUTHORIZED", "VALID", now.plusHours(2), now.minusSeconds(1));

        TkTiktokAccountController.deriveTokenStatus(valid, now);
        TkTiktokAccountController.deriveTokenStatus(autoRefresh, now);
        TkTiktokAccountController.deriveTokenStatus(expired, now);

        assertEquals("VALID", valid.getTokenStatus());
        assertEquals("AUTO_REFRESH", autoRefresh.getTokenStatus());
        assertEquals("EXPIRED", expired.getTokenStatus());
    }

    private TkTiktokAccountRespVO account(String authStatus, String tokenStatus,
                                          LocalDateTime accessExpire, LocalDateTime refreshExpire) {
        TkTiktokAccountRespVO account = new TkTiktokAccountRespVO();
        account.setAuthStatus(authStatus);
        account.setTokenStatus(tokenStatus);
        account.setAccessTokenExpireTime(accessExpire);
        account.setRefreshTokenExpireTime(refreshExpire);
        return account;
    }

}
