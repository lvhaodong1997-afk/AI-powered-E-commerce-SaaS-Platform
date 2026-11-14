package cn.iocoder.yudao.module.tk.service.credit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** All batch mutations share the aggregate account row lock. Caller transaction also covers the old log/account API. */
@Service
@Transactional(rollbackFor = Exception.class)
public class TkCreditBatchService {
    private final JdbcTemplate db;
    private final Clock clock;
    @Autowired public TkCreditBatchService(JdbcTemplate db) { this(db, Clock.systemUTC()); }
    TkCreditBatchService(JdbcTemplate db, Clock clock) { this.db=db; this.clock=clock; }

    public void prepare(Long tenantId) {
        Map<String,Object> account=lock(tenantId);
        if (db.queryForList("SELECT id FROM tk_credit_batch WHERE tenant_id=? FOR UPDATE",tenantId).isEmpty()) {
            long total=n(account,"total_credits"),available=n(account,"remaining_credits"),frozen=n(account,"frozen_credits");
            if (available<0 || frozen<0 || total<available+frozen) throw new IllegalStateException("历史积分余额不平，请先对账");
            db.update("INSERT INTO tk_credit_batch(tenant_id,source_key,initial_credits,available,frozen,used,expired,refund_reserved,refunded) VALUES(?,?,?,?,?,?,0,0,0)",
                    tenantId,"LEGACY:"+tenantId,total,available,frozen,total-available-frozen);
            Long batchId=db.queryForObject("SELECT id FROM tk_credit_batch WHERE source_key=? FOR UPDATE",Long.class,"LEGACY:"+tenantId);
            long allocated=0;
            for (Map<String,Object> log:db.queryForList("SELECT id,credits FROM tk_credit_log WHERE tenant_id=? AND deleted=0 AND status='IN_PROGRESS' FOR UPDATE",tenantId)) {
                long amount=n(log,"credits"); allocated+=amount;
                db.update("INSERT INTO tk_credit_batch_allocation(log_id,batch_id,credits,status) VALUES(?,?,?,'IN_PROGRESS')",log.get("id"),batchId,amount);
            }
            if(allocated!=frozen) throw new IllegalStateException("历史冻结积分与任务流水不平，请先对账");
        }
        // Compatibility with platform tenant quota edits: convert the delta into explicit ledger movements.
        Long tenantTotal=db.queryForObject("SELECT account_count FROM system_tenant WHERE id=? AND deleted=0 FOR UPDATE",Long.class,tenantId);
        if(tenantTotal!=null && tenantTotal!=n(account,"total_credits")) {
            long delta=tenantTotal-n(account,"total_credits");
            adjust(tenantId,delta);
            db.update("UPDATE tk_tenant_credit_account SET total_credits=?,remaining_credits=remaining_credits+?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND deleted=0",tenantTotal,delta,tenantId);
        }
        expire(tenantId);
    }

    public long currentTenantTotal(Long tenantId) {
        Long total=db.queryForObject("SELECT account_count FROM system_tenant WHERE id=? AND deleted=0 FOR UPDATE",Long.class,tenantId);
        return Math.max(0,total==null?0:total);
    }

    /** Account lock always precedes log/allocation locks; zero-credit logs still have a single winner. */
    public Long claimCompletion(Long tenantId,Long logId,String status) {
        lock(tenantId);
        List<Map<String,Object>> logs=db.queryForList("SELECT credits,status FROM tk_credit_log WHERE id=? AND tenant_id=? AND deleted=0 FOR UPDATE",logId,tenantId);
        if(logs.isEmpty() || !"IN_PROGRESS".equals(logs.get(0).get("status")))return null;
        long allocated=0;
        for(Map<String,Object> a:db.queryForList("SELECT a.credits FROM tk_credit_batch_allocation a JOIN tk_credit_batch b ON b.id=a.batch_id WHERE a.log_id=? AND b.tenant_id=? AND a.status='IN_PROGRESS' FOR UPDATE",logId,tenantId))allocated+=n(a,"credits");
        if(allocated!=n(logs.get(0),"credits"))throw new IllegalStateException("任务冻结分配与流水不平");
        return db.update("UPDATE tk_credit_log SET status=? WHERE id=? AND tenant_id=? AND deleted=0 AND status='IN_PROGRESS'",status,logId,tenantId)==1?n(logs.get(0),"credits"):null;
    }

    public void adjust(Long tenantId,long delta) {
        lock(tenantId);
        if(delta>0) add(tenantId,"ADMIN:"+UUID.randomUUID(),delta,null);
        else if(delta<0) {
            long remaining=-delta;
            for(Map<String,Object> b:available(tenantId)) {
                long take=Math.min(remaining,n(b,"available"));
                db.update("UPDATE tk_credit_batch SET available=available-?,used=used+? WHERE id=?",take,take,b.get("id"));
                remaining-=take; if(remaining==0)break;
            }
            if(remaining!=0)throw new IllegalStateException("不能减少已消耗、已过期或冻结中的积分额度");
        }
    }

    public void allocate(Long tenantId,Long logId,long credits) {
        lock(tenantId); long remaining=credits;
        for(Map<String,Object> b:available(tenantId)) {
            long take=Math.min(remaining,n(b,"available"));
            if(take==0)continue;
            db.update("UPDATE tk_credit_batch SET available=available-?,frozen=frozen+? WHERE id=?",take,take,b.get("id"));
            db.update("INSERT INTO tk_credit_batch_allocation(log_id,batch_id,credits,status) VALUES(?,?,?,'IN_PROGRESS')",logId,b.get("id"),take);
            remaining-=take; if(remaining==0)break;
        }
        if(remaining!=0)throw new IllegalStateException("可用积分不足");
    }

    /** Returns the still-valid credits that can be restored on failed work; expired allocations stay expired. */
    public long complete(Long tenantId,Long logId,boolean settled) {
        lock(tenantId); long returned=0;
        for(Map<String,Object> a:db.queryForList("SELECT a.batch_id,a.credits,b.expires_at FROM tk_credit_batch_allocation a JOIN tk_credit_batch b ON b.id=a.batch_id WHERE a.log_id=? AND b.tenant_id=? AND a.status='IN_PROGRESS' FOR UPDATE",logId,tenantId)) {
            long amount=n(a,"credits"); Timestamp expires=(Timestamp)a.get("expires_at");
            boolean valid=expires==null || expires.toInstant().isAfter(clock.instant());
            String target=settled?"used":valid?"available":"expired";
            if(db.update("UPDATE tk_credit_batch_allocation SET status=? WHERE log_id=? AND batch_id=? AND status='IN_PROGRESS'",settled?"SETTLED":"REFUNDED",logId,a.get("batch_id"))!=1)
                throw new IllegalStateException("任务积分分配已被处理");
            if(db.update("UPDATE tk_credit_batch SET frozen=frozen-?,"+target+"="+target+"+? WHERE id=? AND frozen>=?",amount,amount,a.get("batch_id"),amount)!=1)
                throw new IllegalStateException("任务冻结批次余额不平");
            if(!settled&&valid)returned+=amount;
        }
        return returned;
    }

    public boolean grantPayment(Long tenantId,String orderNo,long credits,Instant paidAt) {
        lock(tenantId);
        if(!db.queryForList("SELECT id FROM tk_credit_batch WHERE source_key=? FOR UPDATE",orderNo).isEmpty())return false;
        if(credits<=0 || paidAt==null || paidAt.isAfter(clock.instant().plusSeconds(300)))throw new IllegalArgumentException("付款时间或积分无效");
        Timestamp expiry=Timestamp.from(paidAt.atZone(ZoneOffset.UTC).plusMonths(6).toInstant());
        add(tenantId,orderNo,credits,expiry);
        if(db.update("UPDATE tk_tenant_credit_account SET total_credits=total_credits+?,remaining_credits=remaining_credits+?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND deleted=0 AND total_credits<=2147483647-?",credits,credits,tenantId,credits)!=1)
            throw new IllegalStateException("积分总额度超过上限");
        mirrorTotal(tenantId); audit(tenantId,"ONLINE_PAYMENT","RECHARGE",credits,credits,orderNo); expire(tenantId); return true;
    }

    public boolean reserveRefund(Long tenantId,String orderNo) {
        lock(tenantId); Map<String,Object> b=batch(tenantId,orderNo);
        if(n(b,"refunded")>0 || n(b,"refund_reserved")>0)return true;
        if(n(b,"used")>0 || n(b,"frozen")>0)return false;
        long amount=n(b,"available");
        db.update("UPDATE tk_credit_batch SET available=0,refund_reserved=? WHERE id=?",amount,b.get("id"));
        db.update("UPDATE tk_tenant_credit_account SET remaining_credits=remaining_credits-? WHERE tenant_id=? AND deleted=0",amount,tenantId);
        return true;
    }

    /** False means channel refunded consumed/frozen points: retain balances and require review. */
    public boolean confirmRefund(Long tenantId,String orderNo) {
        lock(tenantId); Map<String,Object> b=batch(tenantId,orderNo);
        if(n(b,"refunded")>0)return true;
        if(n(b,"used")>0 || n(b,"frozen")>0)return false;
        db.update("UPDATE tk_credit_batch SET available=0,expired=0,refund_reserved=0,refunded=initial_credits WHERE id=?",b.get("id"));
        db.update("UPDATE tk_tenant_credit_account SET remaining_credits=remaining_credits-?,total_credits=total_credits-?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND deleted=0",n(b,"available"),n(b,"initial_credits"),tenantId);
        mirrorTotal(tenantId); audit(tenantId,"ONLINE_REFUND","REFUND",n(b,"initial_credits"),-n(b,"available"),orderNo); return true;
    }

    /** Called only after the payment store verifies canonical terminal-failure proof. */
    public void releaseFailedRefund(Long tenantId,String orderNo) {
        lock(tenantId);Map<String,Object> b=batch(tenantId,orderNo);
        long amount=n(b,"refund_reserved");
        if(amount==0)return;
        if(n(b,"refunded")>0)throw new IllegalStateException("已退款积分不能释放");
        Timestamp expiry=(Timestamp)b.get("expires_at");
        boolean valid=expiry==null || expiry.toInstant().isAfter(clock.instant());
        String target=valid?"available":"expired";
        if(db.update("UPDATE tk_credit_batch SET refund_reserved=0,"+target+"="+target+"+? WHERE id=? AND refund_reserved=?",amount,b.get("id"),amount)!=1)
            throw new IllegalStateException("退款冻结积分已变化");
        long restored=valid?amount:0;
        if(db.update("UPDATE tk_tenant_credit_account SET remaining_credits=remaining_credits+?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND deleted=0",restored,tenantId)!=1)
            throw new IllegalStateException("退款冻结释放失败");
        audit(tenantId,"REFUND_FAILURE","RELEASE",amount,restored,orderNo);
    }

    private void add(Long tenantId,String source,long amount,Timestamp expiry) {
        db.update("INSERT INTO tk_credit_batch(tenant_id,source_key,initial_credits,available,frozen,used,expired,refund_reserved,refunded,expires_at) VALUES(?,?,?,?,0,0,0,0,0,?)",tenantId,source,amount,amount,expiry);
    }
    private void expire(Long tenantId) {
        long amount=0;
        for(Map<String,Object> b:db.queryForList("SELECT available FROM tk_credit_batch WHERE tenant_id=? AND expires_at<=? AND available>0 FOR UPDATE",tenantId,Timestamp.from(clock.instant())))amount+=n(b,"available");
        if(amount==0)return;
        db.update("UPDATE tk_credit_batch SET expired=expired+available,available=0 WHERE tenant_id=? AND expires_at<=? AND available>0",tenantId,Timestamp.from(clock.instant()));
        db.update("UPDATE tk_tenant_credit_account SET remaining_credits=remaining_credits-?,update_time=CURRENT_TIMESTAMP WHERE tenant_id=? AND deleted=0",amount,tenantId);
        audit(tenantId,"CREDIT_EXPIRY","EXPIRE",amount,-amount,"已付款积分满六个日历月到期");
    }
    private List<Map<String,Object>> available(Long tenantId) {
        return db.queryForList("SELECT * FROM tk_credit_batch WHERE tenant_id=? AND available>0 ORDER BY CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END,expires_at,id FOR UPDATE",tenantId);
    }
    private Map<String,Object> lock(Long tenantId) {
        if(tenantId==null || tenantId<=0)throw new IllegalArgumentException("租户无效");
        return db.queryForMap("SELECT * FROM tk_tenant_credit_account WHERE tenant_id=? AND deleted=0 FOR UPDATE",tenantId);
    }
    private Map<String,Object> batch(Long tenantId,String source) {return db.queryForMap("SELECT * FROM tk_credit_batch WHERE tenant_id=? AND source_key=? FOR UPDATE",tenantId,source);}
    private void mirrorTotal(Long tenantId) {
        Long total=db.queryForObject("SELECT total_credits FROM tk_tenant_credit_account WHERE tenant_id=? AND deleted=0 FOR UPDATE",Long.class,tenantId);
        db.update("UPDATE system_tenant SET account_count=? WHERE id=? AND deleted=0",total,tenantId);
    }
    private void audit(Long tenantId,String biz,String action,long amount,long delta,String remark) {
        Map<String,Object> account=lock(tenantId);
        long remaining=n(account,"remaining_credits"),frozen=n(account,"frozen_credits");
        db.update("INSERT INTO tk_credit_log(tenant_id,biz_type,action,credits,status,before_remaining_credits,after_remaining_credits,before_frozen_credits,after_frozen_credits,remark) VALUES(?,?,?,?,'SETTLED',?,?,?,?,?)",
                tenantId,biz,action,amount,remaining-delta,remaining,frozen,frozen,remark);
    }
    private static long n(Map<String,Object> row,String key) {Object v=row.get(key);return v==null?0:((Number)v).longValue();}
}
