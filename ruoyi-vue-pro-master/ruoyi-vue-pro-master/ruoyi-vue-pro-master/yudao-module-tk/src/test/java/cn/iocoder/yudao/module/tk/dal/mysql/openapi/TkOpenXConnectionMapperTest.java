package cn.iocoder.yudao.module.tk.dal.mysql.openapi;

import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkOpenXConnectionMapperTest {

    @Test
    void locksAndReturnsNewestLiveConnectionForClientAndAccountAfterSqlInterception() throws Exception {
        UnpooledDataSource source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:x_connection_" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=100", "sa", "");
        MybatisConfiguration config = new MybatisConfiguration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        MybatisPlusInterceptor interceptors = new MybatisPlusInterceptor();
        interceptors.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(new TenantProperties())));
        interceptors.addInnerInterceptor(new DataPermissionInterceptor(
                (MultiDataPermissionHandler) (table, where, mappedStatementId) -> null));
        config.addInterceptor(interceptors);
        config.addMapper(TkOpenXConnectionMapper.class);
        SqlSessionFactory factory = new MybatisSqlSessionFactoryBuilder().build(config);

        try (Connection setup = source.getConnection(); Statement statement = setup.createStatement()) {
            statement.execute("CREATE TABLE tk_open_x_connection (id BIGINT PRIMARY KEY, connection_id VARCHAR(64), "
                    + "client_id VARCHAR(128), workspace_id CHAR(36), external_account_id VARCHAR(128), display_name VARCHAR(256), "
                    + "username VARCHAR(256), access_token_cipher TEXT, refresh_token_cipher TEXT, token_expires_at TIMESTAMP, "
                    + "scopes VARCHAR(512), auth_status VARCHAR(32), end_user_id VARCHAR(128), source_social_session_id BIGINT, "
                    + "source_social_account_id BIGINT, fail_reason VARCHAR(255), last_auth_time TIMESTAMP, "
                    + "create_time TIMESTAMP, update_time TIMESTAMP, creator VARCHAR(64), updater VARCHAR(64), deleted INT)");
            statement.execute("INSERT INTO tk_open_x_connection (id, connection_id, client_id, external_account_id, deleted) VALUES "
                    + "(1,'older','client-a','account-a',0),(2,'newest','client-a','account-a',0),"
                    + "(3,'other-client','client-b','account-a',0),(4,'other-account','client-a','account-b',0),"
                    + "(5,'deleted','client-a','account-a',1)");

            try (SqlSession session = factory.openSession(false)) {
                TkOpenXConnectionMapper mapper = session.getMapper(TkOpenXConnectionMapper.class);
                assertEquals("newest", mapper.selectByClientAndExternalAccountIdForUpdate("client-a", "account-a").getConnectionId());
                assertNull(mapper.selectByClientAndExternalAccountIdForUpdate("client-a", "missing"));
                assertEquals(1, statement.executeUpdate("UPDATE tk_open_x_connection SET auth_status='REVOKED' WHERE id=3"));
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE tk_open_x_connection SET auth_status='REVOKED' WHERE id=2"));
                session.rollback(true);
                assertEquals(1, statement.executeUpdate("UPDATE tk_open_x_connection SET auth_status='REVOKED' WHERE id=2"));
            }
        }
    }
}
