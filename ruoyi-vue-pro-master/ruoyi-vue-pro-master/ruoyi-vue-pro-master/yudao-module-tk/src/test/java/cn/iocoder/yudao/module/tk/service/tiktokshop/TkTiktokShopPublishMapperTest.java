package cn.iocoder.yudao.module.tk.service.tiktokshop;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.*;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class TkTiktokShopPublishMapperTest {
    SqlSessionFactory db()throws Exception {
        UnpooledDataSource ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("CREATE TABLE tk_tiktok_shop_publish_task(id BIGINT PRIMARY KEY,tenant_id BIGINT,stage VARCHAR(64),owner_token VARCHAR(128),lease_expire_time TIMESTAMP,next_poll_time TIMESTAMP,deleted INT,version INT,request_hash VARCHAR(64),confirmed_by BIGINT,confirmed_at TIMESTAMP,confirmed_version INT,update_time TIMESTAMP,file_id VARCHAR(128),precheck_task_id VARCHAR(128),precheck_result VARCHAR(2048),video_id VARCHAR(128),request_id VARCHAR(128),poll_count INT,fail_code VARCHAR(128),fail_reason VARCHAR(1024))");
            s.execute("INSERT INTO tk_tiktok_shop_publish_task(id,tenant_id,stage,deleted,version,request_hash) VALUES(1,2,'AWAITING_CONFIRMATION',0,1,'hash')");
            s.execute("CREATE TABLE tk_tiktok_shop_publish_attempt(task_id BIGINT,tenant_id BIGINT,attempt_no INT,stage VARCHAR(64),video_id VARCHAR(128),request_id VARCHAR(128),fail_code VARCHAR(128),fail_reason VARCHAR(1024),response_json VARCHAR(2048),owner_token VARCHAR(128),lease_expire_time TIMESTAMP,update_time TIMESTAMP,deleted INT)");
            s.execute("INSERT INTO tk_tiktok_shop_publish_attempt(task_id,tenant_id,attempt_no,stage,deleted) VALUES(1,2,1,'SUBMITTING',0)");
        }
        Configuration conf=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));conf.setMapUnderscoreToCamelCase(true);conf.addMapper(TkTiktokShopPublishTaskMapper.class);conf.addMapper(TkTiktokShopPublishAttemptMapper.class);return new SqlSessionFactoryBuilder().build(conf);
    }
    @Test void confirmationAndLeaseHaveSingleWinnerWithStaleFence()throws Exception{
        SqlSessionFactory db=db();LocalDateTime now=LocalDateTime.of(2026,10,29,0,0);
        try(SqlSession s=db.openSession(true)){TkTiktokShopPublishTaskMapper m=s.getMapper(TkTiktokShopPublishTaskMapper.class);assertEquals(0,m.confirm(1L,2L,2,"hash",7L,now));assertEquals(1,m.confirm(1L,2L,1,"hash",7L,now));assertEquals(0,m.confirm(1L,2L,1,"hash",7L,now));}
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        Callable<Integer> run=()->{gate.await();try(SqlSession s=db.openSession(true)){return s.getMapper(TkTiktokShopPublishTaskMapper.class).claim(1L,2L,"SUBMITTING",UUID.randomUUID().toString(),now,now.plusMinutes(65));}};
        try{Future<Integer>a=pool.submit(run),b=pool.submit(run);gate.countDown();assertEquals(1,a.get()+b.get());}finally{pool.shutdownNow();}
        try(SqlSession s=db.openSession(true)){TkTiktokShopPublishTaskMapper m=s.getMapper(TkTiktokShopPublishTaskMapper.class);TkTiktokShopPublishTaskDO task=m.findTask(1L,2L);String owner=task.getOwnerToken();task.setStage("PROCESSING");assertEquals(0,m.saveFenced(task,"stale",now,false));assertEquals(0,m.saveFenced(task,owner,now.plusMinutes(65),false));assertEquals(1,m.saveFenced(task,owner,now,false));assertEquals(0,m.claim(1L,9L,"PROCESSING","wrong-tenant",now,now.plusMinutes(65)));}
    }
    @Test void concurrentConfirmOnlyOneWinnerAndAttemptWritesShareLeaseFence()throws Exception{
        SqlSessionFactory db=db();LocalDateTime now=LocalDateTime.of(2026,10,29,0,0);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        Callable<Integer> confirm=()->{gate.await();try(SqlSession s=db.openSession(true)){return s.getMapper(TkTiktokShopPublishTaskMapper.class).confirm(1L,2L,1,"hash",7L,now);}};
        try{Future<Integer>a=pool.submit(confirm),b=pool.submit(confirm);gate.countDown();assertEquals(1,a.get()+b.get());}finally{pool.shutdownNow();}
        try(SqlSession s=db.openSession(true)){
            TkTiktokShopPublishTaskMapper tasks=s.getMapper(TkTiktokShopPublishTaskMapper.class);assertEquals(1,tasks.claim(1L,2L,"SUBMITTING","owner",now,now.plusMinutes(65)));
            TkTiktokShopPublishAttemptDO a=new TkTiktokShopPublishAttemptDO();a.setTaskId(1L);a.setTenantId(2L);a.setStage("SENDING");a.setLeaseExpireTime(now.plusMinutes(65));
            TkTiktokShopPublishAttemptMapper attempts=s.getMapper(TkTiktokShopPublishAttemptMapper.class);assertEquals(0,attempts.updateFenced(a,"stale",now));assertEquals(0,attempts.updateFenced(a,"owner",now.plusMinutes(65)));assertEquals(1,attempts.updateFenced(a,"owner",now));a.setTenantId(9L);assertEquals(0,attempts.updateFenced(a,"owner",now));
        }
    }
}
