package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.*;
import cn.iocoder.yudao.module.tk.controller.admin.tiktokshop.vo.TkTiktokShopPublishVO.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopPublishTaskDO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import static org.junit.jupiter.api.Assertions.*;
class TkTiktokShopPublishControllerTest {
    @Test void actualControllerMapsSafeActionableBusinessErrors()throws Exception {
        TkTiktokShopPublishService service=org.mockito.Mockito.mock(TkTiktokShopPublishService.class);
        org.mockito.Mockito.when(service.create(org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalArgumentException("secret credential private-path"));
        org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new TkTiktokShopPublishController(service,null,null)).setControllerAdvice(new TkTiktokShopExceptionHandler()).build();
        String response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tk/tiktok-shop/publish/tasks").contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("selection"));assertFalse(response.contains("secret credential"));
        org.mockito.Mockito.doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"private-path")).when(service).create(org.mockito.ArgumentMatchers.any());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/tk/tiktok-shop/publish/tasks").contentType("application/json").content("{}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }
    @Test void explicitDtoNeverLeaksPrivatePersistence(){TkTiktokShopPublishTaskDO t=new TkTiktokShopPublishTaskDO();t.setSourcePath("private-source-path");t.setOwnerToken("private-lease-owner");t.setProductSnapshot("private-proof");String s=new ObjectMapper().valueToTree(new TaskResponse(t)).toString();assertFalse(s.contains("private-"));assertFalse(s.contains("sourcePath"));assertFalse(s.contains("ownerToken"));}
    @Test void routesRequireShopPermissions()throws Exception {
        assertTrue(TkTiktokShopPublishController.class.getMethod("create",CreateRequest.class).getAnnotation(PreAuthorize.class).value().contains("tk:tiktok-shop-publish:create"));
        assertTrue(TkTiktokShopPublishController.class.getMethod("confirm",Long.class,ConfirmRequest.class).getAnnotation(PreAuthorize.class).value().contains("tk:tiktok-shop-publish:confirm"));
        assertTrue(TkTiktokShopMediaController.class.getAnnotation(PreAuthorize.class).value().contains("tk:tiktok-shop-publish:create"));
    }
}
