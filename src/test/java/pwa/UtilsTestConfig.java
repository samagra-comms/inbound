package pwa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uci.utils.BotService;
import com.uci.utils.UtilHealthService;
import com.uci.utils.dto.BotServiceParams;
import com.uci.utils.kafka.KafkaConfig;
import com.uci.utils.service.UserService;
import io.fusionauth.client.FusionAuthClient;
import org.apache.kafka.clients.admin.AdminClient;
import org.mockito.Mock;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;


public class UtilsTestConfig {

    @Mock
    WebClient webClient;

    @MockBean
    FusionAuthClient fusionAuthClient;

    @MockBean
    BotServiceParams botServiceParams;

    @MockBean
    AdminClient adminClient;
    @MockBean
    Cache<Object, Object> cache;
    @Mock
    KafkaTemplate<String, String> simpleProducer;

    @Bean
    public UserService getUserService() {
        return new UserService();
    }

    @Bean
    public UtilHealthService getUtilHealthService() {
        return new UtilHealthService();
    }

    @Bean
    public KafkaConfig getKafkaConfig() {
        return new KafkaConfig();
    }

    @Bean
    public BotService getBotService() {
        return new BotServiceTest(webClient, fusionAuthClient, cache, botServiceParams);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    //    @Bean
//    public Cache<Object, Object> caffeineCacheBuilder() {
//        return Caffeine.newBuilder()
//                .recordStats()
//                .build();
//    }
    public Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .recordStats();
    }

    @Bean
    public Cache<Object, Object> cache() {
        return caffeineCacheBuilder().build();
    }

    @Bean
    public SimpleProducerTest simpleProducer() {
        return new SimpleProducerTest(simpleProducer);
    }
}
