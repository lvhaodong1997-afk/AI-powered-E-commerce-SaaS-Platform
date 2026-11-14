package cn.iocoder.yudao.module.tk.dal.mysql;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkPublishFileCopyDO;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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

class TkPublishFileCopyMapperTest {
    private SqlSessionFactory database() throws Exception {
        UnpooledDataSource ds = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE tk_publish_file_copy(id CHAR(64) PRIMARY KEY,source_bucket VARCHAR(128),source_key VARCHAR(1024),source_etag VARCHAR(128),source_version VARCHAR(256),source_size BIGINT,target_url VARCHAR(2048),target_etag VARCHAR(128),status VARCHAR(16),claim_token VARCHAR(64),lease_until TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
        MybatisConfiguration configuration = new MybatisConfiguration(new Environment("test", new JdbcTransactionFactory(), ds));
        configuration.setMapUnderscoreToCamelCase(true);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TkPublishFileCopyDO.class);
        TenantDatabaseInterceptor tenant = new TenantDatabaseInterceptor(new TenantProperties());
        assertTrue(tenant.ignoreTable("tk_publish_file_copy"), "TenantIgnore must be recognized with registered table metadata");
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor(); interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenant));
        configuration.addInterceptor(interceptor); configuration.addMapper(TkPublishFileCopyMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }
    @Test void tenantPluginAndConcurrentClaimsPreserveReuseAndRetryFences() throws Exception {
        SqlSessionFactory db = database(); String id = new String(new char[64]).replace("\0", "a");
        TenantContextHolder.setTenantId(123L);
        try {
            try (SqlSession session = db.openSession(true)) {
                TkPublishFileCopyMapper m = session.getMapper(TkPublishFileCopyMapper.class);
                TkPublishFileCopyDO row = new TkPublishFileCopyDO();row.setId(id);row.setSourceBucket("material");row.setSourceKey("tk/123/456/file.mp4");row.setSourceEtag("etag");row.setSourceSize(100L);row.setTargetUrl("https://tkpublish.fnn.net.cn/tk/123/456/copy.mp4");
                assertEquals(1, m.insertIfMissing(row));assertEquals(0, m.insertIfMissing(row));assertEquals("PENDING",m.find(id).getStatus());
            }
            ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
            Callable<Integer> claim=()->{TenantContextHolder.setTenantId(999L);try{start.await();try(SqlSession s=db.openSession(true)){return s.getMapper(TkPublishFileCopyMapper.class).claim(id,Thread.currentThread().getName(),LocalDateTime.now().plusHours(2));}}finally{TenantContextHolder.clear();}};
            try {Future<Integer>a=pool.submit(claim),b=pool.submit(claim);start.countDown();assertEquals(1,a.get()+b.get());} finally {pool.shutdownNow();}
            try(SqlSession s=db.openSession(true)) {
                TkPublishFileCopyMapper m=s.getMapper(TkPublishFileCopyMapper.class);String token=m.find(id).getClaimToken();
                assertEquals(0,m.ready(id,"stale","etag2"));assertEquals(0,m.failed(id,"stale"));
                assertEquals(1,m.failed(id,token));assertEquals(1,m.claim(id,"retry",LocalDateTime.now().plusHours(2)));
                assertEquals(1,m.ready(id,"retry","etag2"));assertEquals(0,m.claim(id,"duplicate",LocalDateTime.now().plusHours(2)));
                assertEquals(0,m.invalidate(id,"foreign-etag"));assertEquals(1,m.invalidate(id,"etag2"));
                assertEquals(1,m.claim(id,"rebuild",LocalDateTime.now().plusHours(2)));
            }
        } finally {TenantContextHolder.clear();}
    }
}
