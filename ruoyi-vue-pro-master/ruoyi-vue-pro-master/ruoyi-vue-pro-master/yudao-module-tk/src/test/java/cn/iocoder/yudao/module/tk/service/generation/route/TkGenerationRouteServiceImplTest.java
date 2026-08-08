package cn.iocoder.yudao.module.tk.service.generation.route;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationRouteDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationRouteMapper;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiPromptConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkGenerationRouteServiceImplTest {

    @Test
    void resolveRouteUsesCategoryRouteBeforeDefaultRoute() {
        TkGenerationRouteServiceImpl service = new TkGenerationRouteServiceImpl();
        TkGenerationRouteMapper routeMapper = mock(TkGenerationRouteMapper.class);
        ReflectionTestUtils.setField(service, "routeMapper", routeMapper);

        TkGenerationRouteDO categoryRoute = TkGenerationRouteDO.builder()
                .routeCode("ECOM_BEAUTY")
                .routeName("Beauty Route")
                .routeConfig("{\"segments\":[\"S1\",\"S3\",\"S5\"]}")
                .build();
        when(routeMapper.selectEnabledRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "02"))
                .thenReturn(categoryRoute);

        TkGenerationRoute route = service.resolveRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "02");

        assertEquals("02", route.getProductCategoryCode());
        assertEquals("ECOM_BEAUTY", route.getRouteCode());
        assertEquals("{\"segments\":[\"S1\",\"S3\",\"S5\"]}", route.getRouteConfig());
    }

    @Test
    void resolveRouteFallsBackToDefaultRouteWhenCategoryRouteMissing() {
        TkGenerationRouteServiceImpl service = new TkGenerationRouteServiceImpl();
        TkGenerationRouteMapper routeMapper = mock(TkGenerationRouteMapper.class);
        ReflectionTestUtils.setField(service, "routeMapper", routeMapper);

        TkGenerationRouteDO defaultRoute = TkGenerationRouteDO.builder()
                .routeCode("ECOM_DEFAULT")
                .routeName("Default Route")
                .routeConfig("{}")
                .build();
        when(routeMapper.selectEnabledRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "01"))
                .thenReturn(null);
        when(routeMapper.selectEnabledRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE,
                TkGenerationRouteService.DEFAULT_PRODUCT_CATEGORY_CODE))
                .thenReturn(defaultRoute);

        TkGenerationRoute route = service.resolveRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "01");

        assertEquals("01", route.getProductCategoryCode());
        assertEquals("ECOM_DEFAULT", route.getRouteCode());
        assertEquals("{}", route.getRouteConfig());
    }

    @Test
    void resolveRouteFallsBackToGlobalTenantRouteWhenTenantRouteMissing() {
        TkGenerationRouteServiceImpl service = new TkGenerationRouteServiceImpl();
        TkGenerationRouteMapper routeMapper = mock(TkGenerationRouteMapper.class);
        ReflectionTestUtils.setField(service, "routeMapper", routeMapper);

        TkGenerationRouteDO globalRoute = TkGenerationRouteDO.builder()
                .routeCode("ECOM_APPAREL_GLOBAL")
                .routeName("Global Apparel Route")
                .routeConfig("{\"source\":\"global\"}")
                .build();
        when(routeMapper.selectEnabledRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "01"))
                .thenReturn(null);
        when(routeMapper.selectEnabledRoute(0L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "01"))
                .thenReturn(globalRoute);

        TkGenerationRoute route = service.resolveRoute(8L, TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE, "01");

        assertEquals("01", route.getProductCategoryCode());
        assertEquals("ECOM_APPAREL_GLOBAL", route.getRouteCode());
        assertEquals("{\"source\":\"global\"}", route.getRouteConfig());
    }
}
