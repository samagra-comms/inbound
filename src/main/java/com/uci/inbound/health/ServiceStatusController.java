package com.uci.inbound.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.dao.models.XMessageDAO;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.BotService;
import com.uci.dao.service.HealthService;
import com.uci.utils.cache.service.RedisCacheService;
import com.uci.utils.kafka.KafkaConfig;

import com.uci.utils.model.ApiResponse;
import com.uci.utils.model.ApiResponseParams;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.cassandra.CassandraHealthIndicator;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/service")
public class ServiceStatusController {
	@Autowired 
	private HealthService healthService;
    
    @RequestMapping(value = "/health/cassandra", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> cassandraStatusCheck() throws IOException, JsonProcessingException {
    	ApiResponse response = ApiResponse.builder()
                .id("api.service.health.cassandra")
                .params(ApiResponseParams.builder().build())
                .responseCode(HttpStatus.OK.name())
                .result(healthService.getCassandraHealthNode())
                .build();
    	
        return ResponseEntity.ok(response);
    }
    
    @RequestMapping(value = "/health/kafka", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> kafkaStatusCheck() throws IOException, JsonProcessingException {
        ApiResponse response = ApiResponse.builder()
                .id("api.service.health.kafka")
                .params(ApiResponseParams.builder().build())
                .responseCode(HttpStatus.OK.name())
                .result(healthService.getKafkaHealthNode())
                .build();

        return ResponseEntity.ok(response);
    }
    
    @RequestMapping(value = "/health/campaign", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> campaignUrlStatusCheck() throws JsonProcessingException, IOException {
        ApiResponse response = ApiResponse.builder()
                .id("api.service.health.campaign")
                .params(ApiResponseParams.builder().build())
                .responseCode(HttpStatus.OK.name())
                .result(healthService.getCampaignUrlHealthNode())
                .build();

        return ResponseEntity.ok(response);
    }

    @RequestMapping(value = "/testUserSegment", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<JsonNode> testUserSegment() throws JsonProcessingException, IOException {

        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        ObjectNode data1 = mapper.createObjectNode();
        data1.put("id", "1");
        data1.put("phoneNo", "7597185708");
        data1.put("name", "Surabhi");
        data1.put("url", "http://google.com");

        ObjectNode data2 = mapper.createObjectNode();
        data2.put("id", "2");
        data2.put("phoneNo", "9783246247");
        data2.put("name", "Pankaj");
        data2.put("url", "http://google.com");

        arrayNode.addAll(Arrays.asList(data1, data2));

        ObjectNode result = mapper.createObjectNode();
        result.put("data", arrayNode);

        return ResponseEntity.ok(result);
    }
}
