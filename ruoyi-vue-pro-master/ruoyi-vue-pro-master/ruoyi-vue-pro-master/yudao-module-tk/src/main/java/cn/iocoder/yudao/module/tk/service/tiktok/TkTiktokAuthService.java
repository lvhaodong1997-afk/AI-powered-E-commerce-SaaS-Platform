package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;

public interface TkTiktokAuthService {

    TkTiktokAuthRedirectRespVO createRedirectUrl(TkTiktokAuthRedirectReqVO reqVO);

    TkTiktokAuthCallbackResult handleCallback(String code, String state, String error, String errorDescription);

    TkTiktokQrCodeRespVO startQrCode(TkTiktokQrCodeStartReqVO reqVO);

    TkTiktokQrCodeRespVO getQrCodeStatus(String clientTicket);

}
