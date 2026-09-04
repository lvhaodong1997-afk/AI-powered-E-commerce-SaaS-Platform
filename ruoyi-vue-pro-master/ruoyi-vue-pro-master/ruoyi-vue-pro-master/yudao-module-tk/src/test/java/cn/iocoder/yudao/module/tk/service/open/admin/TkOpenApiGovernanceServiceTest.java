package cn.iocoder.yudao.module.tk.service.open.admin;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.controller.admin.openapi.vo.TkOpenApiGovernanceVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiRequestLogMapper;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackOperations;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TkOpenApiGovernanceServiceTest {

    @Test
    void shouldReplayExistingEventByOpaqueEventId() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiCallbackOperations callbackOperations = mock(TkOpenApiCallbackOperations.class);
        when(eventMapper.selectByEventId("evt_1")).thenReturn(
                TkOpenApiEventDO.builder().eventId("evt_1").clientId("client_b").status("FAILED")
                        .callbackUrl("https://callback.example.com/tiktok").build());
        TkOpenApiGovernanceService service = new TkOpenApiGovernanceService(eventMapper,
                mock(TkOpenApiRequestLogMapper.class), callbackOperations);

        service.replay("evt_1");

        verify(callbackOperations).replay("evt_1");
    }

    @Test
    void shouldRejectReplayWhileEventIsDeliveringOrCallbackIsMissing() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiCallbackOperations callbackOperations = mock(TkOpenApiCallbackOperations.class);
        TkOpenApiGovernanceService service = new TkOpenApiGovernanceService(eventMapper,
                mock(TkOpenApiRequestLogMapper.class), callbackOperations);
        when(eventMapper.selectByEventId("evt_active")).thenReturn(
                TkOpenApiEventDO.builder().eventId("evt_active").status("DELIVERING")
                        .callbackUrl("https://callback.example.com/tiktok").build());
        when(eventMapper.selectByEventId("evt_skipped")).thenReturn(
                TkOpenApiEventDO.builder().eventId("evt_skipped").status("SKIPPED").build());

        assertThrows(ServiceException.class, () -> service.replay("evt_active"));
        assertThrows(ServiceException.class, () -> service.replay("evt_skipped"));
        verifyNoInteractions(callbackOperations);
    }

    @Test
    void shouldReturnDailyUsageWithoutInternalIds() {
        TkOpenApiRequestLogMapper requestLogMapper = mock(TkOpenApiRequestLogMapper.class);
        TkOpenApiGovernanceVO.UsageResp row = new TkOpenApiGovernanceVO.UsageResp();
        row.setRequestDate(LocalDate.of(2026, 9, 1));
        row.setClientId("client_b");
        row.setRequestCount(12L);
        row.setSuccessCount(10L);
        row.setFailureCount(2L);
        row.setAverageDurationMs(85L);
        when(requestLogMapper.selectDailyUsage("client_b", LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 7))).thenReturn(Collections.singletonList(row));
        TkOpenApiGovernanceService service = new TkOpenApiGovernanceService(mock(TkOpenApiEventMapper.class),
                requestLogMapper, mock(TkOpenApiCallbackOperations.class));

        java.util.List<TkOpenApiGovernanceVO.UsageResp> result = service.getUsage("client_b",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));

        assertEquals(1, result.size());
        assertEquals(12L, result.get(0).getRequestCount());
        assertEquals(2L, result.get(0).getFailureCount());
    }
}
