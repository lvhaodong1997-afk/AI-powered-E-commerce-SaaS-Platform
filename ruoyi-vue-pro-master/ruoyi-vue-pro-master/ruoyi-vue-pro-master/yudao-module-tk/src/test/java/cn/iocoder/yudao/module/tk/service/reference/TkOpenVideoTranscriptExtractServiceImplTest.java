package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractCreateReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.reference.vo.TkOpenVideoTranscriptExtractSyncRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkOpenVideoTranscriptTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkOpenVideoTranscriptTaskMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
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
import static org.mockito.Mockito.when;

class TkOpenVideoTranscriptExtractServiceImplTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void createExtractTaskStoresPublicTenantWithoutUserScope() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        AtomicReference<TkOpenVideoTranscriptTaskDO> inserted = new AtomicReference<>();
        AtomicReference<Long> tenantDuringInsert = new AtomicReference<>();
        AtomicReference<Boolean> ignoreDuringInsert = new AtomicReference<>();
        doAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO task = invocation.getArgument(0);
            task.setId(99L);
            inserted.set(task);
            tenantDuringInsert.set(TenantContextHolder.getTenantId());
            ignoreDuringInsert.set(TenantContextHolder.isIgnore());
            return 1;
        }).when(mapper).insert(any(TkOpenVideoTranscriptTaskDO.class));

        TenantContextHolder.setTenantId(321L);
        TenantContextHolder.setIgnore(true);
        service(mapper, new NoopExecutorService()).createExtractTask(request());

        assertNotNull(inserted.get());
        assertEquals(0L, inserted.get().getTenantId());
        assertEquals(null, inserted.get().getCompanyId());
        assertEquals(0L, tenantDuringInsert.get());
        assertFalse(ignoreDuringInsert.get());
        assertEquals(321L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void getExtractTaskReadsOnlyPublicTenantWithoutUserScope() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkOpenVideoTranscriptTaskDO task = TkOpenVideoTranscriptTaskDO.builder()
                .id(105L)
                .status("SUCCESS")
                .segmentsJson("[]")
                .wordsJson("[]")
                .build();
        task.setTenantId(100L);
        task.setCompanyId(200L);
        task.setCreator("7");
        AtomicReference<Long> tenantDuringSelect = new AtomicReference<>();
        AtomicReference<Boolean> ignoreDuringSelect = new AtomicReference<>();
        when(mapper.selectById(105L)).thenAnswer(invocation -> {
            tenantDuringSelect.set(TenantContextHolder.getTenantId());
            ignoreDuringSelect.set(TenantContextHolder.isIgnore());
            return task;
        });

        TenantContextHolder.setTenantId(321L);
        TenantContextHolder.setIgnore(true);
        service(mapper, new NoopExecutorService()).getExtractTask(105L);

        assertEquals(0L, tenantDuringSelect.get());
        assertFalse(ignoreDuringSelect.get());
        assertEquals(321L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void createExtractTaskRunsAsyncWorkInPublicTenantAndRestoresWorkerContext() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
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

        service(mapper, executor).createExtractTask(request());
        TenantContextHolder.setTenantId(999L);
        TenantContextHolder.setIgnore(true);
        executor.runCapturedTask();

        assertEquals(0L, tenantDuringTask.get());
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
    void getExtractTaskPrefersVerifiedTranscriptAndTimelineWithoutChangingTiming() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        when(mapper.selectById(104L)).thenReturn(TkOpenVideoTranscriptTaskDO.builder()
                .id(104L)
                .status("SUCCESS")
                .targetLanguage("zh-CN")
                .transcriptText("B L G 首 發")
                .segmentsJson("[{\"text\":\"團隊 回歸\",\"start\":0.1,\"end\":1.2}]")
                .wordsJson("[{\"text\":\"回 歸\",\"start\":0.1,\"end\":0.8}]")
                .verifiedTranscriptText("校验后的文案")
                .verifiedSegmentsJson("[{\"text\":\"校验后的团队\",\"start\":0.1,\"end\":1.2}]")
                .textVerifyStatus("SUCCESS")
                .textVerifyModel("deepseek-v4-flash")
                .build());

        TkOpenVideoTranscriptExtractRespVO result = service(mapper).getExtractTask(104L);

        assertEquals("校验后的文案", result.getTranscriptText());
        assertEquals("校验后的团队", result.getSegments().get(0).get("text"));
        assertEquals(0.1D, result.getSegments().get(0).get("start"));
        assertEquals("回归", result.getWords().get(0).get("text"));
        assertEquals(0.8D, result.getWords().get(0).get("end"));
        assertEquals("校验后的文案", result.getVerifiedTranscriptText());
        assertEquals("校验后的团队", result.getVerifiedSegments().get(0).get("text"));
        assertEquals(0.1D, result.getVerifiedSegments().get(0).get("start"));
        assertEquals("SUCCESS", result.getTextVerifyStatus());
        assertEquals("deepseek-v4-flash", result.getTextVerifyModel());
    }

    @Test
    void persistAsrResultStoresVerifiedTextAndKeepsOriginalTimeline() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkTranscriptTextVerifyService verifier = mock(TkTranscriptTextVerifyService.class);
        List<TkOpenVideoTranscriptTaskDO> updates = new ArrayList<>();
        AtomicReference<TkOpenVideoTranscriptTaskDO> lastUpdate = new AtomicReference<>();
        when(mapper.updateById(any(TkOpenVideoTranscriptTaskDO.class))).thenAnswer(invocation -> {
            TkOpenVideoTranscriptTaskDO update = invocation.getArgument(0);
            updates.add(update);
            lastUpdate.set(update);
            return 1;
        });
        String originalSegments = "[{\"start\":1.25,\"end\":2.75,\"text\":\"原始错字\"}]";
        when(verifier.verify("原始错字", originalSegments))
                .thenReturn(new TkTranscriptTextVerifyResult("校验后的文字",
                        "[{\"start\":1.25,\"end\":2.75,\"text\":\"校验后的文字\"}]"));

        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getTranscriptVerify().setEnabled(true);
        TkOpenVideoTranscriptExtractServiceImpl service = service(mapper, new NoopExecutorService());
        ReflectionTestUtils.setField(service, "transcriptTextVerifyService", verifier);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        ReflectionTestUtils.invokeMethod(service, "persistAsrResult", 201L, "audio-url", 2.75D,
                "原始错字", originalSegments, "[]", "small", "{\"segments\":[]}");

        assertEquals("原始错字", ReflectionTestUtils.getField(updates.get(0), "transcriptText"));
        assertEquals(originalSegments, ReflectionTestUtils.getField(updates.get(0), "segmentsJson"));
        assertEquals("校验后的文字", ReflectionTestUtils.getField(lastUpdate.get(), "verifiedTranscriptText"));
        assertEquals("[{\"start\":1.25,\"end\":2.75,\"text\":\"校验后的文字\"}]",
                ReflectionTestUtils.getField(lastUpdate.get(), "verifiedSegmentsJson"));
        assertEquals("SUCCESS", ReflectionTestUtils.getField(lastUpdate.get(), "textVerifyStatus"));
    }

    @Test
    void persistAsrResultFallsBackToOriginalWhenTextVerificationFails() {
        TkOpenVideoTranscriptTaskMapper mapper = mock(TkOpenVideoTranscriptTaskMapper.class);
        TkTranscriptTextVerifyService verifier = mock(TkTranscriptTextVerifyService.class);
        AtomicReference<TkOpenVideoTranscriptTaskDO> lastUpdate = new AtomicReference<>();
        when(mapper.updateById(any(TkOpenVideoTranscriptTaskDO.class))).thenAnswer(invocation -> {
            lastUpdate.set(invocation.getArgument(0));
            return 1;
        });
        String originalSegments = "[{\"start\":3.0,\"end\":4.0,\"text\":\"原始文案\"}]";
        when(verifier.verify("原始文案", originalSegments)).thenThrow(new IllegalStateException("DeepSeek unavailable"));

        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getTranscriptVerify().setEnabled(true);
        TkOpenVideoTranscriptExtractServiceImpl service = service(mapper, new NoopExecutorService());
        ReflectionTestUtils.setField(service, "transcriptTextVerifyService", verifier);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        ReflectionTestUtils.invokeMethod(service, "persistAsrResult", 202L, "audio-url", 4.0D,
                "原始文案", originalSegments, "[]", "small", "{\"segments\":[]}");

        assertEquals("SUCCESS", ReflectionTestUtils.getField(lastUpdate.get(), "status"));
        assertEquals("原始文案", ReflectionTestUtils.getField(lastUpdate.get(), "verifiedTranscriptText"));
        assertEquals(originalSegments, ReflectionTestUtils.getField(lastUpdate.get(), "verifiedSegmentsJson"));
        assertEquals("FAILED", ReflectionTestUtils.getField(lastUpdate.get(), "textVerifyStatus"));
        assertEquals("DeepSeek unavailable", ReflectionTestUtils.getField(lastUpdate.get(), "textVerifyFailReason"));
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
                .verifiedTranscriptText("verified hello world")
                .verifiedSegmentsJson("[]")
                .textVerifyStatus("SUCCESS")
                .asrProvider("FASTER_WHISPER")
                .asrModel("small")
                .build());

        TkOpenVideoTranscriptExtractSyncRespVO result = service(mapper).extractAndWait(request());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("verified hello world", result.getTranscriptText());
        assertEquals("verified hello world", result.getVerifiedTranscriptText());
        assertEquals("SUCCESS", result.getTextVerifyStatus());
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
        return service(mapper, new NoopExecutorService());
    }

    private TkOpenVideoTranscriptExtractServiceImpl service(TkOpenVideoTranscriptTaskMapper mapper,
                                                             ExecutorService executorService) {
        TkOpenVideoTranscriptExtractServiceImpl service = new TkOpenVideoTranscriptExtractServiceImpl();
        ReflectionTestUtils.setField(service, "transcriptTaskMapper", mapper);
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
