package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkPaymentVerificationTest {
    private static final String ORDER = "TKP_00000000000000000000000000000001";
    private static final String KEY = "verification-key-0001";
    private final ObjectMapper json = new ObjectMapper();
    private TkPaymentProperties properties;
    private TkPaymentGateway gateway;
    private TkPaymentOrderStore store;
    private TkDataScopeService scopes;
    private TkPaymentService service;
    private final AtomicReference<ObjectNode> request = new AtomicReference<>();

    @BeforeEach void setup() {
        properties = new TkPaymentProperties();
        properties.setEnabled(true);
        properties.setGatewayUrl("https://tokdeploy.com");
        properties.setGatewaySecret("g".repeat(32));
        properties.setNotifySecret("n".repeat(32));
        gateway = mock(TkPaymentGateway.class);
        store = mock(TkPaymentOrderStore.class);
        scopes = mock(TkDataScopeService.class);
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(10L, 9L, "TENANT_ADMIN", null));
        when(gateway.request(eq("GET"), eq("catalog"), isNull())).thenAnswer(i -> publicCatalog());
        when(store.create(anyLong(), anyLong(), anyString(), any(ObjectNode.class))).thenAnswer(i -> {
            ObjectNode canonical = ((ObjectNode) i.getArgument(3)).deepCopy()
                    .put("tenantId", Long.toString(i.getArgument(0)))
                    .put("userId", Long.toString(i.getArgument(1))).put("orderNo", ORDER);
            request.set(canonical);
            return canonical.deepCopy().put("status", "created");
        });
        when(store.get(eq(ORDER), isNull())).thenAnswer(i -> request.get().deepCopy().put("status", "created"));
        when(store.request(ORDER)).thenAnswer(i -> request.get().deepCopy());
        when(gateway.request(eq("POST"), eq("quotes"), any())).thenAnswer(i -> ((ObjectNode) i.getArgument(2)).deepCopy().put("status", "quoted"));
        when(store.apply(eq(ORDER), any(ObjectNode.class))).thenAnswer(i -> i.getArgument(1));
        service = new TkPaymentService(gateway, store, scopes, properties, json);
    }

    private ObjectNode publicCatalog() {
        ObjectNode catalog = json.createObjectNode().put("enabled", true).put("version", "2026-11-v1");
        catalog.putArray("packages").add(json.createObjectNode().put("code", "P1000").put("amountCents", 100000).put("points", 250));
        catalog.putArray("paymentMethods").add("alipay").add("wechat_pay");
        return catalog;
    }

    private void property(String key, Object value) {
        BeanWrapperImpl bean = new BeanWrapperImpl(properties);
        assertTrue(bean.isWritableProperty(key), "verification property must exist: " + key);
        bean.setPropertyValue(key, value);
    }

    private void enableVerification() {
        property("verifyEnabled", true);
        property("verifyExpiresAt", Instant.now().plusSeconds(3600).toString());
        property("verifyTenantIds", "9");
        property("verifyUserIds", "10");
    }

    private ObjectNode quote(String code, int quantity) {
        return json.createObjectNode().put("packageCode", code).put("quantity", quantity)
                .put("paymentMethod", "alipay").put("idempotencyKey", KEY);
    }

    private JsonNode verificationPack() {
        for (JsonNode pack : service.catalog().path("packages")) {
            if ("VERIFY_1".equals(pack.path("code").asText())) return pack;
        }
        return null;
    }

    private ObjectNode unpaid(String status) {
        return json.createObjectNode().put("orderNo", ORDER).put("tenantId", "9").put("userId", "10")
                .put("packageCode", "VERIFY_1").put("quantity", 5).put("amountCents", 500)
                .put("currency", "CNY").put("status", status).put("creditStatus", "PENDING");
    }

    @Test void defaultConfigHidesTestPackageAndRejectsDirectQuote() {
        assertNull(verificationPack());
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1)));
        verify(store, never()).create(anyLong(), anyLong(), anyString(), any());
    }

    @Test void upstreamCatalogCannotExposeTestPackageWithoutLocalAuthorization() {
        ObjectNode catalog = publicCatalog();
        catalog.withArray("packages").add(json.createObjectNode().put("code", "VERIFY_1").put("amountCents", 1).put("points", 999));
        doReturn(catalog).when(gateway).request("GET", "catalog", null);
        assertNull(verificationPack());
        enableVerification();
        assertEquals(100, verificationPack().path("amountCents").asInt());
        assertEquals(1, verificationPack().path("points").asInt());
        assertEquals(2, service.catalog().path("packages").size());
    }

    @Test void onlyAllowedTenantAndUserSeeLocallyPricedVerificationPack() {
        enableVerification();
        JsonNode pack = verificationPack();
        assertNotNull(pack);
        assertEquals(100, pack.path("amountCents").asInt());
        assertEquals(1, pack.path("points").asInt());
        assertEquals(2, service.catalog().path("packages").size());
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(10L, 8L, "TENANT_ADMIN", null));
        assertNull(verificationPack());
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1)));
        when(scopes.getCurrentScope()).thenReturn(new TkUserScope(11L, 9L, "TENANT_ADMIN", null));
        assertNull(verificationPack());
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1)));
    }

    @Test void disabledExpiredMissingOrMalformedConfigurationFailsClosed() {
        enableVerification();
        for (String key : Arrays.asList("verifyExpiresAt", "verifyTenantIds", "verifyUserIds")) {
            for (String bad : Arrays.asList("", "invalid", "9,invalid", "10,invalid", "9,", "10,")) {
                enableVerification();
                property(key, bad);
                assertNull(verificationPack(), key + "=" + bad);
                assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1)));
            }
        }
        enableVerification();
        property("verifyExpiresAt", Instant.now().minusSeconds(1).toString());
        assertNull(verificationPack());
        enableVerification();
        property("verifyEnabled", false);
        assertNull(verificationPack());
        enableVerification();
        property("verifyTenantIds", null);
        assertNull(verificationPack());
    }

    @Test void exactCsvIdsSupportWhitespaceWithoutPrefixOrLeadingZeroMatches() {
        enableVerification();
        property("verifyTenantIds", " 8, 9 ");
        property("verifyUserIds", " 11, 10 ");
        assertNotNull(verificationPack());
        property("verifyTenantIds", "19");
        assertNull(verificationPack());
        property("verifyTenantIds", "09");
        assertNull(verificationPack());
    }

    @Test void configuredWhitelistDoesNotGrantTenantAdministratorRole() {
        enableVerification();
        for (String role : Arrays.asList("PLATFORM_ADMIN", "COMPANY_USER")) {
            when(scopes.getCurrentScope()).thenReturn(new TkUserScope(10L, 9L, role, null));
            assertThrows(IllegalArgumentException.class, () -> service.catalog());
            assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1)));
        }
        verifyNoInteractions(store);
    }

    @Test void verificationQuantitiesArePreservedAndBrowserCannotOverrideIdentityOrPrice() {
        enableVerification();
        for (int quantity : new int[]{1, 5, 10}) {
            ObjectNode input = quote("VERIFY_1", quantity).put("tenantId", "8").put("userId", "11")
                    .put("amountCents", 1).put("points", 999999);
            ObjectNode result = service.quote(input);
            assertEquals(quantity, request.get().path("quantity").asInt());
            assertEquals("9", request.get().path("tenantId").asText());
            assertEquals("10", request.get().path("userId").asText());
            assertFalse(request.get().has("amountCents"));
            assertFalse(request.get().has("points"));
            assertEquals("quoted", result.path("status").asText());
        }
    }

    @Test void invalidQuantitiesAndUnsupportedMethodsNeverCreateOrders() {
        enableVerification();
        for (int quantity : new int[]{-1, 0, 2, 4, 6, 11}) {
            assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", quantity)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1).put("quantity", "1")));
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1).put("quantity", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1).put("quantity", new BigInteger("4294967297"))));
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("VERIFY_1", 1).put("paymentMethod", "other")));
        for (int quantity : new int[]{5, 10}) assertThrows(IllegalArgumentException.class, () -> service.quote(quote("P1000", quantity)));
        assertThrows(IllegalArgumentException.class, () -> service.quote(quote("P1000", 1).put("quantity", new BigInteger("4294967297"))));
        verify(store, never()).create(anyLong(), anyLong(), anyString(), any());
    }

    @Test void regularPackageStillUsesSingleQuantityWhenVerificationIsDisabled() {
        service.quote(quote("P1000", 1));
        assertEquals("P1000", request.get().path("packageCode").asText());
        assertEquals(1, request.get().path("quantity").asInt());
    }

    @Test void expiryOrRemovedWhitelistPreventsPreviouslyQuotedAndPendingOrdersFromStartingPayment() {
        enableVerification();
        for (String status : Arrays.asList("quoted", "payment_pending")) {
            when(store.get(ORDER, 9L)).thenReturn(unpaid(status));
            property("verifyExpiresAt", Instant.now().minusSeconds(1).toString());
            assertThrows(IllegalArgumentException.class, () -> service.pay(ORDER, unpaid(status)));
            enableVerification();
            property("verifyUserIds", "11");
            assertThrows(IllegalArgumentException.class, () -> service.pay(ORDER, unpaid(status)));
            enableVerification();
        }
        verify(gateway, never()).request(eq("POST"), eq("orders"), any());
    }

    @Test void permittedVerificationOrderCanPayWithConfirmedAmount() {
        enableVerification();
        ObjectNode local = unpaid("quoted");
        when(store.get(ORDER, 9L)).thenReturn(local);
        when(gateway.request(eq("POST"), eq("orders"), any())).thenReturn(local.deepCopy().put("status", "payment_pending"));
        assertEquals("payment_pending", service.pay(ORDER, local).path("status").asText());
        assertThrows(IllegalArgumentException.class, () -> service.pay(ORDER, local.deepCopy().put("amountCents", 100)));
        verify(gateway, times(1)).request(eq("POST"), eq("orders"), any());
    }

    @Test void disabledVerificationStillAllowsOriginalOrderReadsReconciliationAndNotification() {
        properties.setEnabled(false);
        ObjectNode local = unpaid("payment_pending");
        ObjectNode paid = local.deepCopy().put("status", "paid").put("creditStatus", "CREDITED");
        when(store.get(ORDER, 9L)).thenReturn(local);
        doReturn(local).when(store).get(ORDER, null);
        when(store.claim(ORDER)).thenReturn(true);
        when(gateway.request("GET", "orders/" + ORDER, null)).thenReturn(paid);
        assertEquals("paid", service.get(ORDER).path("status").asText());
        assertEquals("paid", service.reconcile(ORDER).path("status").asText());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = "00000000000000000000000000000001";
        String body = "{\"orderNo\":\"" + ORDER + "\"}";
        when(store.acceptNonce(nonce)).thenReturn(true);
        String signature = TkPaymentProtocol.sign(properties.getNotifySecret(), timestamp, nonce, "POST", properties.getNotifyPath(), body);
        assertDoesNotThrow(() -> service.notify(timestamp, nonce, signature, body));
        verify(store, times(3)).apply(ORDER, paid);
    }
}
