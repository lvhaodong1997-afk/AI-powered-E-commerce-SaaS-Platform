package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import cn.iocoder.yudao.module.tk.service.scope.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import java.time.Instant;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_PAYMENT_QUOTE_REJECTED;

@Service
public class TkPaymentService {
    private static final String VERIFICATION_PACKAGE = "VERIFY_1";
    private final TkPaymentGateway gateway;
    private final TkPaymentOrderStore store;
    private final TkDataScopeService scopes;
    private final TkPaymentProperties properties;
    private final ObjectMapper json;
    public TkPaymentService(TkPaymentGateway gateway,TkPaymentOrderStore store,TkDataScopeService scopes,TkPaymentProperties properties,ObjectMapper json) {
        this.gateway=gateway;this.store=store;this.scopes=scopes;this.properties=properties;this.json=json;
    }
    public ObjectNode catalog() {
        TkUserScope scope=admin();
        if(!properties.isConfigured())return json.createObjectNode().put("enabled",false).put("version","2026-11-v1").set("packages",json.createArrayNode());
        ObjectNode catalog=gateway.request("GET","catalog",null).deepCopy();
        ArrayNode packages=catalog.withArray("packages");
        // The public gateway catalog cannot grant access to this tenant-local verification package.
        for(int i=packages.size()-1;i>=0;i--)if(VERIFICATION_PACKAGE.equals(packages.get(i).path("code").asText()))packages.remove(i);
        if(properties.isVerificationAllowed(scope.getTenantId(),scope.getUserId()))
            packages.add(json.createObjectNode().put("code",VERIFICATION_PACKAGE).put("amountCents",100).put("points",1));
        catalog.put("enabled",properties.isEnabled()&&catalog.path("enabled").asBoolean());return catalog;
    }
    public ObjectNode quote(ObjectNode input) {
        TkUserScope scope=admin();
        if(!properties.isEnabled())throw new IllegalStateException("在线购买暂未开启");
        ObjectNode catalog=catalog();
        if(!catalog.path("enabled").asBoolean())throw new IllegalStateException("在线购买暂不可用");
        String code=input.path("packageCode").asText(),method=input.path("paymentMethod").asText();
        boolean verification=VERIFICATION_PACKAGE.equals(code);
        if(verification)requireVerification(scope);
        boolean found=false,available=false;
        for(JsonNode p:catalog.path("packages"))if(code.equals(p.path("code").asText()))found=true;
        for(JsonNode p:catalog.path("paymentMethods"))if(method.equals(p.asText()))available=true;
        JsonNode quantityNode=input.path("quantity");
        if(!found||!available||!quantityNode.isIntegralNumber()||!quantityNode.canConvertToInt())throw new IllegalArgumentException("套餐或付款渠道无效");
        int quantity=quantityNode.intValue();
        if(verification ? quantity!=1&&quantity!=5&&quantity!=10 : quantity!=1)throw new IllegalArgumentException("购买数量无效");
        ObjectNode purchase=json.createObjectNode().put("packageCode",code).put("paymentMethod",method).put("quantity",quantity).put("version",catalog.path("version").asText());
        ObjectNode order=store.create(scope.getTenantId(),scope.getUserId(),input.path("idempotencyKey").asText(),purchase);
        if(order.path("quoteRejected").asBoolean())throw new ServiceException(TK_PAYMENT_QUOTE_REJECTED);
        String orderNo=order.path("orderNo").asText();
        try{return reconcile(orderNo);}
        catch(TkPaymentGateway.QuoteRejectedException e){
            if(store.rejectQuote(orderNo))throw new ServiceException(TK_PAYMENT_QUOTE_REJECTED.getCode(),e.getMessage());
            throw new IllegalStateException("报价结果发生变化，请查询原订单");
        }
    }
    public ObjectNode pay(String order,ObjectNode confirmation) {
        TkUserScope scope=admin();
        ObjectNode local=store.get(order,scope.getTenantId());
        if(!properties.isEnabled())throw new IllegalStateException("在线购买暂未开启");
        if(!local.path("amountCents").equals(confirmation.path("amountCents"))||!local.path("currency").equals(confirmation.path("currency")))throw new IllegalArgumentException("付款金额已变化，请重新确认报价");
        if(!"quoted".equals(local.path("status").asText()) && !"payment_pending".equals(local.path("status").asText()))return local;
        if(VERIFICATION_PACKAGE.equals(local.path("packageCode").asText()))requireVerification(scope);
        return store.apply(order,gateway.request("POST","orders",json.createObjectNode().put("orderNo",order)));
    }
    public ObjectNode get(String order) {
        ObjectNode local=store.get(order,admin().getTenantId());
        if(!terminal(local) && store.claim(order))try{return reconcile(order);}catch(RuntimeException e){store.failed(order);}
        return store.get(order,admin().getTenantId());
    }
    public ObjectNode page(int page,int size) {return store.page(admin().getTenantId(),page,size);}
    public ObjectNode cancel(String order) {
        ObjectNode local=store.get(order,admin().getTenantId());
        if(terminal(local))return local;
        if("created".equals(local.path("status").asText()))reconcile(order);
        return store.apply(order,gateway.request("POST","cancel",json.createObjectNode().put("orderNo",order)));
    }
    public ObjectNode refund(String order) {
        scopes.validatePlatformAdmin();
        store.get(order,null);store.reserveRefund(order);
        try{return store.apply(order,gateway.request("POST","refunds",json.createObjectNode().put("orderNo",order)));}
        catch(RuntimeException e){store.failed(order);throw e;}
    }
    public ObjectNode releaseFailedRefund(String order) {
        scopes.validatePlatformAdmin();
        store.get(order,null);
        ObjectNode canonical=gateway.request("GET","orders/"+TkPaymentProtocol.orderNo(order),null);
        return store.releaseFailedRefund(order,canonical);
    }
    public void notify(String timestamp,String nonce,String signature,String body) {
        if(body==null||body.length()>65536)throw new IllegalArgumentException("支付通知过大");
        TkPaymentProtocol.verify(properties.getNotifySecret(),timestamp,nonce,signature,"POST",properties.getNotifyPath(),body,Instant.now().getEpochSecond());
        // Each delivery uses a fresh nonce; duplicate requests are acknowledged without repeating work.
        if(!store.acceptNonce(nonce))return;
        String order;
        try{order=TkPaymentProtocol.orderNo(json.readTree(body).path("orderNo").asText());}catch(Exception e){throw new IllegalArgumentException("支付通知无效");}
        store.get(order,null);
        try{reconcile(order);}catch(RuntimeException e){store.failed(order);throw e;}
    }
    public ObjectNode reconcile(String order) {
        ObjectNode local=store.get(order,null);
        ObjectNode remote="paid".equals(local.path("status").asText())&&"REFUND_PENDING".equals(local.path("creditStatus").asText())
                ?gateway.request("POST","refunds",json.createObjectNode().put("orderNo",order))
                :"created".equals(local.path("status").asText())
                ?gateway.request("POST","quotes",store.request(order))
                :gateway.request("GET","orders/"+TkPaymentProtocol.orderNo(order),null);
        return store.apply(order,remote);
    }
    private TkUserScope admin() {
        TkUserScope scope=scopes.getCurrentScope();
        if(!scope.hasTenantScope() || !scope.isTenantAdmin() || scope.getUserId()==null)throw new IllegalArgumentException("仅租户管理员可管理充值订单");
        return scope;
    }
    private void requireVerification(TkUserScope scope) {
        if(!properties.isVerificationAllowed(scope.getTenantId(),scope.getUserId()))throw new IllegalArgumentException("临时验收套餐未开放或已到期");
    }
    private boolean terminal(ObjectNode order) {String s=order.path("status").asText();return "canceled".equals(s)||"refunded".equals(s)||"review".equals(s)||("paid".equals(s)&&"CREDITED".equals(order.path("creditStatus").asText()));}
}
