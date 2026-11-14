package cn.iocoder.yudao.module.tk.job;

import cn.iocoder.yudao.module.tk.service.payment.*;
import cn.iocoder.yudao.module.tk.service.credit.TkCreditServiceImpl;
import cn.iocoder.yudao.module.tk.framework.config.TkPaymentProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TkPaymentReconcileJob {
    private final TkPaymentOrderStore store;
    private final TkPaymentService payments;
    private final TkPaymentProperties properties;
    private final JdbcTemplate db;
    private final TkCreditServiceImpl credits;
    public TkPaymentReconcileJob(TkPaymentOrderStore store,TkPaymentService payments,TkPaymentProperties properties,JdbcTemplate db,TkCreditServiceImpl credits){
        this.store=store;this.payments=payments;this.properties=properties;this.db=db;this.credits=credits;
    }
    @Scheduled(fixedDelay=30000,initialDelay=60000)
    public void reconcile(){
        // Workers keep recovering existing orders even when new purchases are switched off.
        if(!properties.isWorkerEnabled()||!properties.isConfigured())return;
        for(String order:store.due())if(store.claim(order))try{payments.reconcile(order);}catch(RuntimeException e){store.failed(order);log.warn("Payment reconcile deferred: {}",order);}
        store.cleanupNonces();
    }
    @Scheduled(fixedDelay=60000,initialDelay=90000)
    public void expire(){
        for(Long tenant:db.queryForList("SELECT DISTINCT tenant_id FROM tk_credit_batch WHERE expires_at<=CURRENT_TIMESTAMP AND available>0 LIMIT 100",Long.class))
            try{credits.ensureAccount(tenant);}catch(RuntimeException e){log.warn("Credit expiry deferred for tenant {}",tenant);}
    }
}
