package com.uci.inbound;

import java.util.HashMap;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.uci.utils.CampaignService;
import io.fusionauth.client.FusionAuthClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.uci.dao.service.HealthService;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AppConfigInbound {
	@Value("${spring.kafka.bootstrap-servers}")
    private String BOOTSTRAP_SERVERS;

    @Value("${campaign.url}")
    public String CAMPAIGN_URL;

    @Value("${campaign.admin.token}")
    public String CAMPAIGN_ADMIN_TOKEN;

    @Value("${fusionauth.url}")
    public String FUSIONAUTH_URL;

    @Value("${fusionauth.key}")
    public String FUSIONAUTH_KEY;

    @Autowired
    public Cache<Object, Object> cache;
	
	@Bean
	public HealthService healthService() {
		return new HealthService();
	}

	@Bean
    Map<String, Object> kafkaProducerConfiguration() {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        configuration.put(org.springframework.kafka.support.serializer.JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        configuration.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        return configuration;
    }
    
    @Bean
    ProducerFactory<String, String> producerFactory(){
    	ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(kafkaProducerConfiguration());
    	return producerFactory;
    }
    
    @Bean
    KafkaTemplate<String, String> kafkaTemplate() {
    	KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory());
    	return kafkaTemplate;
    }

    @Autowired
    private FusionAuthClient fusionAuthClient;

    @Autowired
    private WebClient webClient;

    @Bean
    public CampaignService getCampaignService() {
        return new CampaignService(webClient, fusionAuthClient, cache);
    }
}
