package com.uci.inbound.config;


import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.time.Duration;

@Configuration
@EnableR2dbcRepositories
@Slf4j
public class PostgresConfig extends AbstractR2dbcConfiguration {
    @Value("${analytics.spring.r2dbc.url:#{''}}")
    private String url;
    @Value("${analytics.spring.r2dbc.username:#{''}}")
    private String username;
    @Value("${analytics.spring.r2dbc.password:#{''}}")
    private String password;
    @Value("${analytics.spring.r2dbc.host:#{''}}")
    private String host;
    @Value("${analytics.spring.r2dbc.port:#{'5430'}}")
    private int port;
    @Value("${analytics.spring.r2dbc.dbname:#{''}}")
    private String database;
    @Value("${spring.r2dbc.initialSize}")
    private String initialSize;
    @Value("${spring.r2dbc.maxIdleTime}")
    private String maxIdleTime;
    @Value("${spring.r2dbc.maxSize}")
    private String maxSize;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        log.info("Postgres Config : " + host + " : port : " + port);
        PostgresqlConnectionConfiguration postgresConfig = PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .username(username)
                .password(password)
                .database(database)
                .build();
        ConnectionFactory connectionFactory = new PostgresqlConnectionFactory(postgresConfig);
        ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .name("user-analytics-pool")
                .initialSize(Integer.parseInt(initialSize))
                .maxSize(Integer.parseInt(maxSize))
                .maxIdleTime(Duration.ofMillis(Integer.parseInt(maxIdleTime)))
                .build();
        return new ConnectionPool(poolConfig);
    }
}
