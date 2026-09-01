package cn.iocoder.yudao.module.tk.service.tiktok;

public interface TkTiktokTokenService {

    String getValidAccessToken(Long accountId);

    String forceRefreshAccessToken(Long accountId);

    int refreshExpiringActiveAccounts(int limit);

}
