package cn.iocoder.yudao.module.tk.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TkPaymentProtocolTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void signatureBindsOriginalBytesMethodPathAndNonce() {
        String secret = "0123456789abcdef0123456789abcdef";
        String body = "{\"orderNo\":\"TKP_123\"}";
        String signature = TkPaymentProtocol.sign(secret, "1794614400", "0123456789abcdef0123456789abcdef",
                "POST", "/admin-api/tk/payments/notify", body);
        assertEquals(64, signature.length());
        assertNotEquals(signature, TkPaymentProtocol.sign(secret, "1794614400", "0123456789abcdef0123456789abcdef",
                "POST", "/admin-api/tk/payments/notify", body + " "));
        assertNotEquals(signature, TkPaymentProtocol.sign(secret, "1794614400", "0123456789abcdef0123456789abcdef",
                "GET", "/admin-api/tk/payments/notify", body));
        assertThrows(IllegalArgumentException.class, () -> TkPaymentProtocol.verify(secret, "1794614400",
                "0123456789abcdef0123456789abcdef", signature, "POST", "/admin-api/tk/payments/notify", body,
                1794614801L));
    }

    @Test void acceptedSnapshotCannotChangeTenantAmountCurrencyOrMode() {
        ObjectNode expected = quote();
        TkPaymentProtocol.validateSnapshot(expected, expected.deepCopy());
        for (String field : new String[]{"tenantId", "amountCents", "currency", "livemode", "points", "catalogVersion", "fx"}) {
            ObjectNode changed = expected.deepCopy(); changed.put(field, "changed");
            assertThrows(IllegalArgumentException.class, () -> TkPaymentProtocol.validateSnapshot(expected, changed), field);
        }
    }

    @Test void paymentStateCannotMoveBackwardsAfterRefundOrPayment() {
        assertFalse(TkPaymentProtocol.isAllowedTransition("refunded", "paid"));
        assertFalse(TkPaymentProtocol.isAllowedTransition("refund_pending", "paid"));
        assertFalse(TkPaymentProtocol.isAllowedTransition("paid", "quoted"));
        assertFalse(TkPaymentProtocol.isAllowedTransition("payment_pending", "quoted"));
        assertTrue(TkPaymentProtocol.isAllowedTransition("payment_pending", "paid"));
        assertTrue(TkPaymentProtocol.isAllowedTransition("paid", "refunded"));
        assertTrue(TkPaymentProtocol.isAllowedTransition("review", "refunded"));
    }

    private ObjectNode quote() {
        ObjectNode q = json.createObjectNode();
        q.put("orderNo", "TKP_0123456789abcdef0123456789abcdef");
        q.put("tenantId", "8"); q.put("userId", "7"); q.put("packageCode", "P1000");
        q.put("catalogVersion", "2026-11-v1"); q.put("quantity", 1);
        q.put("baseAmountCents", 100000); q.put("amountCents", 100000);
        q.put("points", 250); q.put("currency", "CNY"); q.put("livemode", true);
        q.put("paymentMethod", "alipay"); q.putNull("fx"); return q;
    }
}
