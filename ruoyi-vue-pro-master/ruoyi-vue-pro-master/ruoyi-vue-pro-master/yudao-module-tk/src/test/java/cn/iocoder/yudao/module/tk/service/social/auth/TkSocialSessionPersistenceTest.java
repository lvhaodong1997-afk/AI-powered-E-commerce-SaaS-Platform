package cn.iocoder.yudao.module.tk.service.social.auth;

import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAuthSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialAccountMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TkSocialSessionPersistenceTest {
    @Test void revokedRowCannotBeResurrectedByInFlightTokenRefresh() throws Exception {
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:h2:mem:meta_token_cas;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection(); Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS tk_social_account");
            s.execute("CREATE TABLE tk_social_account(id BIGINT PRIMARY KEY,tenant_id BIGINT,status VARCHAR(32),platform VARCHAR(32),"
                    +"access_token_ciphertext VARCHAR(4096),token_expires_at TIMESTAMP,last_validated_at TIMESTAMP,"
                    +"last_auth_time TIMESTAMP,update_time TIMESTAMP,deleted INT DEFAULT 0)");
            s.execute("INSERT INTO tk_social_account(id,tenant_id,status,access_token_ciphertext) VALUES(1,9,'AUTHORIZED','old')");
        }
        Configuration configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        configuration.addMapper(TkSocialAccountMapper.class);
        SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(configuration);
        try(SqlSession sql=factory.openSession(true)) {
            TkSocialAccountMapper mapper=sql.getMapper(TkSocialAccountMapper.class);
            try(Connection c=source.getConnection(); Statement s=c.createStatement()) {
                s.execute("UPDATE tk_social_account SET status='UNBOUND',access_token_ciphertext=NULL WHERE id=1");
            }
            assertEquals(0,mapper.replaceToken(1L,9L,"old","new",LocalDateTime.now().plusDays(60),LocalDateTime.now()));
        }
        try(Connection c=source.getConnection(); Statement s=c.createStatement();
            java.sql.ResultSet rows=s.executeQuery("SELECT status,access_token_ciphertext FROM tk_social_account WHERE id=1")) {
            assertTrue(rows.next()); assertEquals("UNBOUND",rows.getString(1)); assertNull(rows.getString(2));
        }
    }

    @Test void oneTimeClaimExpiryAndPayloadErasureExecuteAgainstDatabase() throws Exception {
        DriverManagerDataSource source=new DriverManagerDataSource("jdbc:h2:mem:meta_session;MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=source.getConnection(); Statement s=c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS tk_social_auth_session");
            s.execute("CREATE TABLE tk_social_auth_session(id BIGINT PRIMARY KEY,status VARCHAR(32),expire_time TIMESTAMP,"
                    +"payload_ciphertext VARCHAR(4096),fail_reason VARCHAR(500),update_time TIMESTAMP,deleted INT DEFAULT 0)");
            s.execute("INSERT INTO tk_social_auth_session(id,status,expire_time,payload_ciphertext) VALUES"
                    +"(1,'PENDING',DATEADD('MINUTE',10,CURRENT_TIMESTAMP),'cipher'),"
                    +"(2,'PAGES_READY',DATEADD('MINUTE',-10,CURRENT_TIMESTAMP),'cipher')");
        }
        Configuration configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),source));
        configuration.addMapper(TkSocialAuthSessionMapper.class);
        SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(configuration);
        try(SqlSession sql=factory.openSession(true)) {
            TkSocialAuthSessionMapper mapper=sql.getMapper(TkSocialAuthSessionMapper.class);
            LocalDateTime now=LocalDateTime.now();
            assertEquals(1,mapper.claim(1L,"PENDING","PROCESSING",now));
            assertEquals(0,mapper.claim(1L,"PENDING","PROCESSING",now));
            assertEquals(0,mapper.claim(2L,"PAGES_READY","PROCESSING",now));
            assertEquals(1,mapper.finish(1L,"PROCESSING","SUCCESS",null,null,now));
            assertEquals(0,mapper.finish(1L,"PROCESSING","SUCCESS","should-not-write",null,now));
            assertEquals(1,mapper.expire(now));
        }
        try(Connection c=source.getConnection(); Statement s=c.createStatement();
            java.sql.ResultSet rows=s.executeQuery("SELECT status,payload_ciphertext FROM tk_social_auth_session ORDER BY id")) {
            assertTrue(rows.next()); assertEquals("SUCCESS",rows.getString(1)); assertNull(rows.getString(2));
            assertTrue(rows.next()); assertEquals("EXPIRED",rows.getString(1)); assertNull(rows.getString(2));
        }
    }
}
