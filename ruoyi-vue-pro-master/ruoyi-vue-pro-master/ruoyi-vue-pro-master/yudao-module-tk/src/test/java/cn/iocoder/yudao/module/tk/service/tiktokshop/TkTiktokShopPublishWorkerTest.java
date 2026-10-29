package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import cn.iocoder.yudao.module.tk.framework.config.TkTiktokShopProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TkTiktokShopPublishWorkerTest {
    TkTiktokShopPublishTaskMapper tasks=mock(TkTiktokShopPublishTaskMapper.class);TkTiktokShopPublishAttemptMapper attempts=mock(TkTiktokShopPublishAttemptMapper.class);
    TkTiktokShopTokenService tokens=mock(TkTiktokShopTokenService.class);TkTiktokShopMediaService media=mock(TkTiktokShopMediaService.class);TkTiktokShopUploadService upload=mock(TkTiktokShopUploadService.class);TkTiktokShopWorkflowClient wire=mock(TkTiktokShopWorkflowClient.class);
    PlatformTransactionManager tx=mock(PlatformTransactionManager.class);TkTiktokShopPublishTaskDO t=new TkTiktokShopPublishTaskDO();
    TkTiktokShopPublishWorker worker(){t.setId(1L);t.setTenantId(2L);t.setCompanyId(3L);t.setAccountId(4L);t.setSourcePath("private");t.setSourceFingerprint("hash");t.setFileSize(10L);t.setVersion(1);t.setConfirmedVersion(1);t.setConfirmedBy(7L);t.setConfirmedAt(java.time.LocalDateTime.now());t.setPollCount(0);t.setStage("SUBMITTING");t.setPrecheckResult("{\"passed\":true}");
        when(tasks.claim(eq(1L),eq(2L),anyString(),anyString(),any(),any())).thenAnswer(i->{t.setOwnerToken(i.getArgument(3));t.setLeaseExpireTime(i.getArgument(5));return 1;});when(tasks.findTask(1L,2L)).thenReturn(t);when(tasks.saveFenced(any(),anyString(),any(),anyBoolean())).thenReturn(1);when(attempts.updateFenced(any(),anyString(),any())).thenReturn(1);when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());when(tokens.getAccessToken(2L,3L,4L)).thenReturn(new TkTiktokShopTokenService.AccessToken("fake",1));return new TkTiktokShopPublishWorker(tasks,attempts,tokens,media,upload,wire,new ObjectMapper(),tx);
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void unknownPublishIsNeverAutomaticallySentAgain(){TkTiktokShopPublishWorker w=worker();when(wire.publish(any(),anyString())).thenThrow(new TkTiktokShopApiException("TRANSPORT_ERROR",null));w.runOne(t);assertEquals("RECOVERY_REQUIRED",t.getStage());w.runOne(t);verify(wire,times(1)).publish(any(),anyString());}
    @Test void crashedSendingNeverPostsAndKnownVideoOnlyPolls(){TkTiktokShopPublishWorker w=worker();t.setStage("SENDING");w.runOne(t);assertEquals("RECOVERY_REQUIRED",t.getStage());verify(wire,never()).publish(any(),anyString());t.setVideoId("known");when(wire.status("known","fake")).thenReturn("SUCCESS");w.runOne(t);assertEquals("SUCCESS",t.getStage());verify(wire,never()).publish(any(),anyString());}
    @Test void precheckPassOnlyWaitsForUserAndQualityAdvisoryIsAllowed(){TkTiktokShopPublishWorker w=worker();t.setStage("PRECHECKING");t.setPrecheckTaskId("pc");when(wire.check("pc","fake")).thenReturn(new TkTiktokShopWorkflowClient.Check("SUCCESS","FAIL",true));w.runOne(t);assertEquals("AWAITING_CONFIRMATION",t.getStage());verify(wire,never()).publish(any(),anyString());}
    @Test void failedViolationBlocksAndLostFenceCannotPost(){TkTiktokShopPublishWorker w=worker();t.setStage("PRECHECKING");t.setPrecheckTaskId("pc");when(wire.check("pc","fake")).thenReturn(new TkTiktokShopWorkflowClient.Check("FAIL","SUCCESS",true));w.runOne(t);assertEquals("FAILED",t.getStage());t.setStage("SUBMITTING");when(tasks.saveFenced(any(),anyString(),any(),eq(false))).thenReturn(0);w.runOne(t);verify(wire,never()).publish(any(),anyString());}
    @Test void http5xxAndMalformedPublishBothRequireRecovery(){for(String code:new String[]{"HTTP_ERROR","INVALID_RESPONSE"}){reset(tasks,attempts,tokens,wire);TkTiktokShopPublishWorker w=worker();when(wire.publish(any(),anyString())).thenThrow(new TkTiktokShopApiException(code,"safe-id"));w.runOne(t);assertEquals("RECOVERY_REQUIRED",t.getStage());w.runOne(t);verify(wire,times(1)).publish(any(),anyString());}}
    @Test void finalPollingResultAlsoUpdatesTheFencedAttemptAudit(){TkTiktokShopPublishWorker w=worker();t.setStage("PROCESSING");t.setVideoId("known");when(wire.status("known","fake")).thenReturn("SUCCESS");w.runOne(t);verify(attempts).updateFenced(argThat(a->"SUCCESS".equals(a.getStage())&&"known".equals(a.getVideoId())),anyString(),any());}
}
