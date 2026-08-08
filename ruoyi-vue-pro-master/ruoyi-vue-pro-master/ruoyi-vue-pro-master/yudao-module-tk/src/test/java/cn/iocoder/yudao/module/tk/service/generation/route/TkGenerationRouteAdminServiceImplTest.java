package cn.iocoder.yudao.module.tk.service.generation.route;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteHistoryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationRouteHistoryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationRouteMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteStatisticsRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.route.vo.TkGenerationRouteUpdateReqVO;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkGenerationRouteAdminServiceImplTest {

    @Test
    void updateRouteStoresHistoryAndBumpsVersion() {
        TkGenerationRouteServiceImpl service = new TkGenerationRouteServiceImpl();
        TkGenerationRouteMapper routeMapper = mock(TkGenerationRouteMapper.class);
        TkGenerationRouteHistoryMapper historyMapper = mock(TkGenerationRouteHistoryMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "routeMapper", routeMapper);
        ReflectionTestUtils.setField(service, "routeHistoryMapper", historyMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(1L, null, "PLATFORM_ADMIN", null));

        TkGenerationRouteDO existing = TkGenerationRouteDO.builder()
                .id(11L)
                .materialPurpose("ECOMMERCE")
                .productCategoryCode("02")
                .routeCode("ECOM_BEAUTY")
                .routeName("Beauty Route")
                .routeConfig("{\"segments\":[\"S1\"]}")
                .routeVersion(3)
                .trafficWeight(60)
                .abGroup("A")
                .enabled(true)
                .lastPublishTime(LocalDateTime.of(2026, 8, 1, 10, 0))
                .build();
        existing.setTenantId(0L);
        when(routeMapper.selectById(11L)).thenReturn(existing);

        TkGenerationRouteUpdateReqVO updateReqVO = new TkGenerationRouteUpdateReqVO();
        updateReqVO.setId(11L);
        updateReqVO.setRouteName("Beauty Route V2");
        updateReqVO.setRouteConfig("{\"segments\":[\"S1\",\"S3\"]}");
        updateReqVO.setTrafficWeight(80);
        updateReqVO.setAbGroup("B");
        updateReqVO.setEnabled(false);
        updateReqVO.setLastPublishTime(LocalDateTime.of(2026, 8, 2, 12, 30));
        updateReqVO.setRemark("tuned");

        service.updateRoute(updateReqVO);

        verify(historyMapper).insert(any(TkGenerationRouteHistoryDO.class));
        verify(routeMapper).updateById(any(TkGenerationRouteDO.class));
        assertEquals(4, existing.getRouteVersion());
    }

    @Test
    void getRouteStatisticsReturnsAggregatedRowsFromTaskMapper() {
        TkGenerationRouteServiceImpl service = new TkGenerationRouteServiceImpl();
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        TkGenerationRouteMapper routeMapper = mock(TkGenerationRouteMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "routeMapper", routeMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(1L, null, "PLATFORM_ADMIN", null));

        TkGenerationRouteStatisticsRespVO item = new TkGenerationRouteStatisticsRespVO();
        item.setRouteCode("ECOM_BEAUTY");
        item.setProductCategoryCode("02");
        item.setGenerationCount(12L);
        item.setSuccessCount(9L);
        item.setFailedCount(2L);
        item.setRunningCount(1L);
        item.setSuccessRate(75.0);
        item.setAverageDurationSeconds(88L);
        item.setRouteName("ECOM_BEAUTY");
        when(taskMapper.selectRouteStatistics(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(item));
        TkGenerationRouteDO route = TkGenerationRouteDO.builder()
                .materialPurpose("ECOMMERCE")
                .productCategoryCode("02")
                .routeCode("ECOM_BEAUTY")
                .routeName("Beauty Route")
                .build();
        when(routeMapper.selectList(any())).thenReturn(List.of(route));

        TkGenerationRouteStatisticsReqVO reqVO = new TkGenerationRouteStatisticsReqVO();
        reqVO.setMaterialPurpose("ECOMMERCE");

        List<TkGenerationRouteStatisticsRespVO> result = service.getRouteStatistics(reqVO);

        assertEquals("ECOM_BEAUTY", result.get(0).getRouteName());
        assertEquals(Arrays.asList(item), result);
    }
}
