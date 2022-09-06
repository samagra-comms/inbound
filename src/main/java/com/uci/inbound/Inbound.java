package com.uci.inbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.data.cassandra.repository.config.EnableReactiveCassandraRepositories;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@EnableReactiveCassandraRepositories("com.uci.dao")
@EntityScan("com.uci.dao")
@PropertySources({
        @PropertySource("classpath:application-messagerosa.properties"),
        @PropertySource("classpath:application.properties"),
        @PropertySource("classpath:application-adapter.properties"),
        @PropertySource("classpath:dao-application.properties"),
})
@SpringBootApplication
@ComponentScan(basePackages = {"com.uci.inbound", "com.uci.adapter", "com.uci.utils"})
public class Inbound {
    public static void main(String[] args) throws InterruptedException {
        SpringApplication.run(Inbound.class, args);
    }
}