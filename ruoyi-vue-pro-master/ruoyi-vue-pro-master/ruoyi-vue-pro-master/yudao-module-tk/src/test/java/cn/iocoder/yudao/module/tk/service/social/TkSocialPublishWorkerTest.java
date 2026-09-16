package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.tk.dal.dataobject.social.*;
import cn.iocoder.yudao.module.tk.dal.mysql.social.*;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialAccountService;
import cn.iocoder.yudao.module.tk.service.social.platform.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkSocialPublishWorkerTest {
    final TkSocialPublishDetailMapper details = mock(TkSocialPublishDetailMapper.class);
    final TkSocialPublishTaskMapper tasks = mock(TkSocialPublishTaskMapper.class);
    final TkSocialAccountMapper accounts = mock(TkSocialAccountMapper.class);
    final TkSocialMediaMapper media = mock(TkSocialMediaMapper.class);
    final TkSocialAccountService tokens = mock(TkSocialAccountService.class);
    final TkSocialMediaService urls = mock(TkSocialMediaService.class);
    final TkSocialPlatformClient platform = mock(TkSocialPlatformClient.class);
    final TkSocialPublishService publisher = mock(TkSocialPublishService.class);
    TkSocialPublishWorker worker;
    TkSocialPublishDetailDO detail;
    @BeforeEach void setup() {
        worker = new TkSocialPublishWorker(details,tasks,accounts,media,tokens,urls,platform,publisher);
        detail = new TkSocialPublishDetailDO(); detail.setId(1L); detail.setTenantId(2L); detail.setCompanyId(3L);
        detail.setSocialAccountId(4L); detail.setPublishTaskId(5L); detail.setPlatform("INSTAGRAM");
        detail.setStatus("PENDING"); detail.setPollCount(0); detail.setRetryCount(0);
        when(details.selectById(1L)).thenReturn(detail);
        when(details.update(isNull(),any(Wrapper.class))).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> update=call.getArgument(1);
            update.getSqlSegment();
            update.getParamNameValuePairs().values().stream().filter(v -> v instanceof String && v.toString().matches("[0-9a-f-]{36}"))
                    .findFirst().ifPresent(v -> {
                        detail.setLeaseToken(v.toString());
                        if ("PENDING".equals(detail.getStatus())) detail.setStatus("PROCESSING");
                    });
            return 1;
        });
        doAnswer(call -> { details.update(null,call.getArgument(2)); return null; })
                .when(publisher).recordOutcome(anyLong(),anyLong(),any());
        TkSocialPublishTaskDO task = new TkSocialPublishTaskDO(); task.setId(5L); task.setTenantId(2L);
        task.setCompanyId(3L); task.setMediaId(6L); task.setInstagramCaption("caption");
        when(tasks.selectById(5L)).thenReturn(task);
        TkSocialAccountDO account = new TkSocialAccountDO(); account.setId(4L); account.setTenantId(2L);
        account.setCompanyId(3L); account.setPlatform("INSTAGRAM"); account.setExternalAccountId("123");
        when(accounts.selectById(4L)).thenReturn(account); when(tokens.getValidToken(account)).thenReturn("secret");
        TkSocialMediaDO source = new TkSocialMediaDO(); source.setId(6L); source.setTenantId(2L);
        source.setCompanyId(3L); source.setMediaType("VIDEO"); source.setStatus("READY");
        when(media.selectById(6L)).thenReturn(source); when(urls.readUrl(source)).thenReturn("https://cdn.example/video.mp4");
    }
    @Test void leaseLoserDoesNotContactPlatform() {
        when(details.update(isNull(),any(Wrapper.class))).thenReturn(0);
        worker.process(1L,2L); verifyNoInteractions(platform,tokens);
    }
    @Test void mutationTimeoutBecomesUnknownAndIsNotResubmitted() {
        when(platform.advance(any(),any(),any(),any(),any(),any(),isNull(),isNull()))
                .thenThrow(new TkSocialPlatformException("TIMEOUT","safe",true,false,true));
        worker.process(1L,2L);
        assertTrue(lastUpdate().containsValue("UNKNOWN"));
        assertFalse(lastUpdate().containsValue("secret"));
    }
    @Test void checkpointIsPersistedAndResumed() {
        detail.setExternalContainerId("container"); detail.setPlatformStatus("IN_PROGRESS");
        TkSocialPlatformClient.PublishResult result = new TkSocialPlatformClient.PublishResult();
        result.setStatus("WAITING"); result.setContainerId("container"); result.setPlatformStatus("IN_PROGRESS");
        when(platform.advance("INSTAGRAM","123","secret","VIDEO","caption","https://cdn.example/video.mp4","container","IN_PROGRESS"))
                .thenReturn(result);
        worker.process(1L,2L);
        assertTrue(lastUpdate().containsValue("container")); assertTrue(lastUpdate().containsValue("PENDING"));
    }
    @Test void wrongTenantNeverPublishes() {
        detail.setTenantId(99L); worker.process(1L,2L); verifyNoInteractions(platform,tokens);
    }
    @Test void expiredLeaseIsUnknownNotRetryable() {
        worker.expire(detail);
        assertTrue(lastUpdate().containsValue("UNKNOWN")); verifyNoInteractions(platform);
    }
    @Test void reloadsCheckpointAfterWinningClaim() {
        TkSocialPublishDetailDO latest=new TkSocialPublishDetailDO();
        org.springframework.beans.BeanUtils.copyProperties(detail,latest);
        latest.setExternalContainerId("new-container"); latest.setPlatformStatus("IN_PROGRESS");
        when(details.selectById(1L)).thenReturn(detail).thenAnswer(call -> {
            latest.setLeaseToken(detail.getLeaseToken()); latest.setStatus("PROCESSING"); return latest;
        });
        TkSocialPlatformClient.PublishResult result=new TkSocialPlatformClient.PublishResult();
        result.setStatus("WAITING"); result.setContainerId("new-container"); result.setPlatformStatus("IN_PROGRESS");
        when(platform.advance("INSTAGRAM","123","secret","VIDEO","caption","https://cdn.example/video.mp4","new-container","IN_PROGRESS")).thenReturn(result);
        worker.process(1L,2L);
        verify(platform).advance("INSTAGRAM","123","secret","VIDEO","caption","https://cdn.example/video.mp4","new-container","IN_PROGRESS");
    }
    @Test void knownUnknownIsReconciledWithoutPublishingAgain() {
        detail.setStatus("UNKNOWN"); detail.setExternalContainerId("789");
        TkSocialPlatformClient.PublishResult result=new TkSocialPlatformClient.PublishResult();
        result.setStatus("SUCCESS"); result.setContainerId("789"); result.setPlatformStatus("PUBLISHED");
        when(platform.reconcile("INSTAGRAM","123","secret","789")).thenReturn(result);
        worker.process(1L,2L);
        assertTrue(lastUpdate().containsValue("SUCCESS"));
        verify(platform).reconcile("INSTAGRAM","123","secret","789");
        verify(platform,never()).advance(any(),any(),any(),any(),any(),any(),any(),any());
        verifyNoInteractions(urls,media);
    }
    @Test void reconciliationPermissionErrorCannotEnableMutationRetry() {
        detail.setStatus("UNKNOWN"); detail.setExternalContainerId("789");
        when(platform.reconcile(any(),any(),any(),any())).thenThrow(new TkSocialPlatformException("META_190","expired",false,true,false));
        worker.process(1L,2L);
        assertTrue(lastUpdate().containsValue("UNKNOWN"));
        assertFalse(lastUpdate().containsValue("REAUTH_REQUIRED"));
        verify(tokens).reportRejectedToken(4L,"secret");
        verify(platform,never()).advance(any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void unknownWithoutRemoteIdNeverCallsPlatform() {
        detail.setStatus("UNKNOWN"); worker.process(1L,2L); verifyNoInteractions(platform,tokens);
    }
    private java.util.Map<String,Object> lastUpdate() {
        ArgumentCaptor<Wrapper> captor=ArgumentCaptor.forClass(Wrapper.class);
        verify(details,atLeastOnce()).update(isNull(),captor.capture());
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> update =
                (com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?>)captor.getValue();
        update.getSqlSegment(); return update.getParamNameValuePairs();
    }
}
