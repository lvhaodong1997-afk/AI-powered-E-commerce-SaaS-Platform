package cn.iocoder.yudao.module.tk.service.payment;

import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditBatchService;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditServiceImpl;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Short DB transactions only. Remote calls are deliberately owned by TkPaymentService. */
@Service
public class TkPaymentOrderStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TkCreditBatchService batches;
    private final TkCreditServiceImpl credits;
    private final TkPaymentProperties properties;
    public TkPaymentOrderStore(JdbcTemplate db,ObjectMapper json,TkCreditBatchService batches,TkCreditServiceImpl credits,TkPaymentProperties properties) {
        this.db=db;this.json=json;this.batches=batches;this.credits=credits;this.properties=properties;
    }

    @Transactional(rollbackFor=Exception.class)
    public ObjectNode create(long tenant,long user,String key,ObjectNode purchase) {
        if(key==null || !key.matches("[a-zA-Z0-9_-]{16,64}"))throw new IllegalArgumentException("下单请求号无效");
        String order="TKP_"+UUID.randomUUID().toString().replace("-","");
        ObjectNode request=purchase.deepCopy();
        request.put("orderNo",order).put("tenantId",Long.toString(tenant)).put("userId",Long.toString(user));
        db.update("INSERT INTO tk_payment_order(order_no,tenant_id,user_id,idempotency_key,request_json) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE order_no=order_no",order,tenant,user,key,encode(request));
        Map<String,Object> row=db.queryForMap("SELECT * FROM tk_payment_order WHERE tenant_id=? AND idempotency_key=? FOR UPDATE",tenant,key);
        ObjectNode original=parse(row.get("request_json"));
        request.put("orderNo",original.path("orderNo").asText());
        if(!request.equals(original))throw new IllegalArgumentException("同一下单请求号不能用于不同的套餐、渠道或用户");
        return view(row);
    }

    public ObjectNode get(String order,Long tenant) {
        TkPaymentProtocol.orderNo(order);
        List<Map<String,Object>> rows=tenant==null?db.queryForList("SELECT * FROM tk_payment_order WHERE order_no=?",order)
                :db.queryForList("SELECT * FROM tk_payment_order WHERE order_no=? AND tenant_id=?",order,tenant);
        if(rows.isEmpty())throw new IllegalArgumentException("订单不存在");
        return view(rows.get(0));
    }
    public ObjectNode request(String order) {return parse(db.queryForObject("SELECT request_json FROM tk_payment_order WHERE order_no=?",String.class,TkPaymentProtocol.orderNo(order)));}
    public ObjectNode page(long tenant,int page,int size) {
        if(page<1 || size<1 || size>100 || page>10000)throw new IllegalArgumentException("分页无效");
        ObjectNode result=json.createObjectNode();
        result.put("total",db.queryForObject("SELECT COUNT(*) FROM tk_payment_order WHERE tenant_id=?",Long.class,tenant));
        com.fasterxml.jackson.databind.node.ArrayNode list=result.putArray("list");
        db.queryForList("SELECT * FROM tk_payment_order WHERE tenant_id=? ORDER BY id DESC LIMIT ? OFFSET ?",tenant,size,(page-1)*size).forEach(r->list.add(view(r)));
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public ObjectNode apply(String order,ObjectNode remote) {
        Map<String,Object> row=db.queryForMap("SELECT * FROM tk_payment_order WHERE order_no=? FOR UPDATE",TkPaymentProtocol.orderNo(order));
        ObjectNode request=parse(row.get("request_json"));
        validateQuote(request,remote);
        ObjectNode quote=row.get("quote_json")==null?remote.deepCopy():parse(row.get("quote_json"));
        TkPaymentProtocol.validateSnapshot(quote,remote);
        String current=(String)row.get("status"),next=remote.path("status").asText();
        long version=remote.path("version").asLong(-1),old=((Number)row.get("version")).longValue();
        if(version<0)throw new IllegalArgumentException("支付版本无效");
        if(version<old)return view(row);
        if(!"created".equals(current)&&!TkPaymentProtocol.isAllowedTransition(current,next))return view(row);
        if(!Arrays.asList("quoted","payment_pending","paid","canceled","refund_pending","refunded","review").contains(next))throw new IllegalArgumentException("支付状态无效");
        String payment=remote.path("paymentId").isTextual()?remote.path("paymentId").asText():null;
        if(row.get("payment_id")!=null && !row.get("payment_id").equals(payment))throw new IllegalArgumentException("渠道付款编号不匹配");
        Timestamp paid=null;
        String credit=(String)row.get("credit_status");
        if(Arrays.asList("paid","refund_pending","refunded").contains(next)) {
            if(payment==null || payment.isEmpty() || !remote.path("paidAt").isTextual())throw new IllegalArgumentException("支付结果缺少已验证的付款信息");
            paid=Timestamp.from(Instant.parse(remote.path("paidAt").asText()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
            if(row.get("paid_at")!=null && !((Timestamp)row.get("paid_at")).toInstant().equals(paid.toInstant()))throw new IllegalArgumentException("付款时间发生变化");
            if(paid.toInstant().isAfter(Instant.now().plusSeconds(300)))throw new IllegalArgumentException("付款时间无效");
            long tenant=((Number)row.get("tenant_id")).longValue();
            credits.ensureAccount(tenant);
            if("PENDING".equals(credit)) {
                long points=remote.path("points").asLong();
                batches.grantPayment(tenant,order,points,paid.toInstant());
                credit="CREDITED";
            }
            if("refund_pending".equals(next)) {
                credit=batches.reserveRefund(tenant,order)?"REFUND_PENDING":"REVIEW";
            } else if("refunded".equals(next)) credit=batches.confirmRefund(tenant,order)?"REFUNDED":"REVIEW";
        } else if("review".equals(next))credit="REVIEW";
        // A conflicting snapshot at the same delivery version cannot mutate an established state.
        if(version==old && !current.equals(next))throw new IllegalArgumentException("支付版本冲突");
        db.update("UPDATE tk_payment_order SET quote_json=?,snapshot_json=?,payment_id=?,status=?,credit_status=?,version=?,paid_at=COALESCE(paid_at,?),next_check_at=?,lease_until=NULL,check_failures=0,force_check=0,update_time=CURRENT_TIMESTAMP WHERE order_no=?",
                encode(quote),encode(remote),payment,next,credit,version,paid,nextCheck(next,credit),order);
        return get(order,null);
    }

    @Transactional(rollbackFor=Exception.class)
    public void reserveRefund(String order) {
        Map<String,Object> row=db.queryForMap("SELECT * FROM tk_payment_order WHERE order_no=? FOR UPDATE",TkPaymentProtocol.orderNo(order));
        if(!"paid".equals(row.get("status")) || !"CREDITED".equals(row.get("credit_status")))throw new IllegalArgumentException("订单不满足自动退款条件");
        Long tenant=((Number)row.get("tenant_id")).longValue();
        credits.ensureAccount(tenant);
        if(!batches.reserveRefund(tenant,order))throw new IllegalStateException("订单积分已消耗或被任务冻结，需要人工处理");
        db.update("UPDATE tk_payment_order SET credit_status='REFUND_PENDING',next_check_at=CURRENT_TIMESTAMP WHERE order_no=?",order);
    }

    @Transactional(rollbackFor=Exception.class)
    public boolean acceptNonce(String nonce) {
        return db.update("INSERT IGNORE INTO tk_payment_event(nonce,expires_at) VALUES(?,?)",nonce,Timestamp.from(Instant.now().plusSeconds(600)))==1;
    }
    public List<String> due() {
        return db.queryForList("SELECT order_no FROM tk_payment_order WHERE next_check_at<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP) AND (force_check=1 OR (status NOT IN ('canceled','refunded') AND NOT(status='paid' AND credit_status='CREDITED') AND credit_status<>'REVIEW')) ORDER BY next_check_at LIMIT 20",String.class);
    }
    public boolean claim(String order) {
        return db.update("UPDATE tk_payment_order SET lease_until=? WHERE order_no=? AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)",Timestamp.from(Instant.now().plusSeconds(60)),order)==1;
    }
    public void failed(String order) {
        db.update("UPDATE tk_payment_order SET lease_until=NULL,force_check=1,next_check_at=?,check_failures=check_failures+1 WHERE order_no=?",Timestamp.from(Instant.now().plusSeconds(120)),order);
    }
    public void cleanupNonces() {db.update("DELETE FROM tk_payment_event WHERE expires_at<CURRENT_TIMESTAMP LIMIT 1000");}

    private Timestamp nextCheck(String status,String credit) {
        return Timestamp.from(Instant.now().plusSeconds("paid".equals(status)&&"CREDITED".equals(credit)?86400:30));
    }
    private void validateQuote(ObjectNode request,ObjectNode remote) {
        for(String field:Arrays.asList("orderNo","userId","tenantId","packageCode","quantity","paymentMethod"))
            if(!request.path(field).equals(remote.path(field)))throw new IllegalArgumentException("报价归属不匹配："+field);
        if(!request.path("version").equals(remote.path("catalogVersion")) || !remote.path("livemode").isBoolean()
                || remote.path("livemode").asBoolean()!=properties.isLiveMode() || !remote.path("points").isIntegralNumber()
                || remote.path("points").asLong()<=0 || !remote.path("amountCents").isIntegralNumber()
                || remote.path("amountCents").asLong()<=0 || !remote.path("baseAmountCents").isIntegralNumber()
                || remote.path("baseAmountCents").asLong()<=0 || !remote.path("currency").asText().matches("CNY|HKD|USD"))
            throw new IllegalArgumentException("支付报价无效或商户模式不匹配");
    }
    private ObjectNode view(Map<String,Object> row) {
        ObjectNode result=row.get("snapshot_json")==null?parse(row.get("request_json")):parse(row.get("snapshot_json"));
        result.put("orderNo",(String)row.get("order_no")).put("status",(String)row.get("status")).put("creditStatus",(String)row.get("credit_status"));
        result.put("createTime",String.valueOf(row.get("create_time")));
        result.put("idempotencyKey",(String)row.get("idempotency_key"));
        return result;
    }
    private ObjectNode parse(Object value) {try{return (ObjectNode)json.readTree(String.valueOf(value));}catch(Exception e){throw new IllegalStateException("订单快照损坏");}}
    private String encode(JsonNode value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("订单序列化失败");}}
}
