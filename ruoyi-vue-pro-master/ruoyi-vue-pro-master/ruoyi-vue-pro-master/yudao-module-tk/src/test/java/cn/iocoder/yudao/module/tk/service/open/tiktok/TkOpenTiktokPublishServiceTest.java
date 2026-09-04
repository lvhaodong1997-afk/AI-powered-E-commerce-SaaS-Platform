package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiIdempotencyDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokConnectionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.service.open.api.TkOpenApiCallbackService;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformAdapter;
import cn.iocoder.yudao.module.tk.service.open.platform.TkOpenPublishPlatformRegistry;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

class TkOpenTiktokPublishServiceTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRejectMissingIdempotencyKey() {
        TkOpenTiktokPublishService service = newService();
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-1");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.create(request(), null));

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", error.getCode());
    }

    @Test
    void shouldReturnExistingTaskForSameIdempotencyHash() {
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenApiIdempotencyMapper idempotencyMapper = mock(TkOpenApiIdempotencyMapper.class);
        TkOpenTiktokPublishTaskDO task = TkOpenTiktokPublishTaskDO.builder().taskId("task_existing").status("PENDING").build();
        when(idempotencyMapper.selectByClientAndKey("client_b", "order-1"))
                .thenReturn(TkOpenApiIdempotencyDO.builder().clientId("client_b").idempotencyKey("order-1")
                        .requestHash(TkOpenTiktokPublishService.requestHash(request())).resourceId("task_existing")
                        .resourceType("PUBLISH_TASK").status("COMPLETED").build());
        when(taskMapper.selectByClientAndTaskId("client_b", "task_existing")).thenReturn(task);
        TkOpenTiktokPublishService service = newService(taskMapper, idempotencyMapper);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-2");

        TkOpenTiktokPublishVO.TaskResp response = service.create(request(), "order-1");

        assertEquals("task_existing", response.getTaskId());
        verify(taskMapper, never()).insert(any(TkOpenTiktokPublishTaskDO.class));
    }

    @Test
    void shouldRejectSameKeyWithDifferentRequest() {
        TkOpenApiIdempotencyMapper idempotencyMapper = mock(TkOpenApiIdempotencyMapper.class);
        when(idempotencyMapper.selectByClientAndKey("client_b", "order-1"))
                .thenReturn(TkOpenApiIdempotencyDO.builder().requestHash("different").resourceId("task_existing").build());
        TkOpenTiktokPublishService service = newService(mock(TkOpenTiktokPublishTaskMapper.class), idempotencyMapper);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-3");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.create(request(), "order-1"));

        assertEquals("IDEMPOTENCY_KEY_CONFLICT", error.getCode());
    }

    @Test
    void shouldHideTaskOwnedByAnotherClient() {
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishService service = newService(taskMapper, mock(TkOpenApiIdempotencyMapper.class));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-4");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.getTask("task_from_client_c"));

        assertEquals("PUBLISH_TASK_NOT_FOUND", error.getCode());
        verify(taskMapper).selectByClientAndTaskId("client_b", "task_from_client_c");
    }

    @Test
    void shouldPersistNewTaskBeforeReturning() {
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokMediaMapper mediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenApiIdempotencyMapper idempotencyMapper = mock(TkOpenApiIdempotencyMapper.class);
        when(mediaMapper.selectByClientAndMediaId("client_b", "media_1")).thenReturn(
                TkOpenTiktokMediaDO.builder().mediaId("media_1").clientId("client_b").status("READY").build());
        when(connectionMapper.selectListByClientAndIds(eq("client_b"), any())).thenReturn(Collections.singletonList(
                TkOpenTiktokConnectionDO.builder().connectionId("conn_1").clientId("client_b")
                        .displayName("account").authStatus("AUTHORIZED").build()));
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(taskMapper, detailMapper,
                mediaMapper, connectionMapper, idempotencyMapper, null, null,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-5");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        TkOpenTiktokPublishVO.TaskResp response = service.create(request(), "order-new");

        assertNotNull(response.getTaskId());
        assertEquals("PENDING", response.getStatus());
        InOrder order = inOrder(idempotencyMapper, taskMapper, detailMapper);
        order.verify(idempotencyMapper).insert(any(TkOpenApiIdempotencyDO.class));
        order.verify(taskMapper).insert(any(TkOpenTiktokPublishTaskDO.class));
        order.verify(detailMapper).insert(any(TkOpenTiktokPublishDetailDO.class));
        order.verify(idempotencyMapper).updateById(any(TkOpenApiIdempotencyDO.class));
    }

    @Test
    void shouldNotCreateOrphanTaskWhenAnotherRequestWinsIdempotencyReservation() {
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokMediaMapper mediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenApiIdempotencyMapper idempotencyMapper = mock(TkOpenApiIdempotencyMapper.class);
        TkOpenApiIdempotencyDO winner = TkOpenApiIdempotencyDO.builder()
                .clientId("client_b").idempotencyKey("order-race")
                .requestHash(TkOpenTiktokPublishService.requestHash(request()))
                .resourceType("PUBLISH_TASK").resourceId("task_winner").status("COMPLETED").build();
        when(idempotencyMapper.selectByClientAndKey("client_b", "order-race")).thenReturn(null);
        when(idempotencyMapper.selectByClientAndKeyForUpdate("client_b", "order-race")).thenReturn(winner);
        doThrow(new DuplicateKeyException("duplicate idempotency key"))
                .when(idempotencyMapper).insert(any(TkOpenApiIdempotencyDO.class));
        when(mediaMapper.selectByClientAndMediaId("client_b", "media_1")).thenReturn(
                TkOpenTiktokMediaDO.builder().mediaId("media_1").clientId("client_b").status("READY").build());
        when(connectionMapper.selectListByClientAndIds(eq("client_b"), any())).thenReturn(Collections.singletonList(
                TkOpenTiktokConnectionDO.builder().connectionId("conn_1").clientId("client_b")
                        .displayName("account").authStatus("AUTHORIZED").build()));
        when(taskMapper.selectByClientAndTaskIdForUpdate("client_b", "task_winner")).thenReturn(
                TkOpenTiktokPublishTaskDO.builder().taskId("task_winner").status("PENDING").build());
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(taskMapper, detailMapper,
                mediaMapper, connectionMapper, idempotencyMapper, null, null,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-race");

        TkOpenTiktokPublishVO.TaskResp response = service.create(request(), "order-race");

        assertEquals("task_winner", response.getTaskId());
        verify(taskMapper, never()).insert(any(TkOpenTiktokPublishTaskDO.class));
        verify(detailMapper, never()).insert(any(TkOpenTiktokPublishDetailDO.class));
    }

    @Test
    void shouldOnlyRetryFailedDetail() {
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        when(detailMapper.selectByClientAndDetailId("client_b", "detail_1")).thenReturn(
                TkOpenTiktokPublishDetailDO.builder().detailId("detail_1").clientId("client_b")
                        .status("PROCESSING").build());
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(
                mock(TkOpenTiktokPublishTaskMapper.class), detailMapper, mock(TkOpenTiktokMediaMapper.class),
                mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class), null, null,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "publish"), "req-6");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.retry("detail_1"));

        assertEquals("PUBLISH_RETRY_STATUS_INVALID", error.getCode());
        verify(detailMapper, never()).update(any(), any());
    }

    @Test
    void shouldSummarizeMixedResultsAsPartialSuccess() {
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokPublishTaskDO task = TkOpenTiktokPublishTaskDO.builder().taskId("task_1")
                .clientId("client_b").status("PROCESSING").build();
        when(taskMapper.selectByClientAndTaskId("client_b", "task_1")).thenReturn(task);
        when(detailMapper.selectListByClientAndTaskId("client_b", "task_1")).thenReturn(Arrays.asList(
                TkOpenTiktokPublishDetailDO.builder().status("SUCCESS").build(),
                TkOpenTiktokPublishDetailDO.builder().status("FAILED").build()));
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(taskMapper, detailMapper,
                mock(TkOpenTiktokMediaMapper.class), mock(TkOpenTiktokConnectionMapper.class),
                mock(TkOpenApiIdempotencyMapper.class), null, null,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);

        service.processTask("client_b", "task_1");

        ArgumentCaptor<TkOpenTiktokPublishTaskDO> captor = ArgumentCaptor.forClass(TkOpenTiktokPublishTaskDO.class);
        verify(taskMapper).updateById(captor.capture());
        assertEquals("PARTIAL_SUCCESS", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getSuccessCount());
        assertEquals(1, captor.getValue().getFailedCount());
        assertEquals(0, captor.getValue().getPendingCount());
    }

    @Test
    void shouldScanPersistedPendingDetailsLostFromTheExecutorQueue() {
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        when(detailMapper.selectStalePending(any(java.time.LocalDateTime.class), eq(100)))
                .thenReturn(Collections.emptyList());
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(
                mock(TkOpenTiktokPublishTaskMapper.class), detailMapper, mock(TkOpenTiktokMediaMapper.class),
                mock(TkOpenTiktokConnectionMapper.class), mock(TkOpenApiIdempotencyMapper.class), null, null,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);

        try {
            assertEquals(0, service.resumeStalePending(100));
            verify(detailMapper).selectStalePending(any(java.time.LocalDateTime.class), eq(100));
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldFailStaleProcessingDetailWithoutPublishIdInsteadOfLeavingItStuck() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishDetailDO.class);
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenApiCallbackService callbackService = mock(TkOpenApiCallbackService.class);
        TkOpenTiktokPublishDetailDO detail = TkOpenTiktokPublishDetailDO.builder()
                .id(1L).detailId("detail_interrupted").taskId("task_1").clientId("client_b")
                .status("PROCESSING").tiktokStatus("LOCAL_PROCESSING").build();
        when(detailMapper.selectStaleInitializing(any(java.time.LocalDateTime.class), eq(100)))
                .thenReturn(Collections.singletonList(detail));
        when(detailMapper.update(isNull(), any())).thenReturn(1);
        when(taskMapper.selectByClientAndTaskId("client_b", "task_1")).thenReturn(
                TkOpenTiktokPublishTaskDO.builder().taskId("task_1").clientId("client_b").build());
        when(detailMapper.selectListByClientAndTaskId("client_b", "task_1"))
                .thenReturn(Collections.singletonList(detail));
        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(taskMapper, detailMapper,
                mock(TkOpenTiktokMediaMapper.class), mock(TkOpenTiktokConnectionMapper.class),
                mock(TkOpenApiIdempotencyMapper.class), null, callbackService,
                mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);

        try {
            assertEquals(1, service.syncStale(100));

            assertEquals("FAILED", detail.getStatus());
            assertEquals("RECOVERY_REQUIRED", detail.getTiktokStatus());
            verify(callbackService).enqueue(eq("client_b"), eq("publish.failed"),
                    eq("PUBLISH_DETAIL"), eq("detail_interrupted"), any());
        } finally {
            service.destroy();
        }
    }

    @Test
    void shouldCompleteInboxSubmissionWhenTikTokDoesNotReturnPublishId() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TkOpenTiktokPublishDetailDO.class);
        TkOpenTiktokPublishTaskMapper taskMapper = mock(TkOpenTiktokPublishTaskMapper.class);
        TkOpenTiktokPublishDetailMapper detailMapper = mock(TkOpenTiktokPublishDetailMapper.class);
        TkOpenTiktokMediaMapper mediaMapper = mock(TkOpenTiktokMediaMapper.class);
        TkOpenTiktokConnectionMapper connectionMapper = mock(TkOpenTiktokConnectionMapper.class);
        TkOpenPublishPlatformRegistry platformRegistry = mock(TkOpenPublishPlatformRegistry.class);
        TkOpenPublishPlatformAdapter adapter = mock(TkOpenPublishPlatformAdapter.class);
        TkOpenApiCallbackService callbackService = mock(TkOpenApiCallbackService.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);

        TkOpenTiktokPublishTaskDO task = TkOpenTiktokPublishTaskDO.builder()
                .taskId("task_inbox").clientId("client_b").mediaId("media_1")
                .postMode("UPLOAD_TO_INBOX").privacyLevel("PUBLIC_TO_EVERYONE")
                .externalRequestId("order-inbox").build();
        TkOpenTiktokPublishDetailDO detail = TkOpenTiktokPublishDetailDO.builder()
                .id(1L).detailId("detail_inbox").taskId("task_inbox").clientId("client_b")
                .connectionId("conn_1").status("PENDING").retryCount(0).build();
        TkOpenTiktokConnectionDO connection = TkOpenTiktokConnectionDO.builder()
                .id(2L).connectionId("conn_1").clientId("client_b").authStatus("AUTHORIZED")
                .accessTokenCipher("access-cipher").accessTokenExpireTime(LocalDateTime.now().plusHours(1))
                .build();
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder()
                .id(3L).mediaId("media_1").clientId("client_b").fileName("video.mp4")
                .fileUrl("https://cdn.example/video.mp4").contentType("video/mp4").fileSize(10L)
                .status("READY").build();

        when(taskMapper.selectByClientAndTaskId("client_b", "task_inbox")).thenReturn(task);
        when(detailMapper.selectListByClientAndTaskId("client_b", "task_inbox"))
                .thenReturn(Collections.singletonList(detail));
        when(connectionMapper.selectByClientAndConnectionId("client_b", "conn_1")).thenReturn(connection);
        when(mediaMapper.selectByClientAndMediaId("client_b", "media_1")).thenReturn(media);
        when(detailMapper.update(isNull(), any())).thenReturn(1);
        when(secretCipher.decrypt("access-cipher")).thenReturn("access-token");
        when(platformRegistry.getRequired("TIKTOK")).thenReturn(adapter);
        when(adapter.verifiedPullDomain()).thenReturn("cdn.example");
        when(adapter.queryCreatorInfo("access-token")).thenReturn(
                new TkOpenPublishPlatformAdapter.CreatorCapabilities(true, null,
                        Collections.singletonList("PUBLIC_TO_EVERYONE"), false, false, false, null));
        when(adapter.initVideoPost(eq("access-token"), eq("UPLOAD_TO_INBOX"), any()))
                .thenReturn(new TkOpenPublishPlatformAdapter.PublishInitResult(true, null, null, null, null));

        TkOpenTiktokPublishService service = new TkOpenTiktokPublishService(taskMapper, detailMapper,
                mediaMapper, connectionMapper, mock(TkOpenApiIdempotencyMapper.class), platformRegistry,
                callbackService, secretCipher, mock(cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService.class));

        try {
            service.processTask("client_b", "task_inbox");

            assertEquals("SUCCESS", detail.getStatus());
            assertEquals("SEND_TO_USER_INBOX", detail.getTiktokStatus());
            verify(callbackService).enqueue(eq("client_b"), eq("publish.success"),
                    eq("PUBLISH_DETAIL"), eq("detail_inbox"), any());
        } finally {
            service.destroy();
        }
    }

    private TkOpenTiktokPublishService newService() {
        return newService(mock(TkOpenTiktokPublishTaskMapper.class), mock(TkOpenApiIdempotencyMapper.class));
    }

    private TkOpenTiktokPublishService newService(TkOpenTiktokPublishTaskMapper taskMapper,
                                                   TkOpenApiIdempotencyMapper idempotencyMapper) {
        return new TkOpenTiktokPublishService(taskMapper, mock(TkOpenTiktokPublishDetailMapper.class),
                mock(TkOpenTiktokMediaMapper.class), mock(TkOpenTiktokConnectionMapper.class), idempotencyMapper,
                null, null, mock(cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher.class), null);
    }

    static TkOpenTiktokPublishVO.TaskCreateReq request() {
        TkOpenTiktokPublishVO.TaskCreateReq request = new TkOpenTiktokPublishVO.TaskCreateReq();
        request.setMediaId("media_1");
        request.setConnectionIds(Collections.singletonList("conn_1"));
        request.setPostMode("DIRECT_POST");
        request.setPrivacyLevel("PUBLIC_TO_EVERYONE");
        request.setCaption("caption");
        return request;
    }
}
