package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class TkReferenceVideoContentServiceImpl implements TkReferenceVideoContentService {

    private static final int HTTP_TIMEOUT_MILLIS = 120_000;
    private static final int DEFAULT_HTML_TIMEOUT_MILLIS = 20_000;
    private static final int PROCESS_TIMEOUT_MINUTES = 5;
    private static final int MAX_FRAME_COUNT = 5;
    private static final int MIN_OPENING_CLIP_SECONDS = 1;
    private static final int MAX_OPENING_CLIP_SECONDS = 10;
    private static final int VIDEO_SIGNATURE_BYTES = 64;
    private static final java.util.regex.Pattern SOURCE_URL_PATTERN = java.util.regex.Pattern
            .compile("https?://[A-Za-z0-9._~:/?#@\\[\\]!$&'()*+,;=%-]+", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DOUYIN_VIDEO_ID_PATH_PATTERN = java.util.regex.Pattern
            .compile("/(?:share/)?video/(\\d+)(?:[/?#]|$)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern DOUYIN_MODAL_ID_PATTERN = java.util.regex.Pattern
            .compile("[?&#](?:modal_id|aweme_id)=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private FileApi fileApi;

    @Override
    public TkReferenceVideoContent analyze(String sourceUrl) {
        return analyze(sourceUrl, null);
    }

    @Override
    public TkReferenceVideoContent analyze(String sourceUrl, Long libraryId) {
        try {
            File taskDir = FileUtil.mkdir(new File(resolveWorkDir(), "reference-" + UUID.randomUUID()));
            SourceVideo sourceVideo = resolveSourceVideo(sourceUrl, taskDir, libraryId);
            VideoMetadata metadata = probe(sourceVideo.file);
            List<TkReferenceVideoContent.Frame> frames = extractFrames(sourceVideo.file, taskDir, metadata.durationSeconds);
            String previewVideoUrl = saveVideo(sourceVideo.file);
            String coverUrl = saveCover(frames.get(0));
            return new TkReferenceVideoContent(sourceUrl, previewVideoUrl, coverUrl,
                    metadata.durationSeconds, metadata.resolution, frames);
        } catch (Exception ex) {
            throw new IllegalStateException("真实对标视频内容解析失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public TkReferenceOpeningClip createOpeningClip(String sourceUrl, Integer startSecond, Integer endSecond,
                                                    Long tenantId, Long companyId) {
        int start = startSecond == null ? 0 : startSecond;
        int end = endSecond == null ? start + 5 : endSecond;
        validateOpeningClipRange(start, end);
        try {
            File taskDir = FileUtil.mkdir(new File(resolveWorkDir(), "opening-clip-" + UUID.randomUUID()));
            SourceVideo sourceVideo = resolveSourceVideo(sourceUrl, taskDir);
            VideoMetadata metadata = probe(sourceVideo.file);
            long sourceDuration = metadata.durationSeconds == null ? 0L : metadata.durationSeconds;
            if (sourceDuration > 0 && start >= sourceDuration) {
                throw new IllegalStateException("裁剪开始秒数不能超过链接视频总时长");
            }
            if (sourceDuration > 0 && end > sourceDuration) {
                end = (int) sourceDuration;
            }
            validateOpeningClipRange(start, end);

            File clip = new File(taskDir, "opening-clip.mp4");
            runCommand(Arrays.asList(ffmpeg(), "-y",
                    "-ss", String.valueOf(start),
                    "-t", String.valueOf(end - start),
                    "-i", sourceVideo.file.getAbsolutePath(),
                    "-vf", "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2,setsar=1",
                    "-r", "30", "-an", "-c:v", "libx264", "-preset", "veryfast", clip.getAbsolutePath()));
            if (!clip.isFile() || clip.length() <= 0) {
                throw new IllegalStateException("FFmpeg 未生成开头裁剪片段");
            }
            String directory = StrUtil.format("tk/{}/{}/generation-openings", tenantId, companyId);
            String fileName = StrUtil.format("opening-link-{}-{}s-{}s.mp4", UUID.randomUUID(), start, end);
            String url = fileApi.createFile(Files.readAllBytes(clip.toPath()), fileName, directory, "video/mp4");
            return new TkReferenceOpeningClip(url, StrUtil.format("链接裁剪开头 {}-{} 秒", start, end), start, end);
        } catch (Exception ex) {
            throw new IllegalStateException("链接视频开头裁剪失败：" + ex.getMessage(), ex);
        }
    }

    private void validateOpeningClipRange(int start, int end) {
        if (start < 0) {
            throw new IllegalStateException("裁剪开始秒数不能小于 0");
        }
        if (end <= start) {
            throw new IllegalStateException("裁剪结束秒数必须大于开始秒数");
        }
        int duration = end - start;
        if (duration < MIN_OPENING_CLIP_SECONDS) {
            throw new IllegalStateException("裁剪片段不能短于 1 秒");
        }
        if (duration > MAX_OPENING_CLIP_SECONDS) {
            throw new IllegalStateException("裁剪片段不能超过 10 秒");
        }
    }

    private SourceVideo resolveSourceVideo(String sourceUrl, File taskDir) {
        return resolveSourceVideo(sourceUrl, taskDir, null);
    }

    private SourceVideo resolveSourceVideo(String sourceUrl, File taskDir, Long libraryId) {
        sourceUrl = normalizeSourceUrl(sourceUrl);
        sourceUrl = canonicalizeDouyinSourceUrl(sourceUrl);
        File cached = findCachedSourceVideo(sourceUrl);
        if (cached != null) {
            log.info("[resolveSourceVideo][sourceUrl({}) 复用历史成功下载视频({})]", sourceUrl, cached.getAbsolutePath());
            return new SourceVideo(cached, sourceUrl);
        }

        if (isDirectVideoUrl(sourceUrl)) {
            return new SourceVideo(download(sourceUrl, new File(taskDir, "reference-video.mp4")), sourceUrl);
        }

        Exception scriptError = null;
        if (isScriptDownloadEnabled()) {
            try {
                return downloadByApp08Script(sourceUrl, taskDir);
            } catch (NonVideoSourceException ex) {
                throw ex;
            } catch (Exception ex) {
                scriptError = ex;
                log.warn("[resolveSourceVideo][sourceUrl({}) app08 视频下载脚本失败，准备尝试 HTML 兜底解析]", sourceUrl, ex);
            }
        }

        if (!isHtmlFallbackEnabled()) {
            if (scriptError != null) {
                throw new IllegalStateException("app08 真实视频下载失败：" + scriptError.getMessage(), scriptError);
            }
            throw new IllegalStateException("作品页视频下载脚本未启用，且 HTML 兜底解析已关闭");
        }

        try {
            String videoUrl = resolveVideoUrlFromHtml(sourceUrl);
            return new SourceVideo(download(videoUrl, new File(taskDir, "reference-video.mp4")), videoUrl);
        } catch (Exception htmlError) {
            if (scriptError != null) {
                throw new IllegalStateException("app08 真实视频下载失败：" + scriptError.getMessage()
                        + "；HTML 兜底解析也失败：" + htmlError.getMessage(), scriptError);
            }
            throw htmlError;
        }
    }

    private boolean isScriptDownloadEnabled() {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        return config != null && Boolean.TRUE.equals(config.getScriptEnabled());
    }

    private SourceVideo downloadByApp08Script(String sourceUrl, File taskDir) {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        Path script = requiredFile(config.getScriptPath(), "视频下载脚本路径未配置");
        Path repo = optionalPath(config.getScriptRepo());
        if (repo != null && !Files.isDirectory(repo)) {
            throw new IllegalStateException("视频下载脚本依赖仓库不存在：" + repo);
        }

        List<String> command = new ArrayList<>();
        command.add(StrUtil.blankToDefault(config.getScriptPython(), "py"));
        command.add(script.toString());
        if (repo != null) {
            command.add("--repo");
            command.add(repo.toString());
        }
        command.add("download");
        command.add(sourceUrl);
        command.add("--out-dir");
        command.add(taskDir.getAbsolutePath());
        command.add("--prefix");
        command.add("reference");

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        File scriptParent = script.getParent() == null ? null : script.getParent().toFile();
        if (scriptParent != null && scriptParent.isDirectory()) {
            builder.directory(scriptParent);
        }
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.environment().put("PYTHONUTF8", "1");
        if (repo != null) {
            builder.environment().put("DOUYIN_TIKTOK_API_REPO", repo.toString());
        }
        String proxy = referenceDownloadProxy();
        if (StrUtil.isNotBlank(proxy)) {
            builder.environment().put("TK_REFERENCE_DOWNLOAD_PROXY", proxy);
            builder.environment().put("HTTP_PROXY", proxy);
            builder.environment().put("HTTPS_PROXY", proxy);
            builder.environment().put("http_proxy", proxy);
            builder.environment().put("https_proxy", proxy);
        }
        prependFfmpegPath(builder);

        String output;
        try {
            output = runProcess(builder, scriptTimeoutSeconds(), "app08 视频下载脚本");
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }

        JsonNode root;
        try {
            root = JsonUtils.parseTree(jsonPayload(output));
        } catch (Exception ex) {
            throw new IllegalStateException("视频下载脚本返回结果无法解析：" + truncate(logText(output), 300), ex);
        }

        File downloaded = resolveDownloadedFile(root.path("path").asText(""), taskDir);
        if (!isSupportedVideoFile(downloaded)) {
            throw new NonVideoSourceException(buildNonVideoSourceMessage(root, downloaded));
        }
        assertUsableDownloadedVideo(downloaded);
        String resolvedVideoUrl = firstNotBlank(extractScriptVideoUrl(root), sourceUrl);
        return new SourceVideo(downloaded, resolvedVideoUrl);
    }

    private String buildNonVideoSourceMessage(JsonNode root, File downloaded) {
        JsonNode data = root.path("data");
        String type = firstNotBlank(data.path("type").asText(""), root.path("type").asText(""));
        if ("image".equalsIgnoreCase(type)) {
            String platform = platformLabel(data.path("platform").asText(""));
            String videoId = data.path("video_id").asText("");
            String desc = data.path("desc").asText("");
            StringBuilder message = new StringBuilder(platform)
                    .append("链接是图文/相册作品，不是视频，当前视频分析只支持视频链接");
            if (StrUtil.isNotBlank(videoId)) {
                message.append("，作品ID：").append(videoId);
            }
            if (StrUtil.isNotBlank(desc)) {
                message.append("，标题：").append(StrUtil.maxLength(desc, 60));
            }
            return message.toString();
        }
        return "链接解析结果不是视频文件，可能是图文/相册内容：" + downloaded.getName();
    }

    private String platformLabel(String platform) {
        if ("douyin".equalsIgnoreCase(platform)) {
            return "抖音";
        }
        if ("tiktok".equalsIgnoreCase(platform)) {
            return "TikTok";
        }
        if ("bilibili".equalsIgnoreCase(platform)) {
            return "B 站";
        }
        return "该";
    }

    private String resolveVideoUrlFromHtml(String sourceUrl) {
        String html = fetchHtml(sourceUrl);
        String videoUrl = firstNotBlank(
                extractMetaContent(html, "property", "og:video"),
                extractMetaContent(html, "property", "og:video:url"),
                extractMetaContent(html, "property", "og:video:secure_url"),
                extractMetaContent(html, "name", "twitter:player:stream"),
                extractJsonVideoUrl(html));
        if (StrUtil.isBlank(videoUrl)) {
            throw new IllegalStateException("未从页面解析到可下载视频地址");
        }
        return absolutize(sourceUrl, videoUrl);
    }

    private String normalizeSourceUrl(String sourceUrl) {
        String text = StrUtil.trimToEmpty(sourceUrl);
        if (StrUtil.isBlank(text)) {
            return text;
        }
        java.util.regex.Matcher matcher = SOURCE_URL_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = stripTrailingUrlPunctuation(matcher.group());
            if (isSupportedSourceUrl(candidate)) {
                return candidate;
            }
        }
        return text;
    }

    private String canonicalizeDouyinSourceUrl(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl) || isDirectVideoUrl(sourceUrl)) {
            return sourceUrl;
        }
        URI uri;
        try {
            uri = URI.create(sourceUrl);
        } catch (Exception ex) {
            return sourceUrl;
        }
        String host = StrUtil.blankToDefault(uri.getHost(), "").toLowerCase();
        if (!isDouyinHost(host)) {
            return sourceUrl;
        }
        String videoId = extractDouyinVideoId(sourceUrl);
        if (StrUtil.isNotBlank(videoId)) {
            return canonicalDouyinVideoUrl(videoId);
        }
        if (!isDouyinShortHost(host)) {
            return sourceUrl;
        }
        String location = fetchDouyinRedirectLocation(sourceUrl);
        videoId = extractDouyinVideoId(location);
        if (StrUtil.isBlank(videoId)) {
            throw new IllegalStateException("抖音短链跳转未识别到作品 ID：" + location);
        }
        return canonicalDouyinVideoUrl(videoId);
    }

    private String extractDouyinVideoId(String url) {
        if (StrUtil.isBlank(url)) {
            return null;
        }
        java.util.regex.Matcher pathMatcher = DOUYIN_VIDEO_ID_PATH_PATTERN.matcher(url);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        java.util.regex.Matcher queryMatcher = DOUYIN_MODAL_ID_PATTERN.matcher(url);
        return queryMatcher.find() ? queryMatcher.group(1) : null;
    }

    private String fetchDouyinRedirectLocation(String url) {
        HttpURLConnection connection = null;
        try {
            validateRemoteUrl(url);
            URL target = new URL(url);
            Proxy proxy = buildProxy(referenceDownloadProxy());
            connection = (HttpURLConnection) (proxy == null ? target.openConnection() : target.openConnection(proxy));
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(htmlTimeoutMillis());
            connection.setReadTimeout(htmlTimeoutMillis());
            int status = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            if (status < 300 || status >= 400 || StrUtil.isBlank(location)) {
                throw new IllegalStateException(StrUtil.format("抖音短链跳转失败，HTTP {}：{}", status, url));
            }
            String resolved = URI.create(url).resolve(location).toString();
            validateRemoteUrl(resolved);
            return resolved;
        } catch (Exception ex) {
            throw new IllegalStateException(networkError("抖音短链跳转解析失败", url, ex), ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean isDouyinHost(String host) {
        return StrUtil.equals(host, "douyin.com") || StrUtil.endWith(host, ".douyin.com")
                || StrUtil.equals(host, "iesdouyin.com") || StrUtil.endWith(host, ".iesdouyin.com");
    }

    private boolean isDouyinShortHost(String host) {
        return StrUtil.equals(host, "v.douyin.com");
    }

    private String canonicalDouyinVideoUrl(String videoId) {
        return "https://www.douyin.com/video/" + videoId;
    }

    private String stripTrailingUrlPunctuation(String url) {
        String result = StrUtil.trimToEmpty(url);
        while (StrUtil.isNotBlank(result) && StrUtil.containsAny(String.valueOf(result.charAt(result.length() - 1)),
                ".", ",", ";", ":", "!", "?", ")", "]", "}", "）", "】", "》", "\"", "'")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isSupportedSourceUrl(String url) {
        String host;
        String path;
        try {
            URI uri = URI.create(url);
            host = StrUtil.blankToDefault(uri.getHost(), "").toLowerCase();
            path = StrUtil.blankToDefault(uri.getPath(), "").toLowerCase();
        } catch (Exception ex) {
            return false;
        }
        return StrUtil.containsAny(host, "douyin.com", "tiktok.com", "bilibili.com", "b23.tv")
                || path.endsWith(".mp4") || path.endsWith(".mov") || path.endsWith(".webm") || path.endsWith(".m4v");
    }

    private boolean isDirectVideoUrl(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (Exception ex) {
            return false;
        }
        String lower = StrUtil.blankToDefault(path, "").toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".m4v");
    }

    private String fetchHtml(String url) {
        validateRemoteUrl(url);
        try (HttpResponse response = executeSafeGet(url, htmlTimeoutMillis())) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("页面下载失败，HTTP {}：{}", response.getStatus(), url));
            }
            return response.body();
        } catch (Exception ex) {
            throw new IllegalStateException(networkError("页面下载失败", url, ex), ex);
        }
    }

    private String extractMetaContent(String html, String attr, String value) {
        Document document = Jsoup.parse(html);
        Element element = document.selectFirst(StrUtil.format("meta[{}={}]", attr, value));
        return element == null ? null : element.attr("content");
    }

    private String extractJsonVideoUrl(String html) {
        Document document = Jsoup.parse(html);
        Elements scripts = document.select("script");
        for (Element script : scripts) {
            String content = script.html();
            String url = firstMatchedVideoUrl(content);
            if (StrUtil.isNotBlank(url)) {
                return url;
            }
        }
        return firstMatchedVideoUrl(html);
    }

    private String firstMatchedVideoUrl(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("https?:\\\\?/\\\\?/[^\"'<>\\s]+?\\.(?:mp4|mov|webm|m4v)(?:\\?[^\"'<>\\s]*)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group().replace("\\/", "/");
    }

    private String absolutize(String baseUrl, String videoUrl) {
        if (StrUtil.startWithIgnoreCase(videoUrl, "http://") || StrUtil.startWithIgnoreCase(videoUrl, "https://")) {
            return videoUrl;
        }
        return URI.create(baseUrl).resolve(videoUrl).toString();
    }

    private File download(String url, File target) {
        if (StrUtil.isBlank(url) || !(StrUtil.startWithIgnoreCase(url, "http://") || StrUtil.startWithIgnoreCase(url, "https://"))) {
            throw new IllegalStateException("视频 URL 不是可下载的 HTTP 地址：" + url);
        }
        validateRemoteUrl(url);
        try (HttpResponse response = executeSafeGet(url, HTTP_TIMEOUT_MILLIS)) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("视频下载失败，HTTP {}：{}", response.getStatus(), url));
            }
            byte[] content = response.bodyBytes();
            Long maxBytes = generationProperties.getReferenceDownload().getMaxDownloadBytes();
            if (maxBytes != null && maxBytes > 0 && content.length > maxBytes) {
                throw new IllegalStateException("video download exceeds configured size limit");
            }
            FileUtil.writeBytes(content, target);
            if (!target.isFile() || target.length() <= 0) {
                throw new IllegalStateException("视频下载文件为空：" + url);
            }
            assertUsableDownloadedVideo(target);
            return target;
        } catch (Exception ex) {
            throw new IllegalStateException(networkError("视频下载失败", url, ex), ex);
        }
    }

    private File findCachedSourceVideo(String sourceUrl) {
        String tikTokVideoId = extractTikTokVideoId(sourceUrl);
        if (StrUtil.isBlank(tikTokVideoId)) {
            return null;
        }
        Path workDir = resolveWorkDir().toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(workDir)) {
            return null;
        }
        try {
            return Files.walk(workDir, 5)
                    .filter(Files::isRegularFile)
                    .filter(path -> StrUtil.contains(path.getFileName().toString(), tikTokVideoId))
                    .filter(path -> isReusableCachedVideo(path.toFile()))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()))
                    .map(Path::toFile)
                    .orElse(null);
        } catch (IOException ex) {
            log.warn("[findCachedSourceVideo][sourceUrl({}) 扫描历史下载视频失败]", sourceUrl, ex);
            return null;
        }
    }

    private String extractTikTokVideoId(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl) || !StrUtil.containsIgnoreCase(sourceUrl, "tiktok.com")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/video/(\\d+)")
                .matcher(sourceUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    private boolean isReusableCachedVideo(File file) {
        return isSupportedVideoFile(file) && !isHtmlLikeFile(file) && hasVideoContainerSignature(file);
    }

    private void assertUsableDownloadedVideo(File file) {
        if (!isSupportedVideoFile(file)) {
            throw new IllegalStateException("链接解析结果不是视频文件，可能是图文/相册内容：" + file.getName());
        }
        if (isHtmlLikeFile(file)) {
            throw new IllegalStateException("TikTok 返回的是网页/风控页，不是真实 MP4 视频，请稍后重试、更换链接或上传视频素材");
        }
        if (!hasVideoContainerSignature(file)) {
            throw new IllegalStateException("TikTok 下载结果不是可识别的视频容器，可能拿到的是风控页或错误响应，请稍后重试、更换链接或配置可用代理");
        }
    }

    private boolean isHtmlLikeFile(File file) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (IOException ex) {
            throw new IllegalStateException("下载文件不可读取：" + file.getAbsolutePath(), ex);
        }
        int length = Math.min(bytes.length, VIDEO_SIGNATURE_BYTES);
        String head = new String(bytes, 0, length, StandardCharsets.UTF_8).trim().toLowerCase();
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<head>");
    }

    private boolean hasVideoContainerSignature(File file) {
        byte[] bytes = new byte[VIDEO_SIGNATURE_BYTES];
        int length;
        try (java.io.InputStream input = Files.newInputStream(file.toPath())) {
            length = input.read(bytes);
        } catch (IOException ex) {
            return false;
        }
        if (length <= 0) {
            return false;
        }
        String ascii = new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
        return ascii.contains("ftyp") || bytes[0] == 0x1A && length > 3 && bytes[1] == 0x45 && bytes[2] == (byte) 0xDF && bytes[3] == (byte) 0xA3;
    }

    private VideoMetadata probe(File source) throws Exception {
        String output = runCommand(Arrays.asList(ffprobe(), "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height:format=duration",
                "-of", "json",
                source.getAbsolutePath()));
        JsonNode root = JsonUtils.parseTree(output);
        JsonNode stream = root.path("streams").path(0);
        int width = stream.path("width").asInt(0);
        int height = stream.path("height").asInt(0);
        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("FFprobe 未识别到视频分辨率");
        }
        BigDecimal duration = new BigDecimal(root.path("format").path("duration").asText("0"));
        long durationSeconds = Math.max(1L, duration.setScale(0, RoundingMode.CEILING).longValue());
        return new VideoMetadata(durationSeconds, width + "x" + height);
    }

    private List<TkReferenceVideoContent.Frame> extractFrames(File source, File taskDir, Long durationSeconds) throws Exception {
        List<TkReferenceVideoContent.Frame> frames = new ArrayList<>();
        for (Integer second : frameSeconds(durationSeconds)) {
            Exception lastError = null;
            TkReferenceVideoContent.Frame extracted = null;
            for (Integer candidateSecond : frameSecondCandidates(second, durationSeconds)) {
                File frame = new File(taskDir, "frame-" + second + "-" + candidateSecond + ".jpg");
                try {
                    runCommand(Arrays.asList(ffmpeg(), "-y", "-ss", String.valueOf(candidateSecond), "-i", source.getAbsolutePath(),
                            "-frames:v", "1", "-vf", "scale=720:-2", "-q:v", "3", frame.getAbsolutePath()));
                    if (!frame.isFile() || frame.length() <= 0) {
                        lastError = new IllegalStateException("FFmpeg 未抽取到关键帧：" + candidateSecond + "s");
                        continue;
                    }
                    extracted = new TkReferenceVideoContent.Frame(candidateSecond, "image/jpeg",
                            Base64.encode(Files.readAllBytes(frame.toPath())));
                    break;
                } catch (Exception ex) {
                    lastError = ex;
                }
            }
            if (extracted == null) {
                throw new IllegalStateException("FFmpeg 未抽取到关键帧：" + second + "s", lastError);
            }
            frames.add(extracted);
        }
        if (frames.isEmpty()) {
            throw new IllegalStateException("未抽取到可分析关键帧");
        }
        return frames;
    }

    private List<Integer> frameSecondCandidates(Integer second, Long durationSeconds) {
        long duration = durationSeconds == null || durationSeconds <= 0 ? 0L : durationSeconds;
        List<Integer> candidates = new ArrayList<>();
        addFrameSecondCandidate(candidates, normalizeFrameSecond(second, duration));
        if (duration > 1) {
            addFrameSecondCandidate(candidates, (int) Math.max(0L, Math.min(duration - 1, duration / 2)));
            addFrameSecondCandidate(candidates, 1);
        }
        addFrameSecondCandidate(candidates, 0);
        return candidates;
    }

    private Integer normalizeFrameSecond(Integer second, long durationSeconds) {
        int value = second == null ? 0 : Math.max(0, second);
        if (durationSeconds > 0) {
            value = (int) Math.min(value, durationSeconds - 1);
        }
        return value;
    }

    private void addFrameSecondCandidate(List<Integer> candidates, Integer second) {
        if (second != null && second >= 0 && !candidates.contains(second)) {
            candidates.add(second);
        }
    }

    private String saveCover(TkReferenceVideoContent.Frame frame) {
        String directory = "tk/reference-covers";
        return fileApi.createFile(Base64.decode(frame.getBase64Data()),
                "reference-cover-" + UUID.randomUUID() + ".jpg", directory, frame.getMimeType());
    }

    private String saveVideo(File source) throws IOException {
        String directory = "tk/reference-videos";
        String extension = StrUtil.blankToDefault(FileUtil.extName(source), "mp4").toLowerCase();
        return fileApi.createFile(Files.readAllBytes(source.toPath()),
                "reference-video-" + UUID.randomUUID() + "." + extension, directory, videoMimeType(extension));
    }

    private String videoMimeType(String extension) {
        if (StrUtil.equalsAnyIgnoreCase(extension, "mov", "qt")) {
            return "video/quicktime";
        }
        if (StrUtil.equalsIgnoreCase(extension, "webm")) {
            return "video/webm";
        }
        if (StrUtil.equalsIgnoreCase(extension, "m4v")) {
            return "video/x-m4v";
        }
        return "video/mp4";
    }

    private List<Integer> frameSeconds(Long durationSeconds) {
        long duration = durationSeconds == null || durationSeconds <= 0 ? 30 : durationSeconds;
        int count = (int) Math.min(MAX_FRAME_COUNT, Math.max(1, duration / 6));
        List<Integer> seconds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long second = count == 1 ? 0 : Math.round((duration - 1) * (double) i / (double) (count - 1));
            seconds.add((int) Math.max(0, second));
        }
        return seconds;
    }

    private String runCommand(List<String> command) throws Exception {
        return runProcess(new ProcessBuilder(command).redirectErrorStream(true),
                TimeUnit.MINUTES.toSeconds(PROCESS_TIMEOUT_MINUTES), "命令执行：" + String.join(" ", command));
    }

    private String runProcess(ProcessBuilder builder, long timeoutSeconds, String action) throws Exception {
        Process process = builder.start();
        CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                () -> new String(IoUtil.readBytes(process.getInputStream()), StandardCharsets.UTF_8));
        boolean finished = process.waitFor(Math.max(30L, timeoutSeconds), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(action + "超时，请检查 TikTok/抖音访问网络或配置 TK_REFERENCE_DOWNLOAD_PROXY");
        }
        String output = outputFuture.get(5, TimeUnit.SECONDS);
        if (process.exitValue() != 0) {
            throw new IllegalStateException(action + "失败：" + scriptError(output));
        }
        return output;
    }

    private Path requiredFile(String rawPath, String message) {
        Path path = optionalPath(rawPath);
        if (path == null) {
            throw new IllegalStateException(message);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("视频下载脚本文件不存在：" + path);
        }
        return path;
    }

    private Path optionalPath(String rawPath) {
        if (StrUtil.isBlank(rawPath)) {
            return null;
        }
        Path path = Paths.get(rawPath);
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    private long scriptTimeoutSeconds() {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        Integer seconds = config == null ? null : config.getScriptTimeoutSeconds();
        return seconds == null ? 180L : Math.max(30L, seconds.longValue());
    }

    private boolean isHtmlFallbackEnabled() {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        return config == null || !Boolean.FALSE.equals(config.getHtmlFallbackEnabled());
    }

    private int htmlTimeoutMillis() {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        Integer seconds = config == null ? null : config.getHtmlTimeoutSeconds();
        return seconds == null ? DEFAULT_HTML_TIMEOUT_MILLIS : (int) TimeUnit.SECONDS.toMillis(Math.max(5L, seconds.longValue()));
    }

    private String referenceDownloadProxy() {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        return config == null ? null : StrUtil.trimToNull(config.getProxy());
    }

    private File resolveDownloadedFile(String rawPath, File taskDir) {
        if (StrUtil.isBlank(rawPath)) {
            throw new IllegalStateException("视频下载脚本未返回下载文件路径");
        }
        Path tempPath = taskDir.toPath().toAbsolutePath().normalize();
        Path downloaded = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!downloaded.startsWith(tempPath) || !Files.isRegularFile(downloaded)) {
            throw new IllegalStateException("视频下载脚本下载文件不存在：" + downloaded);
        }
        try {
            if (Files.size(downloaded) <= 0) {
                throw new IllegalStateException("视频下载脚本下载文件为空：" + downloaded);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("视频下载脚本下载文件不可读取：" + downloaded, ex);
        }
        return downloaded.toFile();
    }

    private boolean isSupportedVideoFile(File file) {
        String lowerName = file.getName().toLowerCase();
        return lowerName.endsWith(".mp4") || lowerName.endsWith(".mov")
                || lowerName.endsWith(".webm") || lowerName.endsWith(".m4v");
    }

    private String extractScriptVideoUrl(JsonNode root) {
        JsonNode media = root.path("data").path("media");
        return firstNotBlank(
                media.path("no_watermark_url_hq").asText(null),
                media.path("no_watermark_url").asText(null),
                media.path("watermark_url_hq").asText(null),
                media.path("watermark_url").asText(null));
    }

    private String jsonPayload(String output) {
        String normalized = StrUtil.trimToEmpty(output);
        int start = normalized.indexOf('{');
        int end = normalized.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("视频下载脚本未返回 JSON 结果");
        }
        return normalized.substring(start, end + 1);
    }

    private String scriptError(String output) {
        String readable = lastErrorLine(output);
        String normalized = logText(output);
        if (StrUtil.isBlank(normalized)) {
            return "没有错误输出";
        }
        if (isNetworkTimeoutText(readable) || isNetworkTimeoutText(normalized)) {
            return "平台请求连接超时，请确认后端机器可访问 TikTok/抖音，或配置 TK_REFERENCE_DOWNLOAD_PROXY=http://host:port 后重试";
        }
        if (containsAnyIgnoreCase(normalized, "ConnectionError", "ConnectError", "APIConnectionError")) {
            return "平台请求连接失败，请检查网络、代理或平台风控状态";
        }
        if (StrUtil.contains(readable, "未在响应的地址中找到 aweme_id")) {
            return "未识别到有效抖音作品 ID，请粘贴真实公开视频作品页链接后重试";
        }
        if (StrUtil.contains(readable, "检查链接是否为作品页")) {
            return "链接不是可解析的视频作品页，请换成公开视频作品页链接";
        }
        if (StrUtil.contains(normalized, "Missing dependency") || StrUtil.contains(normalized, "No module named")) {
            return "Python 下载依赖缺失，请先安装 Douyin_TikTok_Download_API requirements";
        }
        if (StrUtil.isNotBlank(readable)) {
            return truncate(readable, 220);
        }
        return truncate(normalized, 300);
    }

    private String lastErrorLine(String output) {
        String[] lines = output == null ? new String[0] : output.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = StrUtil.trimToEmpty(lines[index]);
            if (StrUtil.isBlank(line)) {
                continue;
            }
            int colon = line.lastIndexOf(':');
            if (colon >= 0 && colon < line.length() - 1) {
                String detail = StrUtil.trimToEmpty(line.substring(colon + 1));
                if (StrUtil.isNotBlank(detail)) {
                    return detail;
                }
            }
            if (!line.startsWith("File ") && !line.startsWith("Traceback")) {
                return line;
            }
        }
        return "";
    }

    private String logText(String output) {
        return StrUtil.trimToEmpty(output).replaceAll("\\s+", " ");
    }

    private boolean isNetworkTimeoutText(String text) {
        return containsAnyIgnoreCase(StrUtil.trimToEmpty(text),
                "Connection timed out", "Read timed out", "ConnectException", "TimeoutError", "timed out");
    }

    private boolean containsAnyIgnoreCase(String text, String... patterns) {
        for (String pattern : patterns) {
            if (StrUtil.containsIgnoreCase(text, pattern)) {
                return true;
            }
        }
        return false;
    }

    private String networkError(String action, String url, Exception ex) {
        String message = StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName());
        if (isNetworkTimeoutText(message)) {
            return StrUtil.format("{}：连接平台超时，请确认后端机器可访问该地址，或配置 TK_REFERENCE_DOWNLOAD_PROXY=http://host:port。URL：{}",
                    action, url);
        }
        return StrUtil.format("{}：{}，URL：{}", action, truncate(message, 180), url);
    }

    private HttpRequest withOptionalProxy(HttpRequest request) {
        Proxy proxy = buildProxy(referenceDownloadProxy());
        return proxy == null ? request : request.setProxy(proxy);
    }

    private void validateRemoteUrl(String url) {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        if (config != null && Boolean.FALSE.equals(config.getRemoteUrlValidationEnabled())) {
            return;
        }
        new TkSafeRemoteUrlValidator(config == null ? null : config.getAllowedHosts(),
                config == null || !Boolean.FALSE.equals(config.getBlockPrivateAddress())).validate(url);
    }

    private HttpResponse executeSafeGet(String url, int timeoutMillis) {
        TkGenerationProperties.ReferenceDownload config = generationProperties.getReferenceDownload();
        int maxRedirects = config == null || config.getMaxRedirects() == null
                ? 3 : Math.max(0, config.getMaxRedirects());
        String current = url;
        for (int redirect = 0; redirect <= maxRedirects; redirect++) {
            validateRemoteUrl(current);
            HttpResponse response = withOptionalProxy(HttpRequest.get(current)
                    .header("User-Agent", "Mozilla/5.0")
                    .setFollowRedirects(false)
                    .timeout(timeoutMillis)).execute();
            int status = response.getStatus();
            if (status < 300 || status >= 400) {
                return response;
            }
            String location = response.header("Location");
            response.close();
            if (StrUtil.isBlank(location) || redirect == maxRedirects) {
                throw new IllegalStateException("remote video redirect is invalid or exceeds limit");
            }
            current = URI.create(current).resolve(location).toString();
        }
        throw new IllegalStateException("remote video redirect exceeds limit");
    }

    private Proxy buildProxy(String proxyUrl) {
        if (StrUtil.isBlank(proxyUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(proxyUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            if (StrUtil.isBlank(host) || port <= 0) {
                throw new IllegalArgumentException("代理地址缺少 host 或 port");
            }
            Proxy.Type type = StrUtil.equalsIgnoreCase(uri.getScheme(), "socks")
                    || StrUtil.equalsIgnoreCase(uri.getScheme(), "socks5")
                    ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Exception ex) {
            throw new IllegalStateException("TK_REFERENCE_DOWNLOAD_PROXY 格式错误，请使用 http://host:port 或 socks5://host:port：" + proxyUrl, ex);
        }
    }

    private String truncate(String value, int maxLength) {
        String normalized = StrUtil.trimToEmpty(value);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private File resolveWorkDir() {
        String workDir = generationProperties.getFfmpeg().getWorkDir();
        if (StrUtil.isBlank(workDir)) {
            workDir = System.getProperty("java.io.tmpdir") + "/tk-generation";
        }
        return FileUtil.mkdir(workDir.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir")));
    }

    private String ffmpeg() {
        return TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
    }

    private String ffprobe() {
        return TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
    }

    private void prependFfmpegPath(ProcessBuilder builder) {
        String ffmpegDir = TkFfmpegExecutableResolver.parentDir(ffmpeg());
        if (StrUtil.isBlank(ffmpegDir)) {
            return;
        }
        String path = StrUtil.blankToDefault(builder.environment().get("PATH"), "");
        builder.environment().put("PATH", ffmpegDir + File.pathSeparator + path);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static class SourceVideo {

        private final File file;
        private final String resolvedVideoUrl;

        private SourceVideo(File file, String resolvedVideoUrl) {
            this.file = file;
            this.resolvedVideoUrl = resolvedVideoUrl;
        }

    }

    private static class NonVideoSourceException extends IllegalStateException {

        private NonVideoSourceException(String message) {
            super(message);
        }

    }

    private static class VideoMetadata {

        private final Long durationSeconds;
        private final String resolution;

        private VideoMetadata(Long durationSeconds, String resolution) {
            this.durationSeconds = durationSeconds;
            this.resolution = resolution;
        }

    }

}
