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
    @Qualifier("gw2DataSource")
    DataSource gw2DataSource;

    @Autowired
    @Qualifier("payoutDataSource")
    DataSource payoutDataSource;

    @Autowired
    @Qualifier("alipayDataSource")
    DataSource alipayDataSource;

    @Autowired
    @Qualifier("wechatDataSource")
    DataSource wechatDataSource;

    @Autowired
    @Qualifier("amexDataSource")
    DataSource amexDataSource;

    @Autowired
    @Qualifier("fdIndiaDataSource")
    DataSource fdIndiaDataSource;

    @Autowired
    @Qualifier("tbankDataSource")
    DataSource tbankDataSource;

    @Autowired
    @Qualifier("enetsDataSource")
    DataSource enetsDataSource;

    @Autowired
    @Qualifier("eximbayDataSource")
    DataSource eximbayDataSource;

    @Autowired
    @Qualifier("aletaDataSource")
    DataSource aletaDataSource;

    @Primary
    @Bean
    public PlatformTransactionManager platformTransactionManager() {
        PlatformTransactionManager[] transactionManagers = new PlatformTransactionManager[]{
                new DataSourceTransactionManager(gw2DataSource),
                new DataSourceTransactionManager(payoutDataSource),
                new DataSourceTransactionManager(alipayDataSource),
                new DataSourceTransactionManager(wechatDataSource),
                new DataSourceTransactionManager(fdIndiaDataSource),
                new DataSourceTransactionManager(tbankDataSource),
                new DataSourceTransactionManager(amexDataSource),
                new DataSourceTransactionManager(enetsDataSource),
                new DataSourceTransactionManager(aletaDataSource),
                new DataSourceTransactionManager(eximbayDataSource)
        };
        return new ChainedTransactionManager(transactionManagers);
    }

}
