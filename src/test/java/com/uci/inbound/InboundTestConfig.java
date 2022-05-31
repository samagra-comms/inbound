package com.uci.inbound;

import com.github.benmanes.caffeine.cache.Cache;
import com.uci.adapter.utils.MediaSizeLimit;
import com.uci.dao.repository.XMessageRepository;
import com.uci.inbound.incoming.GupShupWhatsappConverter;
import com.uci.inbound.netcore.NetcoreWhatsappConverter;
import com.uci.utils.BotService;
import com.uci.utils.azure.AzureBlobProperties;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.SimpleProducer;
import io.fusionauth.client.FusionAuthClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.cache.CacheMono;

import java.util.HashMap;
import java.util.Map;


public class InboundTestConfig {


//    @MockBean
//    XMessageRepository xMsgRepo;
//
//    @MockBean
//    SimpleProducer simpleProducer;
//
//    @MockBean
//    BotService botService;
//
//    @MockBean
//    RedisCacheService redisCacheService;
//
//    @Autowired
//    AzureBlobService azureBlobService;
//
//    @Autowired
//    MediaSizeLimit mediaSizeLimit;


    @Bean
    public GupShupWhatsappConverter getGupShupWhatsappConverter(){
        return new GupShupWhatsappConverter();
    }

    @Bean
    public NetcoreWhatsappConverter getNetcoreWhatsappConverter(){
        NetcoreWhatsappConverter netcoreWhatsappConverter = new NetcoreWhatsappConverter();
        return netcoreWhatsappConverter ;
    }


    @Bean
    public SimpleProducer getSimpleProducer(){
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "165.232.182.146:9094");
        configuration.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-producer");
        configuration.put(ProducerConfig.ACKS_CONFIG, "all");
        configuration.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        configuration.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);

        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<String, String>(configuration);
        return new SimpleProducer(new KafkaTemplate<String, String>(producerFactory));
    }
    
    @Bean
    public XMessageRepository getXMessageRepository(){
        return null;
    }
    
    @Bean
    public BotService getBotService(){
        WebClient webClient = WebClient.create();
        FusionAuthClient fusionAuthClient = new FusionAuthClient("fa-api-key", "base-url");
        Cache<Object, Object> cache = Mockito.mock(Cache.class);
        Mockito.when(cache.getIfPresent(Mockito.any())).thenReturn(null);
        return new BotService(webClient, fusionAuthClient, cache);
    }

    @Bean
    public RedisCacheService getRedisCacheService(){
        return new RedisCacheService(new RedisTemplate<>());
    }

    @Bean
    public AzureBlobService getAzureBlobService(){
        AzureBlobService azureBlobService = new AzureBlobService(AzureBlobProperties.builder().build());
        return azureBlobService;
    }

    @Bean
    public MediaSizeLimit getMediaSizeLimit(){
        return new MediaSizeLimit(null, null, null, null);
    }


}
