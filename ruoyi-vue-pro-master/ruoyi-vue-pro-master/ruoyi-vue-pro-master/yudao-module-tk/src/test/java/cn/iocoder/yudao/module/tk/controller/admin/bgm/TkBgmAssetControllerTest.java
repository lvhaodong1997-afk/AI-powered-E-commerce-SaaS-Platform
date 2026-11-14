package cn.iocoder.yudao.module.tk.controller.admin.bgm;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkBgmAssetDO;
import cn.iocoder.yudao.module.tk.service.bgm.TkBgmAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkBgmAssetControllerTest {

    @Test
    void userListReturnsOnlyUploadedTracks() {
        TkBgmAssetService service = mock(TkBgmAssetService.class);
        TkBgmAssetDO own = TkBgmAssetDO.builder().id(1L).name("我的上传").sourceType("USER").build();
        when(service.getAvailableList()).thenReturn(Arrays.asList(own,
                TkBgmAssetDO.builder().id(2L).name("系统音乐").sourceType("SYSTEM").build(),
                TkBgmAssetDO.builder().id(3L).name("同租户他人上传").sourceType("USER").build()));
        when(service.canDelete(own)).thenReturn(true);
        TkBgmAssetController controller = new TkBgmAssetController();
        ReflectionTestUtils.setField(controller, "bgmAssetService", service);

        assertEquals(Collections.singletonList("我的上传"),
                controller.userList().getData().stream().map(item -> item.getName()).collect(java.util.stream.Collectors.toList()));
    }
}
