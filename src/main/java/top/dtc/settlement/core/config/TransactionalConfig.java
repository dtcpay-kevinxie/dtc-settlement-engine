package top.dtc.settlement.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class TransactionalConfig {

    @Autowired
    @Qualifier("coreDataSource")
    DataSource coreDataSource;

    @Autowired
    @Qualifier("riskDataSource")
    DataSource riskDataSource;

    @Autowired
    @Qualifier("settlementDataSource")
    DataSource settlementDataSource;


    @Primary
    @Bean
    public PlatformTransactionManager platformTransactionManager() {
        PlatformTransactionManager[] transactionManagers = new PlatformTransactionManager[]{
                new DataSourceTransactionManager(coreDataSource),
                new DataSourceTransactionManager(riskDataSource),
                new DataSourceTransactionManager(settlementDataSource)
        };
        return new ChainedTransactionManager(transactionManagers);
    }

}
