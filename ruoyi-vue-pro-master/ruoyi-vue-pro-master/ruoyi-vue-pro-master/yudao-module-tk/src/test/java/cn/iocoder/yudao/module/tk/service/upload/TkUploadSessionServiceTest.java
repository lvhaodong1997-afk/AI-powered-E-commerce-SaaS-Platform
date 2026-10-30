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
    @Test
    void lockAccessibleRechecksCompletedStateUnderRowLock() {
        TkUploadSessionMapper mapper = mock(TkUploadSessionMapper.class);
        TkDataScopeService scope = mock(TkDataScopeService.class);
        TkUploadSessionDO completed = new TkUploadSessionDO().setStatus("COMPLETED")
                .setExpiresAt(LocalDateTime.now().plusHours(1));
        when(mapper.selectByUploadIdForUpdate("upload-1")).thenReturn(completed);
        TkUploadSessionService service = new TkUploadSessionService();
        ReflectionTestUtils.setField(service, "sessionMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", scope);
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> service.lockAccessible("upload-1"));
        verify(mapper).selectByUploadIdForUpdate("upload-1");
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).selectByUploadId("upload-1");
    }

    @Test
    void expiryLosingAtomicClaimNeverDeletesCompletedSource() {
        TkUploadSessionMapper mapper = mock(TkUploadSessionMapper.class);
        TkOssObjectStorageService oss = mock(TkOssObjectStorageService.class);
        TkUploadSessionDO stale = new TkUploadSessionDO().setId(1L).setUploadId("upload-1")
                .setStatus("UPLOADING").setStorageMode("oss").setExpiresAt(LocalDateTime.now().minusHours(1));
        when(mapper.selectExpired(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Collections.singletonList(stale));
        when(mapper.claimExpired(eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        TkUploadSessionService service = new TkUploadSessionService();
        ReflectionTestUtils.setField(service, "sessionMapper", mapper);
        ReflectionTestUtils.setField(service, "ossObjectStorageService", oss);
        org.junit.jupiter.api.Assertions.assertEquals(0, service.expireSessions());
        verify(mapper).claimExpired(eq(1L), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verifyNoInteractions(oss);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).updateById(org.mockito.ArgumentMatchers.any(TkUploadSessionDO.class));
    }

    @Test
    void expiryWinningClaimDeletesOnlyClaimedSource() {
        TkUploadSessionMapper mapper = mock(TkUploadSessionMapper.class);
        TkOssObjectStorageService oss = mock(TkOssObjectStorageService.class);
        TkUploadSessionDO expired = new TkUploadSessionDO().setId(1L).setUploadId("upload-1")
                .setStatus("UPLOADING").setStorageMode("oss").setExpiresAt(LocalDateTime.now().minusHours(1));
        when(mapper.selectExpired(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.Collections.singletonList(expired));
        when(mapper.claimExpired(eq(1L), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(oss.isConfigured()).thenReturn(true);
        when(oss.buildTiktokObjectKey(expired)).thenReturn("immutable/key.mp4");
        TkUploadSessionService service = new TkUploadSessionService();
        ReflectionTestUtils.setField(service, "sessionMapper", mapper);
        ReflectionTestUtils.setField(service, "ossObjectStorageService", oss);
        org.junit.jupiter.api.Assertions.assertEquals(1, service.expireSessions());
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(mapper, oss);
        order.verify(mapper).claimExpired(eq(1L), org.mockito.ArgumentMatchers.any());
        order.verify(oss).isConfigured();
        order.verify(oss).buildTiktokObjectKey(expired);
        order.verify(oss).deleteObject("immutable/key.mp4");
    }}
