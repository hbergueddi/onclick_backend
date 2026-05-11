package com.onesley.oneclick.etl;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Configuration ETL — active uniquement quand profile {@code etl} actif.
 */
@Configuration
@Profile("etl")
@EnableConfigurationProperties(EtlProperties.class)
public class EtlConfig {

    @Bean
    public TransactionTemplate etlTransactionTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }
}
