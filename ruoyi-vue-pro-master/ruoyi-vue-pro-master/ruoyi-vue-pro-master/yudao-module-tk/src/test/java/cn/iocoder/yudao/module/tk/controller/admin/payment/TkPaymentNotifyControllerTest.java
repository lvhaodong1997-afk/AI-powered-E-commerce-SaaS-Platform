package cn.iocoder.yudao.module.tk.controller.admin.payment;
import cn.iocoder.yudao.module.tk.service.payment.TkPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
class TkPaymentNotifyControllerTest {
    @Test void actualSerializedAcknowledgementMatchesBridgeContractOnlyAfterProcessing() throws Exception {
        TkPaymentService service=mock(TkPaymentService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new TkPaymentNotifyController(service)).build();
        String response=mvc.perform(post("/tk/payments/notify").header("X-Clipforge-Timestamp","1794614400").header("X-Clipforge-Nonce","nonce").header("X-Clipforge-Signature","signature").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getContentAsString();
        String fixture=System.getProperty("tk.notify.ack.output");
        if(fixture!=null)Files.write(Paths.get(fixture),response.getBytes(StandardCharsets.UTF_8));
        com.fasterxml.jackson.databind.JsonNode json=new ObjectMapper().readTree(response);
        assertEquals(200,json.path("code").asInt());assertTrue(json.path("data").asBoolean());
        verify(service).notify("1794614400","nonce","signature","{}");
    }
    @Test void unsuccessfulProcessingCannotReturnSuccessAcknowledgement() {
        TkPaymentService service=mock(TkPaymentService.class);
        doThrow(new IllegalArgumentException("signature rejected")).when(service).notify(anyString(),anyString(),anyString(),anyString());
        assertThrows(IllegalArgumentException.class,()->new TkPaymentNotifyController(service).notify("t","n","s","{}"));
    }
}
