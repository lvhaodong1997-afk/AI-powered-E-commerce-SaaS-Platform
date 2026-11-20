package cn.iocoder.yudao.module.tk.service.open.x;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXReturnTicketDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXReturnTicketMapper;
import cn.iocoder.yudao.module.tk.service.social.auth.TkSocialTokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenXTicketServiceTest {
    private final TkOpenXReturnTicketMapper tickets = mock(TkOpenXReturnTicketMapper.class);
    private final TkSocialTokenCipher cipher = mock(TkSocialTokenCipher.class);
    private TkOpenXTicketService service;

    @BeforeEach
    void setup() {
        service = new TkOpenXTicketService(tickets, cipher);
        when(cipher.encrypt(any(), any())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(cipher.decrypt(any(), any())).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value != null && value.startsWith("enc:") ? value.substring(4) : value;
        });
    }

    @Test
    void issueOrReuseReusesUnusedTicket() {
        String raw = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";
        TkOpenXReturnTicketDO issued = TkOpenXReturnTicketDO.builder()
                .id(1L)
                .clientId("client-a")
                .authSessionId("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ")
                .purpose(TkOpenXTicketService.PURPOSE_AUTH_RESULT)
                .status("ISSUED")
                .ticketCipher("enc:" + raw)
                .expireTime(LocalDateTime.now().plusMinutes(5))
                .build();
        when(tickets.selectListBySessionForUpdate(eq("client-a"), any(), any()))
                .thenReturn(Collections.singletonList(issued));
        assertEquals(raw, service.issueOrReuse("client-a", issued.getAuthSessionId(),
                TkOpenXTicketService.PURPOSE_AUTH_RESULT, LocalDateTime.now().plusMinutes(10)));
        verify(tickets, never()).insert(any(TkOpenXReturnTicketDO.class));
    }

    @Test
    void consumedTicketIsNotReplacedOnIssueOrReuseOrReplay() {
        TkOpenXReturnTicketDO consumed = TkOpenXReturnTicketDO.builder()
                .id(2L)
                .clientId("client-a")
                .authSessionId("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ")
                .purpose(TkOpenXTicketService.PURPOSE_AUTH_RESULT)
                .status("CONSUMED")
                .expireTime(LocalDateTime.now().plusMinutes(5))
                .build();
        when(tickets.selectListBySessionForUpdate(eq("client-a"), any(), any()))
                .thenReturn(Collections.singletonList(consumed));
        assertNull(service.issueOrReuse("client-a", consumed.getAuthSessionId(),
                TkOpenXTicketService.PURPOSE_AUTH_RESULT, LocalDateTime.now().plusMinutes(10)));
        assertNull(service.reuseIssued("client-a", consumed.getAuthSessionId(),
                TkOpenXTicketService.PURPOSE_AUTH_RESULT));
        verify(tickets, never()).insert(any(TkOpenXReturnTicketDO.class));
    }
}