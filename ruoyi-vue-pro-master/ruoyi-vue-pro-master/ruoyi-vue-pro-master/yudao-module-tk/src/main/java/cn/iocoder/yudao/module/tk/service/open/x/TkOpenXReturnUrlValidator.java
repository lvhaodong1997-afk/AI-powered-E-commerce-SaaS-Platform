package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;

import java.net.IDN;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Strict browser returnUrl matching: exact scheme, host, port and path.
 * Rejects userinfo, fragment, query on the configured or requested URL,
 * non-HTTPS (unless an explicit loopback dev exception is enabled),
 * suffix wildcards, and custom schemes. Query string is never taken from
 * the client; only caller-supplied opaque parameters are appended.
 */
public final class TkOpenXReturnUrlValidator {

    private TkOpenXReturnUrlValidator() {
    }

    public static URI requireWhitelisted(String requested, List<String> whitelist, boolean allowInsecureLoopback) {
        URI normalized = normalize(requested, allowInsecureLoopback);
        if (whitelist == null || whitelist.isEmpty()) {
            throw TkOpenApiException.badRequest("RETURN_URL_NOT_ALLOWED", "returnUrl is not in the client whitelist");
        }
        for (String entry : whitelist) {
            if (entry == null || entry.trim().isEmpty()) continue;
            try {
                if (Objects.equals(key(normalized), key(normalize(entry, allowInsecureLoopback)))) {
                    return normalized;
                }
            } catch (TkOpenApiException ignored) {
                // Skip invalid whitelist rows; they cannot authorize a redirect.
            }
        }
        throw TkOpenApiException.badRequest("RETURN_URL_NOT_ALLOWED", "returnUrl is not in the client whitelist");
    }

    public static URI normalize(String raw, boolean allowInsecureLoopback) {
        if (raw == null || raw.trim().isEmpty() || raw.indexOf(' ') >= 0 || raw.indexOf('\\') >= 0) {
            throw invalid("returnUrl is invalid");
        }
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (Exception ex) {
            throw invalid("returnUrl is invalid");
        }
        if (uri.isOpaque() || uri.getScheme() == null || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getUserInfo() != null
                || uri.getRawFragment() != null || uri.getFragment() != null
                || uri.getRawQuery() != null || uri.getQuery() != null) {
            throw invalid("returnUrl must be an absolute HTTPS URL without userinfo, query or fragment");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host;
        try {
            host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            throw invalid("returnUrl host is invalid");
        }
        if (host.isEmpty() || host.contains("*") || host.startsWith(".") || host.endsWith(".")
                || host.contains("..") || host.contains("%")) {
            throw invalid("returnUrl host is invalid");
        }
        boolean loopback = isLoopback(host);
        if ("https".equals(scheme)) {
            // HTTPS websites and HTTPS App Links only.
        } else if ("http".equals(scheme) && allowInsecureLoopback && loopback) {
            // Documented development exception, off by default.
        } else {
            throw invalid("returnUrl must use HTTPS");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (!path.startsWith("/") || path.contains("\\") || path.contains("//")
                || path.contains("%2f") || path.contains("%2F") || path.contains("%5c") || path.contains("%5C")) {
            throw invalid("returnUrl path is invalid");
        }
        URI normalized;
        try {
            int port = uri.getPort();
            if (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) port = -1;
            normalized = new URI(scheme, null, host, port, path, null, null).normalize();
        } catch (Exception ex) {
            throw invalid("returnUrl is invalid");
        }
        if (!path.equals(normalized.getRawPath()) && !path.equals(normalized.getPath())) {
            throw invalid("returnUrl path is invalid");
        }
        if (normalized.getPath() != null && normalized.getPath().contains("..")) {
            throw invalid("returnUrl path is invalid");
        }
        return normalized;
    }

    public static String redirect(URI allowed, Map<String, String> params) {
        if (allowed == null) throw invalid("returnUrl is invalid");
        StringBuilder query = new StringBuilder();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,31}")) {
                    throw invalid("return query is invalid");
                }
                if (entry.getValue() == null || !entry.getValue().matches("[A-Za-z0-9._~-]{1,256}")) {
                    throw invalid("return query is invalid");
                }
                if (query.length() > 0) query.append('&');
                query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        try {
            return new URI(allowed.getScheme(), null, allowed.getHost(), allowed.getPort(),
                    allowed.getPath(), query.length() == 0 ? null : query.toString(), null).toString();
        } catch (Exception ex) {
            throw invalid("returnUrl is invalid");
        }
    }

    public static Map<String, String> ticketParams(String ticket, boolean success) {
        Map<String, String> params = new LinkedHashMap<>();
        if (ticket != null) params.put("ticket", ticket);
        if (!success) params.put("error", "auth_failed");
        return params;
    }

    public static String key(URI uri) {
        int port = uri.getPort();
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getScheme() + "://" + uri.getHost() + (port == -1 ? "" : ":" + port) + path;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "localhost.".equals(host)
                || "[::1]".equals(host) || "::1".equals(host);
    }

    private static TkOpenApiException invalid(String message) {
        return TkOpenApiException.badRequest("RETURN_URL_INVALID", message);
    }
}