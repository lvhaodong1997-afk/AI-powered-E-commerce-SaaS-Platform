package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkPaymentQuoteRejectionTest {
    private static final String MESSAGE = "该渠道最低付款金额为5.00 CNY，请重新选择购买数量";
    private static final String ORDER = "TKP_00000000000000000000000000000001";
    private static final int REJECTED = 1_060_000_053;
    private final ObjectMapper json = new ObjectMapper();

    private RuntimeException failure(String method,String action,int status,String body) throws Exception {
        Method classifier;
        try { classifier=TkPaymentGateway.class.getDeclaredMethod("failure",String.class,String.class,int.class,InputStream.class); }
        catch (NoSuchMethodException e) { fail("gateway must distinguish proven quote rejection from uncertain errors");return null; }
        classifier.setAccessible(true);
        return (RuntimeException)classifier.invoke(new TkPaymentGateway(new TkPaymentProperties(),json),method,action,status,
                body==null?null:new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
    private RuntimeException known() throws Exception {
        return failure("POST","quotes",400,json.createObjectNode().put("code","BELOW_MINIMUM").put("error",MESSAGE).toString());
    }
    @Test void onlyBoundedRecognizedQuoteRejectionGetsSpecificMessage() throws Exception {
        RuntimeException rejected=known();
        assertEquals("QuoteRejectedException",rejected.getClass().getSimpleName());
        assertEquals(MESSAGE,rejected.getMessage());
        String body=json.createObjectNode().put("code","BELOW_MINIMUM").put("error",MESSAGE).toString();
        for(String action:new String[]{"orders","cancel","refunds","catalog"})assertEquals(IllegalStateException.class,failure("POST",action,400,body).getClass());
        for(int status:new int[]{200,401,403,409,500,502})assertEquals(IllegalStateException.class,failure("POST","quotes",status,body).getClass());
        assertEquals(IllegalStateException.class,failure("GET","quotes",400,body).getClass());
        for(String malformed:new String[]{"{", "{}", "null", body.replace("BELOW_MINIMUM","OTHER"), body.replace(MESSAGE,"<script>secret</script>"), body+" ".repeat(5000)})
            assertEquals(IllegalStateException.class,failure("POST","quotes",400,malformed).getClass());
        assertEquals(IllegalStateException.class,failure("POST","quotes",400,null).getClass());
    }
    @Test void serverFencesNoQuoteOrderBeforeExposingDefiniteBusinessRejection() throws Exception {
        TkPaymentProperties p=new TkPaymentProperties();p.setEnabled(true);p.setGatewayUrl("https://tokdeploy.com");p.setGatewaySecret("g".repeat(32));p.setNotifySecret("n".repeat(32));
        TkPaymentGateway gateway=mock(TkPaymentGateway.class);TkPaymentOrderStore store=mock(TkPaymentOrderStore.class);TkDataScopeService scopes=mock(TkDataScopeService.class);
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(10L,9L,"TENANT_ADMIN",null));
        ObjectNode catalog=json.createObjectNode().put("enabled",true).put("version","2026-11-v1");
        catalog.putArray("packages").add(json.createObjectNode().put("code","P1000"));catalog.putArray("paymentMethods").add("alipay");
        when(gateway.request("GET","catalog",null)).thenReturn(catalog);
        ObjectNode created=json.createObjectNode().put("orderNo",ORDER).put("status","created");
        when(store.create(anyLong(),anyLong(),anyString(),any())).thenReturn(created);when(store.get(ORDER,null)).thenReturn(created);when(store.request(ORDER)).thenReturn(json.createObjectNode());
        when(gateway.request(eq("POST"),eq("quotes"),any())).thenThrow(known());
        Method reject;
        try { reject=TkPaymentOrderStore.class.getMethod("rejectQuote",String.class); }
        catch(NoSuchMethodException e){fail("known rejection needs a durable no-quote fence before unlocking frontend");return;}
        when((Boolean)reject.invoke(store,ORDER)).thenReturn(true);
        TkPaymentService service=new TkPaymentService(gateway,store,scopes,p,json);
        ObjectNode input=json.createObjectNode().put("packageCode","P1000").put("quantity",1).put("paymentMethod","alipay").put("idempotencyKey","quote-rejection-key");
        ServiceException error=assertThrows(ServiceException.class,()->service.quote(input));
        assertEquals(REJECTED,error.getCode());assertEquals(MESSAGE,error.getMessage());
        when((Boolean)reject.invoke(store,ORDER)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->service.quote(input),"already-quoted race must remain uncertain");
        when(gateway.request(eq("POST"),eq("quotes"),any())).thenThrow(new IllegalStateException("unknown"));
        assertThrows(IllegalStateException.class,()->service.quote(input));
        when(store.create(anyLong(),anyLong(),anyString(),any())).thenReturn(created.deepCopy().put("quoteRejected",true));
        clearInvocations(gateway);
        ServiceException replay=assertThrows(ServiceException.class,()->service.quote(input));
        assertEquals(REJECTED,replay.getCode());
        verify(gateway,never()).request(eq("POST"),eq("quotes"),any());
    }
}
