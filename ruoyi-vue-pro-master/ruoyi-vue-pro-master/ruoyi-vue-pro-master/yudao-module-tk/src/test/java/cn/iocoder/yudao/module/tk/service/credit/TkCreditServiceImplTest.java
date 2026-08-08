package cn.iocoder.yudao.module.tk.service.credit;

import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkCreditLogDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTenantCreditAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkCreditLogMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTenantCreditAccountMapper;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.log.TkBusinessLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkCreditServiceImplTest {

    @Test
    void freezeForReferenceAnalysisUsesOneCreditWhenConfigMissing() {
        TkCreditServiceImpl service = createService();

        service.freezeForReferenceAnalysis(8L);

        ArgumentCaptor<TkCreditLogDO> captor = ArgumentCaptor.forClass(TkCreditLogDO.class);
        verify((TkCreditLogMapper) ReflectionTestUtils.getField(service, "creditLogMapper")).insert(captor.capture());
        assertEquals(1L, captor.getValue().getCredits());
    }

    @Test
    void freezeForGenerationTaskUsesOneCreditWhenConfigMissing() {
        TkCreditServiceImpl service = createService();

        service.freezeForGenerationTask(8L);

        ArgumentCaptor<TkCreditLogDO> captor = ArgumentCaptor.forClass(TkCreditLogDO.class);
        verify((TkCreditLogMapper) ReflectionTestUtils.getField(service, "creditLogMapper")).insert(captor.capture());
        assertEquals(1L, captor.getValue().getCredits());
    }

    private TkCreditServiceImpl createService() {
        TkCreditServiceImpl service = new TkCreditServiceImpl();
        TkTenantCreditAccountMapper creditAccountMapper = mock(TkTenantCreditAccountMapper.class);
        TkCreditLogMapper creditLogMapper = mock(TkCreditLogMapper.class);
        TkApiKeyConfigService apiKeyConfigService = mock(TkApiKeyConfigService.class);
        TenantService tenantService = mock(TenantService.class);
        TkBusinessLogService businessLogService = mock(TkBusinessLogService.class);

        TenantDO tenant = new TenantDO();
        tenant.setAccountCount(100);
        TkTenantCreditAccountDO account = TkTenantCreditAccountDO.builder()
                .totalCredits(100L)
                .remainingCredits(99L)
                .frozenCredits(1L)
                .warningThreshold(10L)
                .build();
        account.setTenantId(8L);

        when(tenantService.getTenant(8L)).thenReturn(tenant);
        when(creditAccountMapper.selectByTenantId(8L)).thenReturn(account);
        when(creditAccountMapper.freezeCredits(any(), any())).thenReturn(1);

        ReflectionTestUtils.setField(service, "creditAccountMapper", creditAccountMapper);
        ReflectionTestUtils.setField(service, "creditLogMapper", creditLogMapper);
        ReflectionTestUtils.setField(service, "apiKeyConfigService", apiKeyConfigService);
        ReflectionTestUtils.setField(service, "tenantService", tenantService);
        ReflectionTestUtils.setField(service, "businessLogService", businessLogService);
        return service;
    }
}
