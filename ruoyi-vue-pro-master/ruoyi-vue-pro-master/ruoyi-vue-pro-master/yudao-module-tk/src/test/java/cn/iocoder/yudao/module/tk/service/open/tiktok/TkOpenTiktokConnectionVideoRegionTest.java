package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokConnectionMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class TkOpenTiktokConnectionVideoRegionTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
    }

    @Test
    void usernameQueryAddsVideoRegionToAuthorizedConnections() {
        TkOpenTiktokConnectionMapper mapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenTiktokVideoRegionService regionService = mock(TkOpenTiktokVideoRegionService.class);
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .clientId("client-a").connectionId("conn-1").username("jorgenip3")
                .displayName("PixelErzähler").authStatus("AUTHORIZED").build();
        TkOpenTiktokAuthVO.VideoRegionResp region = new TkOpenTiktokAuthVO.VideoRegionResp();
        region.setStatus("AVAILABLE");
        region.setLocationCreated("DE");
        when(mapper.selectListByClient("client-a", null, "AUTHORIZED", "jorgenip3"))
                .thenReturn(Collections.singletonList(connection));
        when(regionService.lookup("jorgenip3")).thenReturn(region);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "connections"), "req-1");

        List<TkOpenTiktokAuthVO.ConnectionResp> result = service(mapper, regionService)
                .getConnections(null, null, "jorgenip3");

        assertEquals(1, result.size());
        assertEquals("DE", result.get(0).getVideoRegion().getLocationCreated());
        verify(regionService).lookup("jorgenip3");
    }

    @Test
    void existingListQueryNeverFetchesVideoRegion() {
        TkOpenTiktokConnectionMapper mapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenTiktokVideoRegionService regionService = mock(TkOpenTiktokVideoRegionService.class);
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .clientId("client-a").connectionId("conn-1").username("jorgenip3").build();
        when(mapper.selectListByClient("client-a", null, null, null))
                .thenReturn(Collections.singletonList(connection));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "connections"), "req-2");

        List<TkOpenTiktokAuthVO.ConnectionResp> result = service(mapper, regionService)
                .getConnections(null, null, null);

        assertEquals(1, result.size());
        assertNull(result.get(0).getVideoRegion());
        assertFalse(JsonUtils.toJsonString(result.get(0)).contains("videoRegion"));
        verifyNoInteractions(regionService);
    }

    @Test
    void regionFailureDoesNotHideTheAuthorizedConnection() {
        TkOpenTiktokConnectionMapper mapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenTiktokVideoRegionService regionService = mock(TkOpenTiktokVideoRegionService.class);
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .clientId("client-a").connectionId("conn-1").username("jorgenip3")
                .authStatus("AUTHORIZED").build();
        when(mapper.selectListByClient("client-a", null, "AUTHORIZED", "jorgenip3"))
                .thenReturn(Collections.singletonList(connection));
        when(regionService.lookup("jorgenip3")).thenThrow(new IllegalStateException("TikTok unavailable"));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client-a", "A", "connections"), "req-3");

        List<TkOpenTiktokAuthVO.ConnectionResp> result = service(mapper, regionService)
                .getConnections(null, null, "jorgenip3");

        assertEquals(1, result.size());
        assertEquals("UNAVAILABLE", result.get(0).getVideoRegion().getStatus());
    }

    private TkOpenTiktokAuthService service(TkOpenTiktokConnectionMapper mapper,
                                            TkOpenTiktokVideoRegionService regionService) {
        return new TkOpenTiktokAuthService(mock(TkOpenTiktokAuthSessionMapper.class), mapper,
                mock(TkOpenPublishPlatformRegistry.class), mock(TkOpenApiSecretCipher.class),
                mock(TkOpenApiCallbackService.class), regionService, "https://callback", "https://launch");
    }
}
