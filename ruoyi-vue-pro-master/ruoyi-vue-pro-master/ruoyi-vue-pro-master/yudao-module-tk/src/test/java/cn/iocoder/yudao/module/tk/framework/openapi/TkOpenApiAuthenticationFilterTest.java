package cn.iocoder.yudao.module.tk.framework.openapi;

import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiRequestLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.mockito.ArgumentCaptor;

class TkOpenApiAuthenticationFilterTest {

    @Test
    void shouldStopReadingChunkedBodyOnceLimitIsExceeded() throws Exception {
        CountingServletInputStream input = new CountingServletInputStream(1024);
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public ServletInputStream getInputStream() {
                return input;
            }
        };
        request.setMethod("POST");
        request.setRequestURI("/admin-api/tk/open/v1/tiktok/publish/tasks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TkOpenApiAuthenticationFilter filter = new TkOpenApiAuthenticationFilter(
                mock(TkOpenApiAuthenticationService.class), mock(TkOpenApiRequestLogMapper.class), 16);

        filter.doFilter(request, response, (req, res) -> {
            throw new AssertionError("oversized request must not reach the filter chain");
        });

        assertEquals(413, response.getStatus());
        assertTrue(input.getBytesRead() <= 17,
                "filter must stop after reading at most maxSignedBodyBytes + 1 bytes");
    }

    @Test
    void shouldIgnoreSpoofedForwardedForAndUseNginxRealIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/admin-api/tk/open/v1/tiktok/auth/sessions/auth_1");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.66, 203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.10");
        TkOpenApiAuthenticationService authenticationService = mock(TkOpenApiAuthenticationService.class);
        when(authenticationService.authenticate(any())).thenReturn(
                new TkOpenApiPrincipal("client_a", "A", "auth"));
        TkOpenApiAuthenticationFilter filter = new TkOpenApiAuthenticationFilter(
                authenticationService, mock(TkOpenApiRequestLogMapper.class), 16);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<TkOpenApiAuthRequest> captor = ArgumentCaptor.forClass(TkOpenApiAuthRequest.class);
        verify(authenticationService).authenticate(captor.capture());
        assertEquals("203.0.113.10", captor.getValue().getClientIp());
    }

    @Test
    void shouldIgnoreRealIpHeaderWhenRequestDoesNotComeFromLoopbackProxy() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/admin-api/tk/open/v1/tiktok/auth/sessions/auth_1");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Real-IP", "203.0.113.10");
        TkOpenApiAuthenticationService authenticationService = mock(TkOpenApiAuthenticationService.class);
        when(authenticationService.authenticate(any())).thenReturn(
                new TkOpenApiPrincipal("client_a", "A", "auth"));
        TkOpenApiAuthenticationFilter filter = new TkOpenApiAuthenticationFilter(
                authenticationService, mock(TkOpenApiRequestLogMapper.class), 16);

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { });

        ArgumentCaptor<TkOpenApiAuthRequest> captor = ArgumentCaptor.forClass(TkOpenApiAuthRequest.class);
        verify(authenticationService).authenticate(captor.capture());
        assertEquals("192.0.2.10", captor.getValue().getClientIp());
    }

    private static final class CountingServletInputStream extends ServletInputStream {
        private final int totalBytes;
        private int bytesRead;

        private CountingServletInputStream(int totalBytes) {
            this.totalBytes = totalBytes;
        }

        int getBytesRead() {
            return bytesRead;
        }

        @Override
        public int read() {
            if (bytesRead >= totalBytes) {
                return -1;
            }
            bytesRead++;
            return 'a';
        }

        @Override
        public boolean isFinished() {
            return bytesRead >= totalBytes;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            try {
                while (!isFinished()) {
                    readListener.onDataAvailable();
                }
                readListener.onAllDataRead();
            } catch (IOException ex) {
                readListener.onError(ex);
            }
        }
    }
}
