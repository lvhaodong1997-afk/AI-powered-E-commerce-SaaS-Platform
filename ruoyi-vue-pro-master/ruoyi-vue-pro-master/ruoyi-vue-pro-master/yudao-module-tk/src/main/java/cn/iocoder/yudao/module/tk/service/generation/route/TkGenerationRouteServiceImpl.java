package cn.iocoder.yudao.module.tk.service.generation.route;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteHistoryPageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRoutePageReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteUpdateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteHistoryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationRouteHistoryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationRouteMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TkGenerationRouteServiceImpl implements TkGenerationRouteService {

    @Resource
    private TkGenerationRouteMapper routeMapper;
    @Resource
    private TkGenerationRouteHistoryMapper routeHistoryMapper;
    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private TkDataScopeService dataScopeService;

    @Override
    public TkGenerationRoute resolveRoute(Long tenantId, String materialPurpose, String productCategoryCode) {
        String normalizedPurpose = TkGeminiPromptConfig.normalizeMaterialPurpose(materialPurpose);
        String normalizedCategory = normalizeProductCategoryCode(productCategoryCode);
        TkGenerationRouteDO route = selectRoute(tenantId, normalizedPurpose, normalizedCategory);
        if (route == null && !DEFAULT_PRODUCT_CATEGORY_CODE.equals(normalizedCategory)) {
            route = selectRoute(tenantId, normalizedPurpose, DEFAULT_PRODUCT_CATEGORY_CODE);
        }
        if (route == null) {
            return new TkGenerationRoute(normalizedCategory, defaultRouteCode(normalizedPurpose), null);
        }
        return new TkGenerationRoute(normalizedCategory, route.getRouteCode(), route.getRouteConfig());
    }

    @Override
    public PageResult<TkGenerationRouteDO> getRoutePage(TkGenerationRoutePageReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        return routeMapper.selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationRouteDO>()
                .eqIfPresent(TkGenerationRouteDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationRouteDO::getMaterialPurpose, StrUtil.blankToDefault(reqVO.getMaterialPurpose(), null))
                .eqIfPresent(TkGenerationRouteDO::getProductCategoryCode, StrUtil.blankToDefault(reqVO.getProductCategoryCode(), null))
                .eqIfPresent(TkGenerationRouteDO::getRouteCode, StrUtil.blankToDefault(reqVO.getRouteCode(), null))
                .likeIfPresent(TkGenerationRouteDO::getRouteName, reqVO.getRouteName())
                .eqIfPresent(TkGenerationRouteDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(TkGenerationRouteDO::getMaterialPurpose)
                .orderByAsc(TkGenerationRouteDO::getProductCategoryCode)
                .orderByAsc(TkGenerationRouteDO::getRouteCode));
    }

    @Override
    public TkGenerationRouteDO getRoute(Long id) {
        return routeMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRoute(TkGenerationRouteUpdateReqVO updateReqVO) {
        TkGenerationRouteDO route = routeMapper.selectById(updateReqVO.getId());
        if (route == null) {
            throw new IllegalArgumentException("Generation route not found: " + updateReqVO.getId());
        }
        dataScopeService.validateWritable(route.getTenantId(), null);
        TkGenerationRouteHistoryDO history = BeanUtils.toBean(route, TkGenerationRouteHistoryDO.class);
        history.setRouteId(route.getId());
        history.setChangeReason(resolveString(updateReqVO.getRemark(), route.getRemark()));
        routeHistoryMapper.insert(history);

        Integer currentVersion = route.getRouteVersion() == null ? 1 : route.getRouteVersion();
        route.setRouteVersion(currentVersion + 1);
        route.setRouteName(resolveString(updateReqVO.getRouteName(), route.getRouteName()));
        route.setRouteConfig(resolveString(updateReqVO.getRouteConfig(), route.getRouteConfig()));
        route.setTrafficWeight(updateReqVO.getTrafficWeight() == null ? route.getTrafficWeight() : updateReqVO.getTrafficWeight());
        route.setAbGroup(resolveString(updateReqVO.getAbGroup(), route.getAbGroup()));
        route.setEnabled(updateReqVO.getEnabled() == null ? route.getEnabled() : updateReqVO.getEnabled());
        route.setLastPublishTime(updateReqVO.getLastPublishTime() == null ? route.getLastPublishTime() : updateReqVO.getLastPublishTime());
        route.setRemark(resolveString(updateReqVO.getRemark(), route.getRemark()));
        routeMapper.updateById(route);
    }

    @Override
    public PageResult<TkGenerationRouteHistoryDO> getRouteHistoryPage(TkGenerationRouteHistoryPageReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        return routeHistoryMapper.selectPage(reqVO, new LambdaQueryWrapperX<TkGenerationRouteHistoryDO>()
                .eqIfPresent(TkGenerationRouteHistoryDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId())
                .eqIfPresent(TkGenerationRouteHistoryDO::getRouteId, reqVO.getRouteId())
                .eqIfPresent(TkGenerationRouteHistoryDO::getMaterialPurpose, StrUtil.blankToDefault(reqVO.getMaterialPurpose(), null))
                .eqIfPresent(TkGenerationRouteHistoryDO::getProductCategoryCode, StrUtil.blankToDefault(reqVO.getProductCategoryCode(), null))
                .eqIfPresent(TkGenerationRouteHistoryDO::getRouteCode, StrUtil.blankToDefault(reqVO.getRouteCode(), null))
                .orderByDesc(TkGenerationRouteHistoryDO::getRouteVersion)
                .orderByDesc(TkGenerationRouteHistoryDO::getCreateTime));
    }

    @Override
    public List<TkGenerationRouteStatisticsRespVO> getRouteStatistics(TkGenerationRouteStatisticsReqVO reqVO) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        LocalDateTime startTime = reqVO.getStartTime() == null ? LocalDateTime.now().minusDays(30) : reqVO.getStartTime();
        LocalDateTime endTime = reqVO.getEndTime() == null ? LocalDateTime.now() : reqVO.getEndTime();
        List<TkGenerationRouteStatisticsRespVO> statistics = taskMapper.selectRouteStatistics(
                scope.isGlobalPlatformView() ? null : scope.getTenantId(),
                scope.canReadAllTenantRecords() ? null : scope.getUserIdString(),
                startTime, endTime,
                StrUtil.blankToDefault(reqVO.getMaterialPurpose(), null),
                StrUtil.blankToDefault(reqVO.getProductCategoryCode(), null),
                StrUtil.blankToDefault(reqVO.getRouteCode(), null));
        if (statistics.isEmpty()) {
            return statistics;
        }
        Map<String, String> routeNameMap = routeMapper.selectList(new LambdaQueryWrapperX<TkGenerationRouteDO>()
                        .eqIfPresent(TkGenerationRouteDO::getTenantId, scope.isGlobalPlatformView() ? null : scope.getTenantId()))
                .stream()
                .filter(item -> StrUtil.isNotBlank(item.getRouteCode()))
                .collect(Collectors.toMap(
                        item -> buildRouteNameKey(item.getMaterialPurpose(), item.getProductCategoryCode(), item.getRouteCode()),
                        TkGenerationRouteDO::getRouteName,
                        (left, right) -> left));
        statistics.forEach(item -> item.setRouteName(routeNameMap.getOrDefault(
                buildRouteNameKey(item.getMaterialPurpose(), item.getProductCategoryCode(), item.getRouteCode()),
                item.getRouteCode())));
        return statistics;
    }

    private TkGenerationRouteDO selectRoute(Long tenantId, String materialPurpose, String productCategoryCode) {
        TkGenerationRouteDO route = routeMapper.selectEnabledRoute(tenantId, materialPurpose, productCategoryCode);
        if (route == null && tenantId != null && tenantId != 0L) {
            route = routeMapper.selectEnabledRoute(0L, materialPurpose, productCategoryCode);
        }
        return route;
    }

    private String normalizeProductCategoryCode(String productCategoryCode) {
        String code = StrUtil.trimToEmpty(productCategoryCode).toUpperCase();
        return StrUtil.blankToDefault(code, DEFAULT_PRODUCT_CATEGORY_CODE);
    }

    private String defaultRouteCode(String materialPurpose) {
        return TkGeminiPromptConfig.isLeadGeneration(materialPurpose)
                ? DEFAULT_LEAD_GENERATION_ROUTE_CODE : DEFAULT_ECOMMERCE_ROUTE_CODE;
    }

    private String resolveString(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value;
    }

    private String buildRouteNameKey(String materialPurpose, String productCategoryCode, String routeCode) {
        return StrUtil.format("{}|{}|{}", StrUtil.blankToDefault(materialPurpose, ""),
                StrUtil.blankToDefault(productCategoryCode, ""),
                StrUtil.blankToDefault(routeCode, ""));
    }

}
