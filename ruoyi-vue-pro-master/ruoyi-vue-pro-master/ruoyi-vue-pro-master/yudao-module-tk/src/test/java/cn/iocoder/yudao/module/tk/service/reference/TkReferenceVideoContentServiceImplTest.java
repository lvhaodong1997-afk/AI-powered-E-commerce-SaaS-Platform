package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkReferenceVideoContentServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void findCachedSourceVideoByTikTokIdReturnsLatestValidMp4() throws Exception {
        Path workDir = tempDir.resolve("tk-generation");
        Path older = workDir.resolve("reference-old").resolve("tiktok_video");
        Path newer = workDir.resolve("opening-clip-new").resolve("tiktok_video");
        Files.createDirectories(older);
        Files.createDirectories(newer);
        Files.write(older.resolve("reference_tiktok_7643012784476949773.mp4"),
                new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'm', 'p', '4', '2'});
        Files.write(newer.resolve("reference_tiktok_7643012784476949773.mp4"),
                new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'});
        Files.write(workDir.resolve("reference_tiktok_7643012784476949773.mp4"),
                "<!doctype html><html></html>".getBytes());

        File cached = invokeFindCachedSourceVideo(serviceWithWorkDir(workDir),
                "https://www.tiktok.com/@rehabifyshop3/video/7643012784476949773");

        assertEquals(newer.resolve("reference_tiktok_7643012784476949773.mp4").toFile(), cached);
    }

    @Test
    void assertUsableVideoRejectsHtmlSavedAsMp4() throws Exception {
        File htmlMp4 = tempDir.resolve("reference_tiktok_7643012784476949773.mp4").toFile();
        Files.writeString(htmlMp4.toPath(), "<!doctype html><html><head><title>TikTok</title></head></html>");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeAssertUsableVideo(serviceWithWorkDir(tempDir), htmlMp4));

        Throwable cause = ex.getCause();
        assertTrue(cause instanceof IllegalStateException);
        assertTrue(cause.getMessage().contains("TikTok 返回的是网页"));
    }

    @Test
    void assertUsableVideoRejectsMp4WithoutContainerSignature() throws Exception {
        File fakeMp4 = tempDir.resolve("reference_tiktok_7643012784476949773.mp4").toFile();
        Files.writeString(fakeMp4.toPath(), "{\"status\":\"verify\",\"message\":\"challenge required\"}");

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeAssertUsableVideo(serviceWithWorkDir(tempDir), fakeMp4));

        Throwable cause = ex.getCause();
        assertTrue(cause instanceof IllegalStateException);
        assertTrue(cause.getMessage().contains("不是可识别的视频容器"));
    }

    @Test
    void tiktokVideoIdExtractorSupportsVideoPageUrls() throws Exception {
        assertEquals("7643012784476949773", invokeExtractCacheKey(serviceWithWorkDir(tempDir),
                "https://www.tiktok.com/@rehabifyshop3/video/7643012784476949773?is_from_webapp=1"));
        assertFalse(invokeExtractCacheKey(serviceWithWorkDir(tempDir),
                "https://example.com/video/7643012784476949773") != null);
    }

    @Test
    void normalizeSourceUrlExtractsDouyinShortUrlFromShareText() throws Exception {
        String shareText = "9.43 12/18 s@e.OX :6pm tEU:/ 梁博《日落大道》他们说主办方把老天爷请来了哈哈哈哈哈哈哈哈 "
                + "所有人感受这个金黄耀眼的日落大道！配上绝美灯光！人生时刻+1！"
                + "https://v.douyin.com/NYZ_4Fw-o30/ 复制此链接，打开Dou音搜索，直接观看视频！";

        assertEquals("https://v.douyin.com/NYZ_4Fw-o30/",
                invokeNormalizeSourceUrl(serviceWithWorkDir(tempDir), shareText));
    }

    @Test
    void canonicalizeDouyinShortUrlUsesRedirectVideoId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "https://www.iesdouyin.com/share/video/7454863364800400674/?from=web_code_link");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            String sourceUrl = "http://v.douyin.com/4eA5Q2_QxAA/";
            TkReferenceVideoContentServiceImpl service = serviceWithWorkDir(tempDir);
            serviceConfig(service).getReferenceDownload().setProxy(
                    "http://127.0.0.1:" + server.getAddress().getPort());

            assertEquals("https://www.douyin.com/video/7454863364800400674",
                    invokeCanonicalizeDouyinSourceUrl(service, sourceUrl));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void canonicalizeDouyinSourceUrlLeavesTikTokUrlsUntouched() throws Exception {
        String tiktokUrl = "https://www.tiktok.com/@rehabifyshop3/video/7643012784476949773?is_from_webapp=1";

        assertEquals(tiktokUrl, invokeCanonicalizeDouyinSourceUrl(serviceWithWorkDir(tempDir), tiktokUrl));
    }

    @Test
    void resolveSourceVideoFailsWhenLinkDownloadFailsEvenWithLibraryId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/page", exchange -> {
            byte[] body = "<!doctype html><html><body>no downloadable video</body></html>"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String sourceUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
            TkReferenceVideoContentServiceImpl service = serviceWithWorkDir(tempDir);
            serviceConfig(service).getReferenceDownload().setScriptEnabled(false);

            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    () -> invokeResolveSourceVideo(service, sourceUrl, tempDir.toFile(), 2L));

            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertTrue(ex.getCause().getMessage().contains("未从页面解析到可下载视频地址"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveSourceVideoDoesNotHtmlFallbackWhenScriptReturnsImagePost() throws Exception {
        Path zip = tempDir.resolve("reference_douyin_7647565675380106362_images.zip");
        Files.write(zip, new byte[] {1, 2, 3});
        Path script = tempDir.resolve("fake-download.py");
        Files.writeString(script, "print(r'''{\"path\":\"" + zip.toString().replace("\\", "\\\\")
                + "\",\"data\":{\"platform\":\"douyin\",\"type\":\"image\",\"video_id\":\"7647565675380106362\",\"desc\":\"不会有人没拍吧\"}}''')",
                StandardCharsets.UTF_8);
        TkReferenceVideoContentServiceImpl service = serviceWithWorkDir(tempDir);
        serviceConfig(service).getReferenceDownload().setScriptPython("py");
        serviceConfig(service).getReferenceDownload().setScriptPath(script.toString());
        serviceConfig(service).getReferenceDownload().setScriptRepo(null);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeResolveSourceVideo(service, "http://127.0.0.1:1/page", tempDir.toFile(), 2L));

        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertTrue(ex.getCause().getMessage().contains("图文/相册"));
        assertFalse(ex.getCause().getMessage().contains("HTML 兜底解析"));
    }

    @Test
    void frameSecondCandidatesFallBackToSafePositions() throws Exception {
        List<Integer> candidates = invokeFrameSecondCandidates(serviceWithWorkDir(tempDir), 17, 18L);

        assertEquals(List.of(17, 9, 1, 0), candidates);
    }

    private TkReferenceVideoContentServiceImpl serviceWithWorkDir(Path workDir) {
        TkReferenceVideoContentServiceImpl service = new TkReferenceVideoContentServiceImpl();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getFfmpeg().setWorkDir(workDir.toString());
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        return service;
    }

    private TkGenerationProperties serviceConfig(TkReferenceVideoContentServiceImpl service) {
        return (TkGenerationProperties) ReflectionTestUtils.getField(service, "generationProperties");
    }

    private Object invokeResolveSourceVideo(TkReferenceVideoContentServiceImpl service, String sourceUrl,
                                            File taskDir, Long libraryId) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("resolveSourceVideo", String.class, File.class, Long.class);
        method.setAccessible(true);
        return method.invoke(service, sourceUrl, taskDir, libraryId);
    }

    private File invokeFindCachedSourceVideo(TkReferenceVideoContentServiceImpl service, String sourceUrl) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("findCachedSourceVideo", String.class);
        method.setAccessible(true);
        return (File) method.invoke(service, sourceUrl);
    }

    private void invokeAssertUsableVideo(TkReferenceVideoContentServiceImpl service, File file) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("assertUsableDownloadedVideo", File.class);
        method.setAccessible(true);
        method.invoke(service, file);
    }

    private String invokeExtractCacheKey(TkReferenceVideoContentServiceImpl service, String sourceUrl) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("extractTikTokVideoId", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, sourceUrl);
    }

    private String invokeNormalizeSourceUrl(TkReferenceVideoContentServiceImpl service, String sourceUrl) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("normalizeSourceUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, sourceUrl);
    }

    private String invokeCanonicalizeDouyinSourceUrl(TkReferenceVideoContentServiceImpl service,
                                                     String sourceUrl) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("canonicalizeDouyinSourceUrl", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, sourceUrl);
    }

    @SuppressWarnings("unchecked")
    private List<Integer> invokeFrameSecondCandidates(TkReferenceVideoContentServiceImpl service,
                                                      int second, Long durationSeconds) throws Exception {
        Method method = TkReferenceVideoContentServiceImpl.class
                .getDeclaredMethod("frameSecondCandidates", Integer.class, Long.class);
        method.setAccessible(true);
        return (List<Integer>) method.invoke(service, second, durationSeconds);
    }
}
