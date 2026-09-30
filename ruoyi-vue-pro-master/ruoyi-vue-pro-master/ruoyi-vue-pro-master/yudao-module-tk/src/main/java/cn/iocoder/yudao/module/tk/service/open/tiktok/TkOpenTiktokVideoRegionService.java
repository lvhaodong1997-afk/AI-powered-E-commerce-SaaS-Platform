package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokAuthVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class TkOpenTiktokVideoRegionService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._]{1,32}");
    private static final Pattern VIDEO_PATH = Pattern.compile("/@([A-Za-z0-9._]+)/video/([0-9]{15,20})");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_VIDEOS = 3;
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 500;
    private static final long CACHE_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private final PageFetcher pageFetcher;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public TkOpenTiktokVideoRegionService(
            @Value("${tk.open-api.tiktok-page-proxy:${TK_OPEN_API_TIKTOK_PAGE_PROXY:${TK_REFERENCE_DOWNLOAD_PROXY:}}}")
            String proxyUrl) {
        this.pageFetcher = url -> fetchPage(url, proxyUrl);
    }

    TkOpenTiktokVideoRegionService(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    public TkOpenTiktokAuthVO.VideoRegionResp lookup(String username) {
        TkOpenTiktokAuthVO.VideoRegionResp result = new TkOpenTiktokAuthVO.VideoRegionResp();
        result.setStatus("UNAVAILABLE");
        result.setFetchedAt(LocalDateTime.now());
        if (username == null || !USERNAME.matcher(username).matches()) {
            return result;
        }
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        CacheEntry cached = cache.get(normalizedUsername);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.region;
        }
        try {
            String embedHtml = pageFetcher.fetch("https://www.tiktok.com/embed/@" + normalizedUsername);
            if (StrUtil.isBlank(embedHtml)) {
                return result;
            }
            Set<String> seenVideoIds = new HashSet<>();
            for (Element link : Jsoup.parse(embedHtml).select("a[href]")) {
                URI uri;
                try {
                    uri = URI.create(link.attr("href"));
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || !"www.tiktok.com".equalsIgnoreCase(uri.getHost())) {
                    continue;
                }
                Matcher match = VIDEO_PATH.matcher(uri.getPath());
                if (!match.matches() || !normalizedUsername.equalsIgnoreCase(match.group(1))
                        || !seenVideoIds.add(match.group(2))) {
                    continue;
                }
                String videoId = match.group(2);
                String videoUrl = "https://www.tiktok.com/@" + normalizedUsername + "/video/" + videoId;
                String locationCreated;
                try {
                    locationCreated = parseVideoRegion(pageFetcher.fetch(videoUrl), normalizedUsername, videoId);
                } catch (Exception ex) {
                    log.debug("[lookupVideoRegion][videoId({}) unavailable: {}]", videoId, ex.toString());
                    locationCreated = null;
                }
                if (locationCreated != null) {
                    result.setStatus("AVAILABLE");
                    result.setLocationCreated(locationCreated);
                    result.setCountryName(new Locale("", locationCreated).getDisplayCountry(Locale.SIMPLIFIED_CHINESE));
                    result.setSourceVideoId(videoId);
                    result.setSourceVideoUrl(videoUrl);
                    if (cache.size() >= MAX_CACHE_ENTRIES) {
                        cache.clear();
                    }
                    cache.put(normalizedUsername, new CacheEntry(result, System.currentTimeMillis() + CACHE_MILLIS));
                    return result;
                }
                if (seenVideoIds.size() >= MAX_VIDEOS) {
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("[lookupVideoRegion][username({}) failed: {}]", normalizedUsername, ex.toString());
        }
        return result;
    }

    private String parseVideoRegion(String html, String username, String videoId) throws IOException {
        if (StrUtil.isBlank(html)) {
            return null;
        }
        Element script = Jsoup.parse(html).selectFirst("script#__UNIVERSAL_DATA_FOR_REHYDRATION__");
        if (script == null) {
            return null;
        }
        JsonNode item = OBJECT_MAPPER.readTree(script.data())
                .path("__DEFAULT_SCOPE__").path("webapp.video-detail")
                .path("itemInfo").path("itemStruct");
        String author = item.path("author").path("uniqueId").asText();
        String location = item.path("locationCreated").asText();
        if (!username.equalsIgnoreCase(author) || !videoId.equals(item.path("id").asText())
                || !location.matches("[A-Z]{2}")) {
            return null;
        }
        return location;
    }

    private static String fetchPage(String url, String proxyUrl) throws IOException {
        Proxy proxy = parseProxy(proxyUrl);
        HttpURLConnection connection = (HttpURLConnection) (proxy == Proxy.NO_PROXY
                ? new URL(url).openConnection() : new URL(url).openConnection(proxy));
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    if (output.size() + read > MAX_PAGE_BYTES) {
                        throw new IOException("TikTok public page exceeds size limit");
                    }
                    output.write(buffer, 0, read);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Proxy parseProxy(String proxyUrl) throws IOException {
        if (StrUtil.isBlank(proxyUrl)) {
            return Proxy.NO_PROXY;
        }
        try {
            URI uri = URI.create(proxyUrl.trim());
            Proxy.Type type = "socks5".equalsIgnoreCase(uri.getScheme())
                    ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            if ((!"http".equalsIgnoreCase(uri.getScheme()) && type != Proxy.Type.SOCKS)
                    || StrUtil.isBlank(uri.getHost()) || uri.getPort() < 1) {
                throw new IllegalArgumentException("invalid proxy URL");
            }
            return new Proxy(type, new InetSocketAddress(uri.getHost(), uri.getPort()));
        } catch (IllegalArgumentException ex) {
            throw new IOException("invalid TikTok public page proxy", ex);
        }
    }

    @FunctionalInterface
    interface PageFetcher {
        String fetch(String url) throws IOException;
    }

    private static class CacheEntry {
        private final TkOpenTiktokAuthVO.VideoRegionResp region;
        private final long expiresAt;

        private CacheEntry(TkOpenTiktokAuthVO.VideoRegionResp region, long expiresAt) {
            this.region = region;
            this.expiresAt = expiresAt;
        }
    }
}
