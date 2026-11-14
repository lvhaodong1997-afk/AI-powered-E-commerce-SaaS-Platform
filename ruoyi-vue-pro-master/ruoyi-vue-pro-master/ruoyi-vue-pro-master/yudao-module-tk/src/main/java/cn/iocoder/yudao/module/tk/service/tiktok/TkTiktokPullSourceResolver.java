package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.reference.TkSafeRemoteUrlValidator;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.concurrent.ExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional public OSS pull mapping. Ownership must be checked by the publishing caller. */
@Component
public class TkTiktokPullSourceResolver {
    private static final String PROVIDER = "TIKTOK";
    private static final String ORIGIN = "tk-video-create.oss-cn-beijing.aliyuncs.com";
    private static final String BASE = "https://tkpublish.fnn.net.cn";
    private static final long MAX_SIZE = 4_000_000_000L;
    public static final long MIN_PULL_VALIDITY_SECONDS = 3600;
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes 0-0/([0-9]+)");
    private static final ScheduledExecutorService PREFLIGHT_DEADLINE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tk-pull-preflight-deadline"); thread.setDaemon(true); return thread;
    });
    // These affect authorization or the download filename, never the object bytes/version.
    // A stripped URL must still pass anonymous HEAD; no signed access is carried across hosts.
    private static final Set<String> REMOVABLE_SOURCE_QUERY_PARAMETERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("OSSAccessKeyId", "Expires", "Signature", "security-token",
                    "x-oss-signature-version", "x-oss-credential", "x-oss-date", "x-oss-expires",
                    "x-oss-signature", "x-oss-security-token", "x-oss-additional-headers",
                    "response-content-disposition")));
    private final TkApiKeyConfigService config;
    private final PublicUrlValidator validator;
    private final ConnectionFactory connections;
    private final Cache<String, PullDecision> metadataCache;

    @Autowired
    public TkTiktokPullSourceResolver(TkApiKeyConfigService config) {
        this(config, uri -> new TkSafeRemoteUrlValidator(Collections.singleton(uri.getHost()), true)
                        .validate(uri.toASCIIString()),
                uri -> (HttpURLConnection) uri.toURL().openConnection());
    }

    TkTiktokPullSourceResolver(TkApiKeyConfigService config, PublicUrlValidator validator,
                              ConnectionFactory connections) {
        this(config, validator, connections, Ticker.systemTicker());
    }

    TkTiktokPullSourceResolver(TkApiKeyConfigService config, PublicUrlValidator validator,
                              ConnectionFactory connections, Ticker ticker) {
        this.config = config;
        this.validator = validator;
        this.connections = connections;
        this.metadataCache = CacheBuilder.newBuilder().maximumSize(1024)
                .expireAfterWrite(20, java.util.concurrent.TimeUnit.SECONDS).ticker(ticker).build();
    }

    /** Pure configuration/URI check: deliberately performs no DNS or HTTP IO. */
    public boolean isCandidate(String originalUrl, Long accountId, String verifiedProperty) {
        return candidate(originalUrl, accountId, verifiedProperty).isEligible();
    }

    /** Opt in per existing account allowlist; persisted V1 tasks keep their original behavior. */
    public boolean isStrictEnabled() {
        return "true".equalsIgnoreCase(value("pull-strict-enabled", "false"));
    }

    public boolean isStrictCandidate(String raw, Long accountId, String property) {
        PullDecision result = candidate(raw, accountId, property, true);
        return result.isEligible() || Arrays.asList("UNSUPPORTED_QUERY_PARAMETERS", "SIGNED_HEADERS_UNSUPPORTED",
                "PROPERTY_NOT_VERIFIED").contains(result.getReasonCode());
    }

    public String pullBaseUrl() {
        return value("pull-base-url", BASE);
    }

    public PullDecision resolveStrict(String raw, Long accountId, String property) {
        PullDecision candidate = candidate(raw, accountId, property, true);
        if (!candidate.isEligible()) return candidate;
        String lifetimeFailure = lifetimeFailure(candidate.getPullUrl(), MIN_PULL_VALIDITY_SECONDS);
        if (lifetimeFailure != null) return PullDecision.fallback(lifetimeFailure);
        Long tenantId = TenantContextHolder.getTenantId();
        // Candidate/lifetime checks run even on hits. Full authorization is part of the key.
        if (tenantId == null) return get(candidate.getPullUrl());
        try {
            return metadataCache.get("GET|" + tenantId + "|" + candidate.getPullUrl(), () -> {
                PullDecision result = get(candidate.getPullUrl());
                if (!result.isEligible()) throw new HeadFailure(result);
                return result;
            });
        } catch (ExecutionException ex) {
            return ex.getCause() instanceof HeadFailure ? ((HeadFailure) ex.getCause()).decision
                    : PullDecision.fallback("PULL_TEMPORARILY_UNAVAILABLE");
        }
    }

    /** No IO: scheduled external links must remain valid through execution and platform download. */
    public static String lifetimeFailure(String url, long minimumSeconds) {
        try {
            Map<String, String> parameters = queryParameters(URI.create(url));
            long expires;
            if (parameters.containsKey("Signature") || parameters.containsKey("OSSAccessKeyId") || parameters.containsKey("Expires")) {
                if (!parameters.keySet().containsAll(Arrays.asList("Signature", "OSSAccessKeyId", "Expires")))
                    return "INVALID_PULL_SIGNATURE";
                expires = Long.parseLong(parameters.get("Expires"));
            } else if (parameters.containsKey("x-oss-signature")) {
                if (!parameters.keySet().containsAll(Arrays.asList("x-oss-signature-version", "x-oss-date", "x-oss-expires", "x-oss-credential"))
                        || !"OSS4-HMAC-SHA256".equals(parameters.get("x-oss-signature-version")))
                    return "INVALID_PULL_SIGNATURE";
                long duration = Long.parseLong(parameters.get("x-oss-expires"));
                if (duration <= 0 || duration > 604800) return "INVALID_PULL_SIGNATURE";
                expires = Math.addExact(LocalDateTime.parse(parameters.get("x-oss-date"),
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")).toEpochSecond(ZoneOffset.UTC), duration);
            } else {
                if (parameters.keySet().stream().anyMatch(k -> k.startsWith("x-oss-") || "security-token".equals(k)))
                    return "INVALID_PULL_SIGNATURE";
                return null; // A public object is still required to pass the anonymous GET probe.
            }
            return expires - Instant.now().getEpochSecond() < minimumSeconds ? "PULL_URL_EXPIRED" : null;
        } catch (Exception ex) {
            return "INVALID_PULL_SIGNATURE";
        }
    }

    private PullDecision get(String pullUrl) {
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        try {
            URI target = URI.create(pullUrl);
            validator.validate(target);
            connection = connections.open(target);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setRequestProperty("Accept-Encoding", "identity");
            HttpURLConnection active = connection;
            deadline = PREFLIGHT_DEADLINE.schedule(active::disconnect, 10, TimeUnit.SECONDS);
            int status = connection.getResponseCode();
            if (status == 403 || status == 401) return PullDecision.fallback("PULL_ACCESS_DENIED");
            if (status == 404) return PullDecision.fallback("PULL_OBJECT_NOT_FOUND");
            if (status == 408 || status == 429 || status >= 500) return PullDecision.fallback("PULL_TEMPORARILY_UNAVAILABLE");
            if (status != 200 && status != 206) return PullDecision.fallback("PULL_HTTP_STATUS_" + status);
            long size;
            if (status == 206) {
                Matcher range = CONTENT_RANGE.matcher(String.valueOf(connection.getHeaderField("Content-Range")));
                if (!range.matches()) return PullDecision.fallback("INVALID_CONTENT_RANGE");
                size = Long.parseLong(range.group(1));
            } else size = connection.getContentLengthLong();
            if (size <= 0 || size > MAX_SIZE) return PullDecision.fallback("INVALID_FILE_SIZE");
            String mime = connection.getContentType();
            if (mime != null && !mime.trim().isEmpty()) {
                mime = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (!mime.startsWith("video/") && !"application/octet-stream".equals(mime))
                    return PullDecision.fallback("INVALID_CONTENT_TYPE");
            }
            try (InputStream input = connection.getInputStream()) {
                // Never buffer a whole response when an origin ignores Range.
                if (input.read(new byte[status == 206 ? 1 : 1024]) <= 0)
                    return PullDecision.fallback("EMPTY_PULL_RESPONSE");
            }
            return PullDecision.eligible(pullUrl, size);
        } catch (IllegalArgumentException ex) {
            return PullDecision.fallback("PUBLIC_URL_REJECTED");
        } catch (Exception ex) {
            return PullDecision.fallback("PULL_TEMPORARILY_UNAVAILABLE");
        } finally {
            if (deadline != null) deadline.cancel(false);
            if (connection != null) connection.disconnect();
        }
    }

    public PullDecision resolve(String originalUrl, Long accountId, String verifiedProperty) {
        PullDecision candidate = candidate(originalUrl, accountId, verifiedProperty);
        if (!candidate.isEligible()) return candidate;
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null) {
            String cacheKey = tenantId + "|" + candidate.getPullUrl();
            try {
                return metadataCache.get(cacheKey, () -> {
                    PullDecision result = head(candidate.getPullUrl());
                    // Failed loads are coalesced only while in-flight, never retained by Guava.
                    if (!result.isEligible()) throw new HeadFailure(result);
                    return result;
                });
            } catch (ExecutionException ex) {
                return ex.getCause() instanceof HeadFailure
                        ? ((HeadFailure) ex.getCause()).decision : PullDecision.fallback("HEAD_FAILED");
            }
        }
        return head(candidate.getPullUrl());
    }

    private PullDecision head(String pullUrl) {
        return head(pullUrl, false);
    }

    /** Photo URLs have the same ownership and account gates as video, but cannot fall back to FILE_UPLOAD. */
    public PullDecision resolvePhoto(String originalUrl, Long accountId, String verifiedProperty) {
        PullDecision candidate = candidate(originalUrl, accountId, verifiedProperty);
        return candidate.isEligible() ? head(candidate.getPullUrl(), true) : candidate;
    }

    private PullDecision head(String pullUrl, boolean photo) {
        HttpURLConnection connection = null;
        try {
            URI target = URI.create(pullUrl);
            validator.validate(target);
            connection = connections.open(target);
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            if (connection.getResponseCode() != 200) return PullDecision.fallback("HEAD_STATUS");
            long size = connection.getContentLengthLong();
            if (size <= 0 || size > (photo ? 20L * 1024 * 1024 : MAX_SIZE)) return PullDecision.fallback("INVALID_FILE_SIZE");
            String mime = connection.getContentType();
            if (mime != null && !mime.trim().isEmpty()) {
                mime = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (photo ? !Arrays.asList("image/jpeg", "image/webp").contains(mime)
                        : !mime.startsWith("video/") && !"application/octet-stream".equals(mime)) {
                    return PullDecision.fallback("INVALID_CONTENT_TYPE");
                }
            }
            return PullDecision.eligible(pullUrl, size);
        } catch (IllegalArgumentException ex) {
            return PullDecision.fallback("PUBLIC_URL_REJECTED");
        } catch (Exception ex) {
            return PullDecision.fallback("HEAD_FAILED");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private PullDecision candidate(String raw, Long accountId, String property) {
        return candidate(raw, accountId, property, false);
    }

    private PullDecision candidate(String raw, Long accountId, String property, boolean signed) {
        try {
            if (!"true".equalsIgnoreCase(value("pull-from-url-enabled", "false"))) {
                return PullDecision.fallback("DISABLED");
            }
            String allowlist = value("pull-account-allowlist", "");
            if (accountId == null || !Arrays.stream(allowlist.split(","))
                    .map(String::trim).anyMatch(item -> "*".equals(item) || accountId.toString().equals(item))) {
                return PullDecision.fallback("ACCOUNT_NOT_ALLOWED");
            }
            URI source = safeUri(raw, true);
            URI base = safeUri(value("pull-base-url", BASE));
            if (base.getRawPath() != null && !base.getRawPath().isEmpty() && !"/".equals(base.getRawPath())) {
                return PullDecision.fallback("INVALID_BASE_URL");
            }
            String origin = value("pull-origin-host", ORIGIN).toLowerCase(Locale.ROOT);
            if (!source.getHost().equalsIgnoreCase(origin) && !source.getHost().equalsIgnoreCase(base.getHost())) {
                return PullDecision.fallback("SOURCE_HOST_MISMATCH");
            }
            if (!hasOnlyRemovableQueryParameters(source)) {
                return PullDecision.fallback("UNSUPPORTED_QUERY_PARAMETERS");
            }
            String path = source.getRawPath();
            if (path == null || path.isEmpty() || "/".equals(path)) return PullDecision.fallback("EMPTY_OBJECT_PATH");
            Map<String, String> parameters = queryParameters(source);
            String signedHeaders = parameters.get("x-oss-additional-headers");
            if (signed && signedHeaders != null && (!"host".equals(signedHeaders)
                    || !source.getHost().equalsIgnoreCase(base.getHost())))
                return PullDecision.fallback("SIGNED_HEADERS_UNSUPPORTED");
            URI target = URI.create("https://" + base.getHost() + path
                    + (signed && source.getRawQuery() != null ? "?" + source.getRawQuery() : ""));
            if (!covers(property, target)) return PullDecision.fallback("PROPERTY_NOT_VERIFIED");
            return PullDecision.eligible(target.toString(), 0);
        } catch (Exception ex) {
            return PullDecision.fallback("INVALID_SOURCE_OR_CONFIG");
        }
    }

    private String value(String key, String fallback) {
        String value = config.getValueOrDefault(PROVIDER, key, fallback);
        return value == null ? fallback : value.trim();
    }

    private static URI safeUri(String raw) {
        return safeUri(raw, false);
    }

    private static URI safeUri(String raw, boolean allowQuery) {
        URI uri = URI.create(raw);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getPort() != -1
                || (!allowQuery && uri.getRawQuery() != null) || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Unsafe pull URL");
        }
        String path = uri.getRawPath();
        if (path != null) {
            String lower = path.toLowerCase(Locale.ROOT);
            // Reject nested escapes and encoded separators/dots before prefix matching or routing.
            if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                    || lower.contains("%25") || lower.contains("\\") || lower.contains("//")) {
                throw new IllegalArgumentException("Ambiguous object path");
            }
            for (String segment : path.split("/")) {
                if (".".equals(segment) || "..".equals(segment)) throw new IllegalArgumentException("Traversal");
            }
        }
        return uri;
    }

    private static boolean hasOnlyRemovableQueryParameters(URI source) {
        String query = source.getRawQuery();
        if (query == null) return true;
        Set<String> seen = new HashSet<>();
        for (String parameter : query.split("&", -1)) {
            int separator = parameter.indexOf('=');
            if (separator <= 0 || separator == parameter.length() - 1) return false;
            String name = parameter.substring(0, separator);
            // Match exact raw names; encoded, duplicate and unknown names retain the old upload path.
            if (!REMOVABLE_SOURCE_QUERY_PARAMETERS.contains(name) || !seen.add(name)) return false;
        }
        return true;
    }

    private static Map<String, String> queryParameters(URI source) {
        Map<String, String> result = new HashMap<>();
        if (source.getRawQuery() != null) for (String parameter : source.getRawQuery().split("&")) {
            int separator = parameter.indexOf('=');
            if (separator <= 0 || result.put(parameter.substring(0, separator), parameter.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Invalid query");
        }
        return result;
    }

    private static boolean covers(String rawProperty, URI target) {
        if (rawProperty == null || rawProperty.trim().isEmpty()) return false;
        String property = rawProperty.trim();
        if (property.contains("://")) {
            URI prefix = safeUri(property);
            if (!prefix.getHost().equalsIgnoreCase(target.getHost())) return false;
            String path = prefix.getRawPath();
            if (path == null || path.isEmpty() || "/".equals(path)) return true;
            String targetPath = target.getRawPath();
            return targetPath.equals(path) || targetPath.startsWith(path.endsWith("/") ? path : path + "/");
        }
        String domain = property.toLowerCase(Locale.ROOT);
        if (!domain.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) return false;
        String targetHost = target.getHost().toLowerCase(Locale.ROOT);
        return targetHost.equals(domain) || targetHost.endsWith("." + domain);
    }

    private static final class HeadFailure extends Exception {
        private final PullDecision decision;
        private HeadFailure(PullDecision decision) { this.decision = decision; }
    }

    @FunctionalInterface
    interface PublicUrlValidator { void validate(URI uri); }
    @FunctionalInterface
    interface ConnectionFactory { HttpURLConnection open(URI uri) throws IOException; }

    public static final class PullDecision {
        private final boolean eligible;
        private final String pullUrl;
        private final long fileSize;
        private final String reasonCode;
        private PullDecision(boolean eligible, String pullUrl, long fileSize, String reasonCode) {
            this.eligible = eligible;
            this.pullUrl = pullUrl;
            this.fileSize = fileSize;
            this.reasonCode = reasonCode;
        }
        public static PullDecision eligible(String url, long size) { return new PullDecision(true, url, size, "ELIGIBLE"); }
        public static PullDecision fallback(String reason) { return new PullDecision(false, null, 0, reason); }
        public boolean isEligible() { return eligible; }
        public String getPullUrl() { return pullUrl; }
        public long getFileSize() { return fileSize; }
        public String getReasonCode() { return reasonCode; }
    }
}
