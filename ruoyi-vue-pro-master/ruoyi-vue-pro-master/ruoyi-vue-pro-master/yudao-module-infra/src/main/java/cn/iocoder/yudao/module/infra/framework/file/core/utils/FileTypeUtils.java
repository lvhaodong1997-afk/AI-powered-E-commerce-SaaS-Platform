package cn.iocoder.yudao.module.infra.framework.file.core.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 文件类型 Utils
 *
 * @author 秀美源码
 */
@Slf4j
public class FileTypeUtils {

    private static final Tika TIKA = new Tika();

    /**
     * 获得文件的 mineType，对于 doc，jar 等文件会有误差
     *
     * @param data 文件内容
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    @SneakyThrows
    public static String getMineType(byte[] data) {
        return TIKA.detect(data);
    }

    /**
     * 已知文件名，获取文件类型，在某些情况下比通过字节数组准确，例如使用 jar 文件时，通过名字更为准确
     *
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(String name) {
        return TIKA.detect(name);
    }

    /**
     * 在拥有文件和数据的情况下，最好使用此方法，最为准确
     *
     * @param data 文件内容
     * @param name 文件名
     * @return mineType 无法识别时会返回“application/octet-stream”
     */
    public static String getMineType(byte[] data, String name) {
        return TIKA.detect(data, name);
    }

    /**
     * 根据 mineType 获得文件后缀
     *
     * 注意：如果获取不到，或者发生异常，都返回 null
     *
     * @param mineType 类型
     * @return 后缀，例如说 .pdf
     */
    public static String getExtension(String mineType) {
        try {
            return MimeTypes.getDefaultMimeTypes().forName(mineType).getExtension();
        } catch (MimeTypeException e) {
            log.warn("[getExtension][获取文件后缀({}) 失败]", mineType, e);
            return null;
        }
    }

    /**
     * 返回附件
     *
     * @param response 响应
     * @param filename 文件名
     * @param content  附件内容
     */
    public static void writeAttachment(HttpServletResponse response, String filename, byte[] content) throws IOException {
        writeAttachment(null, response, filename, content);
    }

    /**
     * 返回附件，支持视频 Range 分片请求
     *
     * @param request  请求
     * @param response 响应
     * @param filename 文件名
     * @param content  附件内容
     */
    public static void writeAttachment(HttpServletRequest request, HttpServletResponse response,
                                       String filename, byte[] content) throws IOException {
        // 设置 header 和 contentType
        String mineType = getMineType(content, filename);
        response.setContentType(mineType);
        // 设置内容显示、下载文件名：https://www.cnblogs.com/wq-9/articles/12165056.html
        if (isImage(mineType) || isVideo(mineType)) {
            // 参见 https://github.com/YunaiV/ruoyi-vue-pro/issues/692 讨论
            response.setHeader("Content-Disposition", buildContentDisposition("inline", filename));
        } else {
            response.setHeader("Content-Disposition", buildContentDisposition("attachment", filename));
        }
        // 针对 video 的特殊处理，解决视频地址在移动端播放的兼容性问题
        if (isVideo(mineType)) {
            writeVideo(request, response, content);
            return;
        }
        // 输出附件
        IoUtil.write(response.getOutputStream(), false, content);
    }

    private static String buildContentDisposition(String dispositionType, String filename) {
        return StrUtil.format("{};filename=\"{}\";filename*=UTF-8''{}",
                dispositionType, buildFallbackFilename(filename), HttpUtils.encodeUrlPathSegment(filename));
    }

    private static String buildFallbackFilename(String filename) {
        if (StrUtil.isEmpty(filename)) {
            return "download";
        }
        StringBuilder result = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char ch = filename.charAt(i);
            if (ch == '"' || ch == '\\') {
                result.append('\\').append(ch);
            } else if (ch >= 0x20 && ch <= 0x7E) {
                result.append(ch);
            } else {
                result.append('_');
            }
        }
        return result.toString();
    }

    /**
     * 判断是否是图片
     *
     * @param mineType 类型
     * @return 是否是图片
     */
    public static boolean isImage(String mineType) {
        return StrUtil.startWith(mineType, "image/");
    }

    /**
     * 判断是否是视频
     *
     * @param mineType 类型
     * @return 是否是视频
     */
    public static boolean isVideo(String mineType) {
        return StrUtil.startWith(mineType, "video/");
    }

    private static void writeVideo(HttpServletRequest request, HttpServletResponse response, byte[] content) throws IOException {
        response.setHeader("Accept-Ranges", "bytes");
        if (request == null || StrUtil.isBlank(request.getHeader("Range"))) {
            response.setHeader("Content-Length", String.valueOf(content.length));
            IoUtil.write(response.getOutputStream(), false, content);
            return;
        }
        String rangeHeader = request.getHeader("Range");
        Long[] range = parseRange(rangeHeader, content.length);
        if (range == null) {
            response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            response.setHeader("Content-Range", "bytes */" + content.length);
            return;
        }
        long start = range[0];
        long end = range[1];
        int length = (int) (end - start + 1);
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader("Content-Length", String.valueOf(length));
        response.setHeader("Content-Range", StrUtil.format("bytes {}-{}/{}", start, end, content.length));
        response.getOutputStream().write(content, (int) start, length);
    }

    private static Long[] parseRange(String rangeHeader, int contentLength) {
        if (contentLength <= 0 || !StrUtil.startWith(rangeHeader, "bytes=")) {
            return null;
        }
        String range = StrUtil.subAfter(rangeHeader, "bytes=", false);
        if (StrUtil.isBlank(range) || range.contains(",")) {
            return null;
        }
        String[] parts = range.split("-", 2);
        try {
            long start;
            long end;
            if (StrUtil.isBlank(parts[0])) {
                long suffixLength = Long.parseLong(parts[1]);
                if (suffixLength <= 0) {
                    return null;
                }
                start = Math.max(contentLength - suffixLength, 0);
                end = contentLength - 1L;
            } else {
                start = Long.parseLong(parts[0]);
                end = parts.length > 1 && StrUtil.isNotBlank(parts[1])
                        ? Long.parseLong(parts[1]) : contentLength - 1L;
            }
            if (start < 0 || end < start || start >= contentLength) {
                return null;
            }
            return new Long[]{start, Math.min(end, contentLength - 1L)};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

}
