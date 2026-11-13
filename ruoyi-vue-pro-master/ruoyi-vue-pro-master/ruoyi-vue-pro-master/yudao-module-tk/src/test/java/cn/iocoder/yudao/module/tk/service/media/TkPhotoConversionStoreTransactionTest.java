package cn.iocoder.yudao.module.tk.service.media;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TkPhotoConversionStoreTransactionTest {
    @Test void cancelledAdminConversionRollsBackMediaInsertAndSuccessfulConversionCommitsBoth() throws Exception {
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        JdbcTemplate jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE tk_upload_session(id BIGINT PRIMARY KEY,purpose VARCHAR(16),status VARCHAR(16),result_media_id BIGINT,photo_width INT,photo_height INT,final_sha256 VARCHAR(64),completed_time TIMESTAMP,fail_reason VARCHAR(1024),conversion_claim_token VARCHAR(64),conversion_lease_until TIMESTAMP,expires_at TIMESTAMP,update_time TIMESTAMP,deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE tk_tiktok_publish_media(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,company_id BIGINT,file_name VARCHAR(512),file_url VARCHAR(2048),cover_url VARCHAR(2048),file_size BIGINT,mime_type VARCHAR(128),status VARCHAR(16),creator VARCHAR(64),updater VARCHAR(64),create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,deleted TINYINT DEFAULT 0)");
        jdbc.execute("INSERT INTO tk_upload_session(id,purpose,status,conversion_claim_token,conversion_lease_until,expires_at) VALUES(1,'PHOTO','CANCELLED','claim',TIMESTAMPADD(MINUTE,10,NOW()),TIMESTAMPADD(HOUR,1,NOW()))");
        MybatisConfiguration config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        GlobalConfig global=new GlobalConfig();GlobalConfig.DbConfig dbc=new GlobalConfig.DbConfig();dbc.setIdType(IdType.AUTO);global.setDbConfig(dbc);global.setBanner(false);
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);factory.setConfiguration(config);factory.setGlobalConfig(global);factory.setTransactionFactory(new SpringManagedTransactionFactory());
        org.apache.ibatis.session.SqlSessionFactory sf=factory.getObject();config.addMapper(TkUploadSessionMapper.class);config.addMapper(TkTiktokPublishMediaMapper.class);
        SqlSessionTemplate template=new SqlSessionTemplate(sf);
        TkPhotoConversionStore target=new TkPhotoConversionStore();ReflectionTestUtils.setField(target,"sessions",template.getMapper(TkUploadSessionMapper.class));ReflectionTestUtils.setField(target,"media",template.getMapper(TkTiktokPublishMediaMapper.class));
        ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        TkPhotoConversionStore store=(TkPhotoConversionStore)proxy.getProxy();
        TkUploadSessionDO row=new TkUploadSessionDO().setId(1L).setUploadId("photo_123").setCompanyId(7L);row.setTenantId(5L);row.setCreator("11");
        TkPhotoNormalizationService.Result result=new TkPhotoNormalizationService.Result(8,4,123,"sha");
        assertThrows(TkPhotoConversionStore.StaleClaimException.class,()->store.finishAdmin(row,"claim","https://publish/a.jpg",result));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM tk_tiktok_publish_media",Integer.class));
        jdbc.update("UPDATE tk_upload_session SET status='PROCESSING' WHERE id=1");
        Long id=store.finishAdmin(row,"claim","https://publish/a.jpg",result);
        assertNotNull(id);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tk_tiktok_publish_media",Integer.class));
        assertEquals(id,jdbc.queryForObject("SELECT result_media_id FROM tk_upload_session WHERE id=1",Long.class));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM tk_upload_session WHERE id=1",String.class));
    }
}
