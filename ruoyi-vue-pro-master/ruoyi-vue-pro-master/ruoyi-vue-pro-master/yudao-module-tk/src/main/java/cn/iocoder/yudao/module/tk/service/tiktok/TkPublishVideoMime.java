package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.io.FileUtil;
import java.util.Locale;

/** Extension has already passed the endpoint's MP4/MOV/WEBM allowlist. Never persist raw multipart MIME. */
public final class TkPublishVideoMime {
    private TkPublishVideoMime() { }
    public static String normalize(String fileName, String supplied) {
        String extension = FileUtil.extName(fileName == null ? "" : fileName).toLowerCase(Locale.ROOT);
        String expected;
        switch (extension) {
            case "mp4": expected = "video/mp4"; break;
            case "mov": expected = "video/quicktime"; break;
            case "webm": expected = "video/webm"; break;
            default: throw new IllegalArgumentException("不支持的发布视频扩展名");
        }
        // MIME is advisory for legacy clients; the validated extension (and chunk container check) defines it.
        return expected;
    }
}
