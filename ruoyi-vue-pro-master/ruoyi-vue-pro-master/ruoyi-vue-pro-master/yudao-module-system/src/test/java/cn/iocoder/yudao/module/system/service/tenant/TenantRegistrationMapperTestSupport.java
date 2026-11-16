package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.mybatis.core.handler.DefaultDBFieldHandler;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.*;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

import javax.sql.DataSource;

/** Real production mappers and audit handler; no replacement SQL for audit writes. */
final class TenantRegistrationMapperTestSupport {
    static SqlSessionTemplate createMappers(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, new GlobalConfig()
                .setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO))
                .setMetaObjectHandler(new DefaultDBFieldHandler()));
        configuration.setEnvironment(new Environment("registration-test", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(TenantRegistrationMapper.class);
        configuration.addMapper(TenantMapper.class);
        configuration.addMapper(TenantInviteMapper.class);
        configuration.addMapper(TenantReferralMapper.class);
        return new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration));
    }
}
