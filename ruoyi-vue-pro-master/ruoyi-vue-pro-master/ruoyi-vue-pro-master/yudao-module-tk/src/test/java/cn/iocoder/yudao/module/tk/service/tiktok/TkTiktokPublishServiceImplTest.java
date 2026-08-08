package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishUrlRegisterReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.tiktok.vo.TkTiktokPublishUrlRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishDetailDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishDetailMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishTaskMapper;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TkTiktokPublishServiceImplTest {

    @Test
    void registerPublishUrlUpdatesLatestDetailForGenerationTask() {
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkGenerationTaskService generationTaskService = mock(TkGenerationTaskService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokPublishServiceImpl service = createService(null, detailMapper, generationTaskService, dataScopeService);
        TkGenerationTaskDO generationTask = TkGenerationTaskDO.builder()
                .id(100L)
                .companyId(20L)
                .status("SUCCESS")
                .outputUrl("https://oss.example.com/video.mp4")
                .build();
        generationTask.setTenantId(8L);
        TkTiktokPublishDetailDO latestDetail = TkTiktokPublishDetailDO.builder()
                .id(300L)
                .generationTaskId(100L)
                .companyId(20L)
                .accountDisplayName("demo")
                .status("SUCCESS")
                .build();
        latestDetail.setTenantId(8L);
        when(generationTaskService.getGenerationTask(100L)).thenReturn(generationTask);
        when(detailMapper.selectLatestRegisteredTargetByGenerationTaskId(100L)).thenReturn(latestDetail);
        TkTiktokPublishUrlRegisterReqVO reqVO = new TkTiktokPublishUrlRegisterReqVO();
        reqVO.setGenerationTaskId(100L);
        reqVO.setPublishUrl("  https://www.tiktok.com/@demo/video/123  ");

        TkTiktokPublishUrlRespVO result = service.registerPublishUrl(reqVO);

        verify(dataScopeService).validateWritable(8L, 20L);
        ArgumentCaptor<TkTiktokPublishDetailDO> captor = ArgumentCaptor.forClass(TkTiktokPublishDetailDO.class);
        verify(detailMapper).updateById(captor.capture());
        assertEquals(300L, captor.getValue().getId());
        assertEquals("https://www.tiktok.com/@demo/video/123", captor.getValue().getPublishUrl());
        assertNotNull(captor.getValue().getPublishUrlRegisteredTime());
        assertEquals(300L, result.getPublishDetailId());
        assertEquals("https://www.tiktok.com/@demo/video/123", result.getPublishUrl());
    }

    @Test
    void registerPublishUrlCreatesManualDetailWhenGenerationTaskHasNoPublishDetail() {
        TkTiktokPublishTaskMapper taskMapper = mock(TkTiktokPublishTaskMapper.class);
        TkTiktokPublishDetailMapper detailMapper = mock(TkTiktokPublishDetailMapper.class);
        TkGenerationTaskService generationTaskService = mock(TkGenerationTaskService.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkTiktokPublishServiceImpl service = createService(taskMapper, detailMapper, generationTaskService, dataScopeService);
        TkGenerationTaskDO generationTask = TkGenerationTaskDO.builder()
                .id(100L)
                .businessTraceId("TRACE-001")
                .companyId(20L)
                .status("SUCCESS")
                .outputUrl("https://oss.example.com/video.mp4")
                .title("Demo video")
                .build();
        generationTask.setTenantId(8L);
        when(generationTaskService.getGenerationTask(100L)).thenReturn(generationTask);
        when(detailMapper.selectLatestRegisteredTargetByGenerationTaskId(100L)).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            TkTiktokPublishTaskDO task = invocation.getArgument(0);
            task.setId(200L);
            return 1;
        }).when(taskMapper).insert(any(TkTiktokPublishTaskDO.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            TkTiktokPublishDetailDO detail = invocation.getArgument(0);
            detail.setId(300L);
            return 1;
        }).when(detailMapper).insert(any(TkTiktokPublishDetailDO.class));
        TkTiktokPublishUrlRegisterReqVO reqVO = new TkTiktokPublishUrlRegisterReqVO();
        reqVO.setGenerationTaskId(100L);
        reqVO.setPublishUrl("https://www.tiktok.com/@demo/video/123");

        TkTiktokPublishUrlRespVO result = service.registerPublishUrl(reqVO);

        verify(dataScopeService).validateWritable(8L, 20L);
        ArgumentCaptor<TkTiktokPublishTaskDO> taskCaptor = ArgumentCaptor.forClass(TkTiktokPublishTaskDO.class);
        verify(taskMapper).insert(taskCaptor.capture());
        assertEquals("MANUAL_REGISTER", taskCaptor.getValue().getPostMode());
        assertEquals("SUCCESS", taskCaptor.getValue().getStatus());
        ArgumentCaptor<TkTiktokPublishDetailDO> detailCaptor = ArgumentCaptor.forClass(TkTiktokPublishDetailDO.class);
        verify(detailMapper).insert(detailCaptor.capture());
        assertEquals(200L, detailCaptor.getValue().getPublishTaskId());
        assertEquals("MANUAL_REGISTER", detailCaptor.getValue().getPostMode());
        assertEquals("SUCCESS", detailCaptor.getValue().getStatus());
        assertEquals("https://www.tiktok.com/@demo/video/123", detailCaptor.getValue().getPublishUrl());
        assertEquals(300L, result.getPublishDetailId());
    }

    private TkTiktokPublishServiceImpl createService(TkTiktokPublishTaskMapper taskMapper,
                                                     TkTiktokPublishDetailMapper detailMapper,
                                                     TkGenerationTaskService generationTaskService,
                                                     TkDataScopeService dataScopeService) {
        TkTiktokPublishServiceImpl service = new TkTiktokPublishServiceImpl();
        ReflectionTestUtils.setField(service, "publishTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "publishDetailMapper", detailMapper);
        ReflectionTestUtils.setField(service, "generationTaskService", generationTaskService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }
}
