package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialMediaInspectJobTest {
    private final TkSocialMediaMapper mapper = mock(TkSocialMediaMapper.class);
    private final TkSocialMediaService service = mock(TkSocialMediaService.class);

    @Test void failedClaimDoesNotDownloadVideo() {
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) {
            job.process(candidate());
            verifyNoInteractions(service);
        }
    }
    @Test void expiredLeaseCanBeClaimedAndReprobedWithinCorrectTenant() {
        TkSocialMediaDO media = candidate();
        media.setMetadataStatus("INSPECTING"); media.setInspectionLeaseUntil(LocalDateTime.now().minusMinutes(1));
        when(mapper.claimInspection(eq(1L), eq(100L), eq(200L), anyString(), any(), any(), eq(3))).thenAnswer(call -> {
            assertEquals(100L, TenantContextHolder.getTenantId()); assertFalse(TenantContextHolder.isIgnore());
            media.setInspectionLeaseToken(call.getArgument(3)); media.setInspectionAttempts(2); return 1;
        });
        when(mapper.selectById(1L)).thenReturn(media);
        when(mapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        doAnswer(call -> { media.setMetadataStatus("VERIFIED"); media.setStatus("READY"); return null; })
                .when(service).inspectOwnedSource(media);
        TenantContextHolder.setTenantId(999L);
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) { job.process(media); }
        assertEquals(999L, TenantContextHolder.getTenantId()); TenantContextHolder.clear();
        verify(service).inspectOwnedSource(media);
        verify(service, never()).deletePublishCopy(any());
    }
    @Test void staleWorkerCannotKeepItsPublishCopy() {
        TkSocialMediaDO media = candidate();
        when(mapper.claimInspection(eq(1L), eq(100L), eq(200L), anyString(), any(), any(), eq(3))).thenAnswer(call -> {
            media.setInspectionLeaseToken(call.getArgument(3)); media.setMetadataStatus("INSPECTING"); return 1;
        });
        when(mapper.selectById(1L)).thenReturn(media);
        try (TkSocialMediaInspectJob job = new TkSocialMediaInspectJob(mapper, service)) { job.process(media); }
        verify(service).deletePublishCopy(media);
    }
    private TkSocialMediaDO candidate() {
        TkSocialMediaDO media = new TkSocialMediaDO(); media.setId(1L); media.setTenantId(100L); media.setCompanyId(200L);
        media.setCreator("7"); media.setMediaType("VIDEO"); media.setStatus("PROCESSING"); media.setMetadataStatus("PENDING");
        media.setInspectionAttempts(0); return media;
    }
}
