package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokAccountStatsOverviewRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentSyncRespVO;

import java.util.List;

public interface TkTiktokAccountStatsService {

    TkTiktokAccountStatsOverviewRespVO getOverview();

    List<TkTiktokContentSyncRespVO> syncAllAccounts();

}
