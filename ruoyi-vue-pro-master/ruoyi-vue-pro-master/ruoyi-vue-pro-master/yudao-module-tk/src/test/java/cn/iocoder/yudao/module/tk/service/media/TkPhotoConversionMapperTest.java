package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TkPhotoConversionMapperTest {
    private SqlSessionFactory database() throws Exception {
        UnpooledDataSource ds=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        try(Connection c=ds.getConnection();Statement st=c.createStatement()) {
            st.execute("CREATE TABLE tk_open_tiktok_media(id BIGINT PRIMARY KEY,media_type VARCHAR(16),status VARCHAR(16),media_id VARCHAR(64),object_key VARCHAR(1024),file_url VARCHAR(2048),file_name VARCHAR(512),file_size BIGINT,content_type VARCHAR(128),sha256 VARCHAR(64),width INT,height INT,source_sha256 VARCHAR(64),completed_time TIMESTAMP,fail_reason VARCHAR(1024),conversion_claim_token VARCHAR(64),conversion_lease_until TIMESTAMP,expire_time TIMESTAMP,update_time TIMESTAMP,deleted TINYINT DEFAULT 0)");
            st.execute("CREATE TABLE tk_upload_session(id BIGINT PRIMARY KEY,purpose VARCHAR(16),status VARCHAR(16),result_media_id BIGINT,photo_width INT,photo_height INT,final_sha256 VARCHAR(64),completed_time TIMESTAMP,fail_reason VARCHAR(1024),conversion_claim_token VARCHAR(64),conversion_lease_until TIMESTAMP,expires_at TIMESTAMP,update_time TIMESTAMP,cancelled_time TIMESTAMP,deleted TINYINT DEFAULT 0)");
            st.execute("INSERT INTO tk_open_tiktok_media(id,media_type,status,conversion_claim_token,conversion_lease_until,expire_time) VALUES(1,'PHOTO','PROCESSING','expired',TIMESTAMPADD(SECOND,-5,NOW()),TIMESTAMPADD(HOUR,1,NOW()))");
            st.execute("INSERT INTO tk_upload_session(id,purpose,status,conversion_claim_token,conversion_lease_until,expires_at) VALUES(1,'PHOTO','PROCESSING','expired',TIMESTAMPADD(SECOND,-5,NOW()),TIMESTAMPADD(HOUR,1,NOW()))");
        }
        MybatisConfiguration config=new MybatisConfiguration(new Environment("test",new JdbcTransactionFactory(),ds));
        config.setMapUnderscoreToCamelCase(true);config.addMapper(TkOpenTiktokMediaMapper.class);config.addMapper(TkUploadSessionMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(config);
    }
    @Test void expiredOpenWorkerCannotCommitEvenBeforeLeaseReclaimed() throws Exception {
        try(SqlSession s=database().openSession(true)) {
            TkOpenTiktokMediaDO m=TkOpenTiktokMediaDO.builder().id(1L).mediaId("media_final").objectKey("final.jpg").fileUrl("https://publish/final.jpg")
                    .fileName("final.jpg").fileSize(123L).sha256("hash").width(8).height(4).sourceSha256("sourcehash").build();
            assertEquals(0,s.getMapper(TkOpenTiktokMediaMapper.class).completePhoto(m,"expired"));
        }
    }
    @Test void expiredAdminWorkerCannotCommitEvenBeforeLeaseReclaimed() throws Exception {
        try(SqlSession s=database().openSession(true)) {
            assertEquals(0,s.getMapper(TkUploadSessionMapper.class).completePhoto(1L,"expired",123L,8,4,"hash"));
        }
    }
    @Test void concurrentOpenClaimsHaveOneWinnerAndRecoveredLeaseFencesOldWorker() throws Exception {
        SqlSessionFactory db=database();ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        Callable<Integer> claim=()->{start.await();try(SqlSession s=db.openSession(true)){return s.getMapper(TkOpenTiktokMediaMapper.class).claimPhoto(1L,Thread.currentThread().getName());}};
        try {
            Future<Integer> a=pool.submit(claim),b=pool.submit(claim);start.countDown();assertEquals(1,a.get()+b.get());
            try(SqlSession s=db.openSession(true)) {
                TkOpenTiktokMediaMapper m=s.getMapper(TkOpenTiktokMediaMapper.class);
                assertTrue(m.selectPhotoConversionCandidates().isEmpty());
                String token;
                try(Statement st=s.getConnection().createStatement();ResultSet rs=st.executeQuery("SELECT conversion_claim_token FROM tk_open_tiktok_media WHERE id=1")){rs.next();token=rs.getString(1);}
                assertEquals(1,m.releasePhotoClaim(1L,token));assertEquals(1,m.selectPhotoConversionCandidates().size());
                assertEquals(1,m.claimPhoto(1L,"recovered"));assertEquals(0,m.failPhoto(1L,token,"late"));
                assertEquals(1,m.cancelPhoto(1L));
                TkOpenTiktokMediaDO output=TkOpenTiktokMediaDO.builder().id(1L).mediaId("m").fileSize(1L).width(1).height(1).build();
                assertEquals(0,m.completePhoto(output,"recovered"));assertEquals(0,m.claimPhoto(1L,"after-cancel"));
            }
        } finally {pool.shutdownNow();}
    }
    @Test void adminLeaseRecoveryAndCancelPreventDelayedReady() throws Exception {
        try(SqlSession s=database().openSession(true)) {
            TkUploadSessionMapper m=s.getMapper(TkUploadSessionMapper.class);
            assertEquals(1,m.selectPhotoConversionCandidates().size());assertEquals(1,m.claimPhoto(1L,"fresh"));
            assertEquals(0,m.completePhoto(1L,"expired",123L,8,4,"sha"));
            assertEquals(1,m.cancelPhoto(1L));assertEquals(0,m.completePhoto(1L,"fresh",123L,8,4,"sha"));
            assertEquals(0,m.failPhoto(1L,"fresh","late"));assertEquals(0,m.claimPhoto(1L,"recovery"));
        }
    }
    @Test void onlyCurrentUnexpiredLeaseOwnerCanRenew() throws Exception {
        try(SqlSession s=database().openSession(true)) {
            TkOpenTiktokMediaMapper open=s.getMapper(TkOpenTiktokMediaMapper.class);TkUploadSessionMapper admin=s.getMapper(TkUploadSessionMapper.class);
            assertEquals(0,open.renewPhotoLease(1L,"expired"));assertEquals(0,admin.renewPhotoLease(1L,"expired"));
            assertEquals(1,open.claimPhoto(1L,"live"));assertEquals(1,admin.claimPhoto(1L,"live"));
            assertEquals(1,open.renewPhotoLease(1L,"live"));assertEquals(1,admin.renewPhotoLease(1L,"live"));
            assertEquals(0,open.renewPhotoLease(1L,"foreign"));assertEquals(0,admin.renewPhotoLease(1L,"foreign"));
            open.cancelPhoto(1L);admin.cancelPhoto(1L);
            assertEquals(0,open.renewPhotoLease(1L,"live"));assertEquals(0,admin.renewPhotoLease(1L,"live"));
        }
    }
}
