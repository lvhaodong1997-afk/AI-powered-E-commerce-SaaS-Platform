package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractSyncRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkOpenVideoTranscriptTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkOpenVideoTranscriptTaskMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkOpenVideoTranscriptExtractServiceImplTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void createExtractTaskStoresCurrentTenantAndCompany() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope())
                .thenReturn(new TkUserScope(7L, 100L, "TENANT_ADMIN", 200L));
        AtomicReference<TkOpenVideoTranscriptTaskDO> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(99L);
            inserted.set(task);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));

        service(mapper, dataScopeService, new NoopExecutorService()).createExtractTask(request());

        assertNotNull(inserted.get());
        assertEquals(100L, inserted.get().getTenantId());
        assertEquals(200L, inserted.get().getCompanyId());
    }

    @Test
    void getExtractTaskValidatesReadableOwnershipBeforeMapping() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .id(105L)
                .status("SUCCESS")
                .segmentsJson("[]")
                .wordsJson("[]")
                .build();
        task.setTenantId(100L);
        task.setCompanyId(200L);
        task.setCreator("7");
        when(mapper.selectById(105L)).thenReturn(task);

        service(mapper, dataScopeService, new NoopExecutorService()).getExtractTask(105L);

        verify(dataScopeService).validateReadable(100L, 200L, "7");
    }

    @Test
    void createExtractTaskRunsAsyncWorkInCapturedTenantAndRestoresWorkerContext() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope())
                .thenReturn(new TkUserScope(7L, 100L, "TENANT_ADMIN", 200L));
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(106L);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));
        AtomicReference<Long> tenantDuringTask = new AtomicReference<>();
        AtomicReference<Boolean> ignoreDuringTask = new AtomicReference<>();
        when(mapper.selectById(106L)).thenAnswer(invocation -> {
            tenantDuringTask.set(TenantContextHolder.getTenantId());
            ignoreDuringTask.set(TenantContextHolder.isIgnore());
            return null;
        });
        CapturingExecutorService executor = new CapturingExecutorService();

        service(mapper, dataScopeService, executor).createExtractTask(request());
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        executor.runCapturedTask();

        assertEquals(100L, tenantDuringTask.get());
        assertFalse(ignoreDuringTask.get());
        assertEquals(999L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void createExtractTaskDefaultsLanguageToSimplifiedChinese() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        AtomicReference<TkOpenVideoTranscriptTaskDO> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(100L);
            inserted.set(task);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));

        TkOpenVideoTranscriptExtractCreateReqVO request = new TkOpenVideoTranscriptExtractCreateReqVO();
        request.setSourceUrl("https://example.com/video");

        service(mapper).createExtractTask(request);

        assertEquals("zh-CN", inserted.get().getTargetLanguage());
    }

    @Test
    void getExtractTaskNormalizesStoredTranscriptWithoutChangingTimeline() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        when(mapper.selectById(104L)).thenReturn(TkOpenVideoTranscriptTaskDO.builder()
                .id(104L)
                .status("SUCCESS")
                .targetLanguage("zh-CN")
                .transcriptText("B L G 首 發")
                .segmentsJson("[{\"text\":\"團隊 回歸\",\"start\":0.1,\"end\":1.2}]")
                .wordsJson("[{\"text\":\"回 歸\",\"start\":0.1,\"end\":0.8}]")
                .build());

        TkOpenVideoTranscriptExtractRespVO result = service(mapper).getExtractTask(104L);

        assertEquals("BLG首发", result.getTranscriptText());
        assertEquals("团队回归", result.getSegments().get(0).get("text"));
        assertEquals(0.1D, result.getSegments().get(0).get("start"));
        assertEquals("回归", result.getWords().get(0).get("text"));
        assertEquals(0.8D, result.getWords().get(0).get("end"));
    }

    @Test
    void extractAndWaitReturnsSuccessResultWithoutTaskId() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(101L);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));
        when(mapper.selectById(101L)).thenReturn(TkOpenVideoTranscriptTaskDO.builder()
                .id(101L)
                .sourceUrl("https://example.com/video")
                .targetLanguage("zh")
                .status("SUCCESS")
                .transcriptText("hello world")
                .segmentsJson("[]")
                .wordsJson("[]")
                .asrProvider("FASTER_WHISPER")
                .asrModel("small")
                .build());

        TkOpenVideoTranscriptExtractSyncRespVO result = service(mapper).extractAndWait(request());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("hello world", result.getTranscriptText());
        assertFalse(Arrays.stream(TkOpenVideoTranscriptExtractSyncRespVO.class.getDeclaredFields())
                .map(Field::getName)
                .anyMatch("taskId"::equals));
    }

    @Test
    void extractAndWaitReturnsFailedWhenTaskFails() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(102L);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));
        when(mapper.selectById(102L)).thenReturn(TkOpenVideoTranscriptTaskDO.builder()
                .id(102L)
                .sourceUrl("https://example.com/video")
                .status("FAILED")
                .failReason("download failed")
                .build());

        TkOpenVideoTranscriptExtractSyncRespVO result = service(mapper).extractAndWait(request());

        assertEquals("FAILED", result.getStatus());
        assertEquals("download failed", result.getFailReason());
    }

    @Test
    void extractAndWaitConvertsProcessingTimeoutToFailed() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(103L);
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));
        when(mapper.selectById(103L)).thenReturn(TkOpenVideoTranscriptTaskDO.builder()
                .id(103L)
                .sourceUrl("https://example.com/video")
                .status("PROCESSING")
                .build());

        TkOpenVideoTranscriptExtractSyncRespVO result = service(mapper).extractAndWait(request());

        assertEquals("FAILED", result.getStatus());
        assertEquals("视频文案提取超时，请稍后重试", result.getFailReason());
    }

    @Test
    void resolveAsrModelUsesProjectLocalModelDirectory() throws Exception {
        java.nio.file.Path modelRoot = Files.createTempDirectory("tk-asr-models");
        Files.createDirectory(modelRoot.resolve("small"));

        TkGenerationProperties.Asr asr = new TkGenerationProperties.Asr();
        asr.setModelCacheDir(modelRoot.toString());

        String resolved = service(mock(TkOpenVideoTranscriptTaskMapper.class))
                .resolveAsrModel(asr, "small");

        assertEquals(modelRoot.resolve("small").toFile().getAbsolutePath(), resolved);
    }

    @Test
    void resolveAsrModelFailsBeforeExternalCommandWhenLocalModelIsMissing() {
        TkGenerationProperties.Asr asr = new TkGenerationProperties.Asr();
        asr.setModelCacheDir("C:\\tk-project\\models");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service(mock(TkOpenVideoTranscriptTaskMapper.class))
                        .resolveAsrModel(asr, "small"));

        assertTrue(failure.getMessage().contains("ASR 模型目录不存在"));
    }

    private TkOpenVideoTranscriptExtractServiceImpl service(TkOpenVideoTranscriptTaskMapper mapper) {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope())
                .thenReturn(new TkUserScope(7L, 100L, "TENANT_ADMIN", 200L));
        return service(mapper, dataScopeService, new NoopExecutorService());
    }

    private TkOpenVideoTranscriptExtractServiceImpl service(TkOpenVideoTranscriptTaskMapper mapper,
                                                             TkDataScopeService dataScopeService,
                                                             ExecutorService executorService) {
        TkOpenVideoTranscriptExtractServiceImpl service = new TkOpenVideoTranscriptExtractServiceImpl();
        ReflectionTestUtils.setField(service, "transcriptTaskMapper", mapper);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "executorService", executorService);
        ReflectionTestUtils.setField(service, "syncWaitTimeoutMillis", 1L);
        ReflectionTestUtils.setField(service, "syncPollIntervalMillis", 1L);
        return service;
    }

    private TkOpenVideoTranscriptExtractCreateReqVO request() {
        TkOpenVideoTranscriptExtractCreateReqVO reqVO = new TkOpenVideoTranscriptExtractCreateReqVO();
        reqVO.setSourceUrl("https://example.com/video");
        reqVO.setTargetLanguage("zh");
        return reqVO;
    }

    private static class NoopExecutorService implements ExecutorService {

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
            return null;
        }

        @Override
        public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
            return null;
        }

        @Override
        public java.util.concurrent.Future<?> submit(Runnable task) {
            return null;
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            return java.util.Collections.emptyList();
        }

        @Override
        public <T> List<java.util.concurrent.Future<T>> invokeAll(
                java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
                long timeout,
                TimeUnit unit) {
            return java.util.Collections.emptyList();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) {
            return null;
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks,
                               long timeout,
                               TimeUnit unit) {
            return null;
        }

        @Override
        public void execute(Runnable command) {
        }
    }

    private static class CapturingExecutorService extends NoopExecutorService {

        private Runnable capturedTask;

        @Override
        public void execute(Runnable command) {
            capturedTask = command;
        }

        void runCapturedTask() {
            assertNotNull(capturedTask);
            capturedTask.run();
        }
    }
}
