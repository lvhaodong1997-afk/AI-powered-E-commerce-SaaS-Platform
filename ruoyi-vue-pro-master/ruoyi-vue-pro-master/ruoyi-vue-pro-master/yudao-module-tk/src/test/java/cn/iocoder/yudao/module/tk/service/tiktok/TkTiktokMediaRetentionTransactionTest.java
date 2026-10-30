package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokMediaRetentionTransactionTest {
    @TempDir Path tempDir;
    @Test void completedMediaInsertAndSessionTransitionShareRollbackBoundary() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss.example.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://media.example.com");
        properties.getUpload().getOss().setAccessKeyId("test");
        properties.getUpload().getOss().setAccessKeySecret("test");
        TkDataScopeService scope = mock(TkDataScopeService.class);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkUploadSessionService sessions = mock(TkUploadSessionService.class);
        TkUploadSessionDO session = new TkUploadSessionDO().setStorageMode("oss").setStatus("UPLOADING")
                .setFileName("video.mp4").setFileSize(123L).setCompanyId(200L);
        session.setTenantId(100L); session.setCreator("7");
        when(sessions.lockAccessible("upload-1")).thenReturn(session);
        doThrow(new IllegalStateException("completion update failed")).when(sessions).markCompleted("upload-1");
        TkTiktokPublishMediaMapper media = mock(TkTiktokPublishMediaMapper.class);
        TkTiktokPublishMediaUploadServiceImpl target = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(target, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(target, "generationProperties", properties);
        ReflectionTestUtils.setField(target, "dataScopeService", scope);
        ReflectionTestUtils.setField(target, "uploadSessionService", sessions);
        ReflectionTestUtils.setField(target, "mediaMapper", media);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(status);
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        TkTiktokPublishMediaUploadServiceImpl proxy = (TkTiktokPublishMediaUploadServiceImpl) factory.getProxy();
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.timeout(anyInt())).thenReturn(request);
        when(request.execute()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.header("Content-Length")).thenReturn("123");
        try (MockedStatic<HttpRequest> http = mockStatic(HttpRequest.class)) {
            http.when(() -> HttpRequest.head(anyString())).thenReturn(request);
            assertThrows(IllegalStateException.class, () -> proxy.complete("upload-1", null));
        }
        InOrder order = inOrder(manager, sessions, media);
        order.verify(manager).getTransaction(any());
        order.verify(sessions).lockAccessible("upload-1");
        order.verify(media).insert(any(TkTiktokPublishMediaDO.class));
        order.verify(sessions).markCompleted("upload-1");
        order.verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verify(sessions, never()).validateAccessible("upload-1");
    }
}