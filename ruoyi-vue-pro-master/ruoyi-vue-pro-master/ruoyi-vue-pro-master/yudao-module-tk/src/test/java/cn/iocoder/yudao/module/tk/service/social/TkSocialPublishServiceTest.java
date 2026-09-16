package cn.iocoder.yudao.module.tk.service.social;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialPublishCreateReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
class TkSocialPublishServiceTest {
    @Test void identicalTargetsAndContentHaveSameHashAcrossOrderAndKeys() {
        TkSocialPublishCreateReqVO a=request(); TkSocialPublishCreateReqVO b=request();
        b.setAccountIds(Arrays.asList(2L,1L,2L)); b.setIdempotencyKey("other-request-key");
        assertEquals(TkSocialPublishService.requestHash(a),TkSocialPublishService.requestHash(b));
        b.setGenerationTaskId(99L);
        assertNotEquals(TkSocialPublishService.requestHash(a),TkSocialPublishService.requestHash(b));
    }
    @Test void keyReuseRejectsDifferentPayload() {
        TkSocialPublishTaskDO task=new TkSocialPublishTaskDO(); task.setId(1L); task.setRequestHash("hash");
        assertEquals(1L,TkSocialPublishService.existingId(task,"hash"));
        assertThrows(IllegalArgumentException.class,() -> TkSocialPublishService.existingId(task,"changed"));
    }
    private TkSocialPublishCreateReqVO request() {
        TkSocialPublishCreateReqVO value=new TkSocialPublishCreateReqVO(); value.setAccountIds(Arrays.asList(1L,2L));
        value.setTitle("video"); value.setMediaId(5L); value.setIdempotencyKey("test-request-key");
        value.setInstagramCaption("caption"); return value;
    }
}
