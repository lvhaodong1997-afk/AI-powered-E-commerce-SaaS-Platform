package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.*;

import java.util.Collection;
import java.util.Map;

public interface TkTiktokPublishService {

    TkTiktokOverviewRespVO getOverview();

    Long createPublishTask(TkTiktokPublishCreateReqVO reqVO);

    PageResult<TkTiktokPublishTaskRespVO> getTaskPage(TkTiktokPublishTaskPageReqVO reqVO);

    PageResult<TkTiktokPublishDetailRespVO> getDetailPage(TkTiktokPublishDetailPageReqVO reqVO);

    TkTiktokPublishUrlRespVO registerPublishUrl(TkTiktokPublishUrlRegisterReqVO reqVO);

    Map<Long, TkTiktokPublishUrlRespVO> getLatestPublishUrlMap(Collection<Long> generationTaskIds);

    void retry(Long detailId);

    void syncStatus(Long taskId);

    int syncStaleProcessingStatus(int limit);

}
