package cn.iocoder.yudao.module.tk.service.social;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TkSocialVideoInspectorTest {
    @Test void parsesFractionalFrameRate() {
        TkSocialVideoInspector.Metadata value=TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"video\",\"codec_name\":\"h264\",\"width\":1080,\"height\":1920,\"avg_frame_rate\":\"30000/1001\"}],\"format\":{\"duration\":\"30.5\"}}"));
        assertEquals(29.970,value.getFrameRate(),0.001);
        assertEquals(30.5,value.getDurationSeconds());
    }
    @Test void rejectsMissingVideoAndWrongAudio() {
        assertThrows(IllegalArgumentException.class,() -> TkSocialVideoInspector.parse(JsonUtils.parseTree(
                "{\"streams\":[{\"codec_type\":\"audio\",\"codec_name\":\"mp3\"}],\"format\":{\"duration\":\"3\"}}")));
    }
    @Test void facebookRejectsLandscapeBeforeTaskCreation() {
        TkSocialMediaDO media=video(1920,1080,30,30);
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(media,"FACEBOOK_PAGE"));
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(media,"INSTAGRAM"));
    }
    @Test void durationAndFpsBoundariesArePlatformSpecific() {
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(video(1080,1920,60,30),"FACEBOOK_PAGE"));
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(video(1080,1920,61,30),"FACEBOOK_PAGE"));
        assertDoesNotThrow(() -> TkSocialMediaService.validateForPlatform(video(1080,1920,61,30),"INSTAGRAM"));
        assertThrows(IllegalArgumentException.class,() -> TkSocialMediaService.validateForPlatform(video(1080,1920,30,15),"INSTAGRAM"));
    }
    private static TkSocialMediaDO video(int w,int h,double seconds,double fps) {
        TkSocialMediaDO value=new TkSocialMediaDO(); value.setMediaType("VIDEO"); value.setWidth(w); value.setHeight(h);
        value.setDurationSeconds(seconds); value.setFrameRate(fps); return value;
    }
}
