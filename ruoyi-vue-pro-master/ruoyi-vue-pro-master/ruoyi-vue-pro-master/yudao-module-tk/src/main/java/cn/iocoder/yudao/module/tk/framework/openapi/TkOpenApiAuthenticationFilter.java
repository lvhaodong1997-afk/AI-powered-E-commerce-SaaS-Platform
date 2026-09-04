package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiRequestLogDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiRequestLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

@Slf4j
public class TkOpenApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_CLIENT_ID = "X-TK-Client-Id";
    public static final String HEADER_TIMESTAMP = "X-TK-Timestamp";
    public static final String HEADER_NONCE = "X-TK-Nonce";
    public static final String HEADER_REQUEST_ID = "X-TK-Request-Id";
    public static final String HEADER_SIGNATURE = "X-TK-Signature";

    private final TkOpenApiAuthenticationService authenticationService;
    private final TkOpenApiRequestLogMapper requestLogMapper;
    private final int maxSignedBodyBytes;

    public TkOpenApiAuthenticationFilter(TkOpenApiAuthenticationService authenticationService,
                                         TkOpenApiRequestLogMapper requestLogMapper,
                                         @Value("${tk.open-api.max-signed-body-bytes:8388608}") int maxSignedBodyBytes) {
        this.authenticationService = authenticationService;
        this.requestLogMapper = requestLogMapper;
        this.maxSignedBodyBytes = maxSignedBodyBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.contains("/tk/open/v1/tiktok/")
                || uri.endsWith("/auth/callback") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long started = System.currentTimeMillis();
        String requestId = resolveRequestId(request.getHeader(HEADER_REQUEST_ID));
        String clientId = request.getHeader(HEADER_CLIENT_ID);
        String requestTarget = requestTarget(request);
        String clientIp = resolveClientIp(request);
        String errorCode = null;
        response.setHeader(HEADER_REQUEST_ID, requestId);
        try {
            TkOpenApiCachedBodyRequest cachedRequest = cacheBody(request);
            TkOpenApiPrincipal principal = authenticationService.authenticate(new TkOpenApiAuthRequest(
                    clientId, request.getHeader(HEADER_TIMESTAMP), request.getHeader(HEADER_NONCE),
                    request.getHeader(HEADER_SIGNATURE), request.getMethod(), requestTarget,
                    cachedRequest.getBody(), clientIp, requiredPermission(request.getRequestURI())));
            TkOpenApiContext.set(principal, requestId);
            filterChain.doFilter(cachedRequest, response);
        } catch (TkOpenApiException ex) {
            errorCode = ex.getCode();
            writeError(response, ex.getHttpStatus(), ex.getCode(), ex.getMessage(), requestId);
        } catch (Exception ex) {
            errorCode = "OPEN_API_INTERNAL_ERROR";
            log.error("[doFilterInternal][requestId({}) open API request failed]", requestId, ex);
            writeError(response, 500, errorCode, "internal server error", requestId);
        } finally {
            TkOpenApiContext.clear();
            saveRequestLog(requestId, clientId, request.getMethod(), requestTarget, response.getStatus(), errorCode,
                    System.currentTimeMillis() - started, clientIp);
        }
    }

    private TkOpenApiCachedBodyRequest cacheBody(HttpServletRequest request) throws IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxSignedBodyBytes) {
            throw new TkOpenApiException("OPEN_API_BODY_TOO_LARGE", "signed request body is too large", 413);
        }
        byte[] body = hasBody(request) ? readBody(request.getInputStream()) : new byte[0];
        return new TkOpenApiCachedBodyRequest(request, body);
    }

    private byte[] readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                (int) Math.min((long) maxSignedBodyBytes + 1L, 8192L));
        byte[] buffer = new byte[8192];
        while (output.size() <= maxSignedBodyBytes) {
            int remaining = (int) Math.min(buffer.length,
                    (long) maxSignedBodyBytes + 1L - output.size());
            int count = input.read(buffer, 0, remaining);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                int value = input.read();
                if (value < 0) {
                    break;
                }
                output.write(value);
            } else {
                output.write(buffer, 0, count);
            }
        }
        if (output.size() > maxSignedBodyBytes) {
            throw new TkOpenApiException("OPEN_API_BODY_TOO_LARGE", "signed request body is too large", 413);
        }
        return output.toByteArray();
    }

    private boolean hasBody(HttpServletRequest request) {
        return !("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (isLoopback(remoteAddress)) {
            String nginxRealIp = request.getHeader("X-Real-IP");
            if (nginxRealIp != null) {
                nginxRealIp = nginxRealIp.trim();
                if (!nginxRealIp.contains(",") && !nginxRealIp.contains("/")
                        && TkOpenApiIpMatcher.isValidRules(nginxRealIp)) {
                    return nginxRealIp;
                }
            }
        }
        return remoteAddress;
    }

    private boolean isLoopback(String address) {
        return "127.0.0.1".equals(address) || "0:0:0:0:0:0:0:1".equals(address) || "::1".equals(address);
    }

    private String requestTarget(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || query.isEmpty() ? "" : "?" + query);
    }

    private String requiredPermission(String uri) {
        if (uri.contains("/media/")) {
            return "media";
        }
        if (uri.contains("/publish/")) {
            return "publish";
        }
        return "auth";
    }

    private String resolveRequestId(String requested) {
        if (requested != null && requested.matches("[A-Za-z0-9._:-]{1,128}")) {
            return requested;
        }
        return TkOpenApiIds.next("req");
    }

    private void writeError(HttpServletResponse response, int status, String code, String message, String requestId) {
        response.setStatus(status);
        ServletUtils.writeJSON(response, TkOpenApiResponse.error(code, message, requestId));
    }

    private void saveRequestLog(String requestId, String clientId, String method, String target, int status,
                                String errorCode, long duration, String clientIp) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return;
        }
        try {
            requestLogMapper.insert(TkOpenApiRequestLogDO.builder()
                    .requestId(requestId)
                    .clientId(clientId)
                    .httpMethod(method)
                    .requestTarget(target)
                    .httpStatus(status)
                    .errorCode(errorCode)
                    .durationMs(duration)
                    .clientIp(clientIp)
                    .requestDate(LocalDate.now())
                    .build());
        } catch (Exception ex) {
            log.warn("[saveRequestLog][requestId({}) failed]", requestId, ex);
        }
    }
}
