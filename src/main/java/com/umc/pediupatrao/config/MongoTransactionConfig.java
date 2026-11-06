package com.umc.pediupatrao.config;

import com.mongodb.TransactionOptions;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/** Escritas auditadas exigem replica set; standalone falha sem escrita parcial. */
@Configuration
@EnableTransactionManagement
public class MongoTransactionConfig {
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory, TransactionOptions.builder()
                .readConcern(ReadConcern.SNAPSHOT).writeConcern(WriteConcern.MAJORITY).build());
    }
}
