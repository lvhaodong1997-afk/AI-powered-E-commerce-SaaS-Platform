package cn.iocoder.yudao.module.tk.service.open.api;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiClientDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenApiEventDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiClientMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenApiEventMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSecretCipher;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiSigner;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkOpenApiCallbackServiceTest {

    @Test
    void shouldRecoverExpiredDeliveringClaimsBeforeScanningPendingEvents() {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        when(eventMapper.selectRetryable(any(LocalDateTime.class), eq(100))).thenReturn(Collections.emptyList());
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(eventMapper,
                mock(TkOpenApiClientMapper.class), mock(TkOpenApiSecretCipher.class),
                mock(TkOpenApiCallbackHttpClient.class));

        try {
            service.deliverPending(100);

            verify(eventMapper).recoverStaleDelivering(any(LocalDateTime.class));
            verify(eventMapper).selectRetryable(any(LocalDateTime.class), eq(100));
        } finally {
            service.destroy();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldSendSignedCallbackThroughPinnedHttpClient() throws Exception {
        TkOpenApiEventMapper eventMapper = mock(TkOpenApiEventMapper.class);
        TkOpenApiClientMapper clientMapper = mock(TkOpenApiClientMapper.class);
        TkOpenApiSecretCipher secretCipher = mock(TkOpenApiSecretCipher.class);
        TkOpenApiCallbackHttpClient httpClient = mock(TkOpenApiCallbackHttpClient.class);
        TkOpenApiCallbackService service = new TkOpenApiCallbackService(
                eventMapper, clientMapper, secretCipher, httpClient);
        TkOpenApiEventDO event = TkOpenApiEventDO.builder()
                .id(1L)
                .eventId("evt_1")
                .clientId("client_a")
                .callbackUrl("https://8.8.8.8/tk/events")
                .payloadJson("{}")
                .status("PENDING")
                .attemptCount(0)
                .build();
        TkOpenApiClientDO client = TkOpenApiClientDO.builder()
                .clientId("client_a")
                .callbackSecretCipher("callback-cipher")
                .status(0)
                .build();
        when(eventMapper.selectByEventId("evt_1")).thenReturn(event);
        when(eventMapper.update(isNull(), any())).thenReturn(1);
        when(clientMapper.selectByClientId("client_a")).thenReturn(client);
        when(secretCipher.decrypt("callback-cipher")).thenReturn("callback-secret");
        when(httpClient.post(any(), any(), anyMap(), eq("{}"))).thenReturn(204);

        try {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    TkOpenApiEventDO.class);
            service.deliver("evt_1");

            ArgumentCaptor<InetAddress[]> addresses = ArgumentCaptor.forClass(InetAddress[].class);
            ArgumentCaptor<Map> headers = ArgumentCaptor.forClass(Map.class);
            verify(httpClient).post(eq(URI.create(event.getCallbackUrl())), addresses.capture(),
                    headers.capture(), eq("{}"));
            assertEquals(InetAddress.getByName("8.8.8.8"), addresses.getValue()[0]);
            assertEquals("evt_1", headers.getValue().get("X-TK-Event-Id"));
            String timestamp = (String) headers.getValue().get("X-TK-Timestamp");
            String bodyHash = TkOpenApiSigner.sha256Hex("{}".getBytes(StandardCharsets.UTF_8));
            assertEquals(TkOpenApiSigner.hmacBase64("callback-secret",
                            "evt_1\n" + timestamp + "\n" + bodyHash),
                    headers.getValue().get("X-TK-Signature"));
            verify(eventMapper, times(2)).update(isNull(), any());
        } finally {
            service.destroy();
        }
    }
}
