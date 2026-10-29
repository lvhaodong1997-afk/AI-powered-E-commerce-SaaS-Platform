package cn.iocoder.yudao.module.tk.service.tiktokshop;

import cn.iocoder.yudao.module.tk.dal.dataobject.tiktokshop.TkTiktokShopAccountDO;
import cn.iocoder.yudao.module.tk.dal.mysql.tiktokshop.*;
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

/** Execute actual annotated SQL in isolated H2, including simultaneous claim and stale-token fences. */
class TkTiktokShopAtomicMapperTest {
    SqlSessionFactory database() throws Exception {
        UnpooledDataSource ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=ds.getConnection();Statement s=c.createStatement()){
            s.execute("CREATE TABLE tk_tiktok_shop_auth_session(id BIGINT PRIMARY KEY,tenant_id BIGINT,company_id BIGINT,user_id BIGINT,state_hash VARCHAR(64),status VARCHAR(32),expire_time TIMESTAMP,consumed_time TIMESTAMP,deleted INT)");
            s.execute("CREATE TABLE tk_tiktok_shop_account(id BIGINT PRIMARY KEY,tenant_id BIGINT,company_id BIGINT,app_key VARCHAR(128),open_id VARCHAR(128),creator VARCHAR(64),username VARCHAR(255),display_name VARCHAR(255),market VARCHAR(16),user_type INT,granted_scopes VARCHAR(1024),access_token_cipher VARCHAR(1024),refresh_token_cipher VARCHAR(1024),access_token_expire_time TIMESTAMP,refresh_token_expire_time TIMESTAMP,auth_status VARCHAR(32),token_version BIGINT,deleted INT)");
        }
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),ds));config.setMapUnderscoreToCamelCase(true);config.addMapper(TkTiktokShopAuthSessionMapper.class);config.addMapper(TkTiktokShopAccountMapper.class);
        return new SqlSessionFactoryBuilder().build(config);
    }
    @Test void simultaneousClaimHasOneWinnerExpiredDeletedAndReplayedStatesLose() throws Exception {
        SqlSessionFactory f=database();LocalDateTime now=LocalDateTime.of(2026,10,29,0,0);
        try(SqlSession s=f.openSession(true);PreparedStatement p=s.getConnection().prepareStatement("INSERT INTO tk_tiktok_shop_auth_session VALUES(?,?,?,?,?,?,?,?,?)")){
            for(int id=1;id<=3;id++){p.setLong(1,id);p.setLong(2,1);p.setLong(3,1);p.setLong(4,7);p.setString(5,"hash"+id);p.setString(6,"WAITING");p.setTimestamp(7,Timestamp.valueOf(id==2?now:now.plusMinutes(1)));p.setNull(8,Types.TIMESTAMP);p.setInt(9,id==3?1:0);p.executeUpdate();}
        }
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Integer> attempt=()->{start.await();try(SqlSession s=f.openSession(true)){return s.getMapper(TkTiktokShopAuthSessionMapper.class).claim("hash1",now);}};
        try {Future<Integer> a=pool.submit(attempt),b=pool.submit(attempt);start.countDown();assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        try(SqlSession s=f.openSession(true)){TkTiktokShopAuthSessionMapper m=s.getMapper(TkTiktokShopAuthSessionMapper.class);assertEquals(0,m.claim("hash1",now));assertEquals(0,m.claim("hash2",now));assertEquals(0,m.claim("hash3",now));assertEquals(0,m.claim("missing",now));assertEquals("PROCESSING",m.findClaimed("hash1").getStatus());}
    }
    @Test void reauthAndDisconnectFenceBothStaleRefreshSuccessAndFailure() throws Exception {
        SqlSessionFactory f=database();
        try(SqlSession s=f.openSession(true)){
            s.getConnection().createStatement().execute("INSERT INTO tk_tiktok_shop_account(id,tenant_id,company_id,app_key,open_id,creator,auth_status,token_version,deleted) VALUES(3,1,1,'app','open','7','AUTHORIZED',4,0)");
            TkTiktokShopAccountMapper m=s.getMapper(TkTiktokShopAccountMapper.class);
            TkTiktokShopAccountDO a=m.findIdentity(1L,"app","open");a.setAccessTokenCipher("new-auth-access");a.setRefreshTokenCipher("new-auth-refresh");a.setGrantedScopes("[\"creator.video.write\"]");a.setMarket("US");
            assertEquals(1,m.reauthorize(a,4));
            a.setAccessTokenCipher("stale-refresh");assertEquals(0,m.replaceTokens(a,4));assertEquals(0,m.markReauthorization(3L,1L,4));
            TkTiktokShopAccountDO fresh=m.findIdentity(1L,"app","open");assertEquals(5L,fresh.getTokenVersion());assertEquals("new-auth-access",fresh.getAccessTokenCipher());
            assertEquals(1,m.disconnect(3L,1L));assertEquals(0,m.replaceTokens(a,5));assertEquals(0,m.markReauthorization(3L,1L,5));
            TkTiktokShopAccountDO disconnected=m.findIdentity(1L,"app","open");assertEquals("DISCONNECTED",disconnected.getAuthStatus());assertEquals(6L,disconnected.getTokenVersion());assertNull(disconnected.getAccessTokenCipher());assertNull(disconnected.getRefreshTokenCipher());
            assertNull(m.findIdentity(2L,"app","open"));assertEquals(0,m.disconnect(3L,2L));
        }
    }
}
