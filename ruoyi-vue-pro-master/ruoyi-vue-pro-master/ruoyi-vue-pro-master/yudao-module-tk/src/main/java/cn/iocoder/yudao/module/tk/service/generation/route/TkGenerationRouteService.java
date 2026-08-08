package cn.iocoder.yudao.module.tk.service.generation.route;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteHistoryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRoutePageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteUpdateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteHistoryDO;

import java.util.List;

public interface TkGenerationRouteService {

    String DEFAULT_PRODUCT_CATEGORY_CODE = "DEFAULT";

    String DEFAULT_ECOMMERCE_ROUTE_CODE = "ECOM_DEFAULT";

    String DEFAULT_LEAD_GENERATION_ROUTE_CODE = "LEAD_DEFAULT";

    TkGenerationRoute resolveRoute(Long tenantId, String materialPurpose, String productCategoryCode);

    PageResult<TkGenerationRouteDO> getRoutePage(TkGenerationRoutePageReqVO reqVO);

    TkGenerationRouteDO getRoute(Long id);

    void updateRoute(TkGenerationRouteUpdateReqVO updateReqVO);

    PageResult<TkGenerationRouteHistoryDO> getRouteHistoryPage(TkGenerationRouteHistoryPageReqVO reqVO);

    List<TkGenerationRouteStatisticsRespVO> getRouteStatistics(TkGenerationRouteStatisticsReqVO reqVO);

}
