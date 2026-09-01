package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkUploadSessionServiceTest {

    @AfterEach
    void resetTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void validatesSessionOutsideTenantFilterAndChecksTkReadableScope() {
        TkUploadSessionMapper sessionMapper = mock(TkUploadSessionMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkUserScope scope = new TkUserScope(7L, 100L, "USER", 200L);
        TkUploadSessionDO session = new TkUploadSessionDO()
                .setUploadId("upload-1")
                .setCompanyId(200L)
                .setStatus("UPLOADING")
                .setExpiresAt(LocalDateTime.now().plusMinutes(10));
        session.setTenantId(100L);
        session.setCreator("7");
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        doAnswer(invocation -> {
            assertTrue(TenantContextHolder.isIgnore());
            return session;
        }).when(sessionMapper).selectByUploadId("upload-1");

        TkUploadSessionService service = new TkUploadSessionService();
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());

        assertSame(session, service.validateAccessible("upload-1"));
        verify(dataScopeService).validateReadable(eq(100L), eq(200L), eq("7"));
    }
}
