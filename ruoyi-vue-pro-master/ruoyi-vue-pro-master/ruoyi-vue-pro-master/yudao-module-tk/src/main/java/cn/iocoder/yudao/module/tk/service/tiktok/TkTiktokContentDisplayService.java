package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentSyncRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokContentVideoRespVO;

public interface TkTiktokContentDisplayService {

    PageResult<TkTiktokContentVideoRespVO> getPage(TkTiktokContentVideoPageReqVO reqVO);

    TkTiktokContentSyncRespVO syncAccount(Long accountId);

    TkTiktokContentVideoRespVO refreshVideo(Long accountId, String videoId);

}
