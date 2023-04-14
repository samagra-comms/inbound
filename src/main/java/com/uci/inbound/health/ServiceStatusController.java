package com.uci.inbound.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.dao.service.HealthService;

import com.uci.utils.model.ApiResponse;
import com.uci.utils.model.ApiResponseParams;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
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

    /**
	 * In use by sunbird team - to check service liveliness & readliness
	 *
	 * @return
	 * @throws JsonProcessingException
	 */
    @RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public Mono<ResponseEntity<ApiResponse>> statusCheck() throws JsonProcessingException {
		return healthService.getAllHealthNode().map(health -> ApiResponse.builder()
				.id("api.health")
				.params(ApiResponseParams.builder().build())
				.result(health)
				.build()
		).map(response -> {
			if (((JsonNode)response.result).get("status").textValue().equals(Status.UP.getCode())) {
				response.responseCode = HttpStatus.OK.name();
				return new ResponseEntity<>(response, HttpStatus.OK);
			}
			else {
				response.responseCode = HttpStatus.SERVICE_UNAVAILABLE.name();
				return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
			}
		});
    }
    
    @RequestMapping(value = "/health/cassandra", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public Mono<ResponseEntity<ApiResponse>> cassandraStatusCheck() {
		return healthService.getCassandraHealthNode().map(result->
				ApiResponse.builder()
				.id("api.service.health.cassandra")
				.params(ApiResponseParams.builder().build())
				.result(result)
				.build())
				.map(response -> {
					if (((JsonNode)response.result).get("status").textValue().equals(Status.UP.getCode())) {
						response.responseCode = HttpStatus.OK.name();
						return new ResponseEntity<>(response, HttpStatus.OK);
					}
					else {
						response.responseCode = HttpStatus.SERVICE_UNAVAILABLE.name();
						return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
					}
				});
    }

    @RequestMapping(value = "/health/kafka", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public Mono<ResponseEntity<ApiResponse>> kafkaStatusCheck() {
		return healthService.getKafkaHealthNode().map(result->
				ApiResponse.builder()
				.id("api.service.health.kafka")
				.params(ApiResponseParams.builder().build())
				.result(result)
				.build())
				.map(response -> {
					if (((JsonNode)response.result).get("status").textValue().equals(Status.UP.getCode())) {
						response.responseCode = HttpStatus.OK.name();
						return new ResponseEntity<>(response, HttpStatus.OK);
					}
					else {
						response.responseCode = HttpStatus.SERVICE_UNAVAILABLE.name();
						return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
					}
				});
    }
    
    @RequestMapping(value = "/health/campaign", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public Mono<ResponseEntity<ApiResponse>> campaignUrlStatusCheck() {
		return healthService.getCampaignUrlHealthNode().map(result ->
			ApiResponse.builder().id("api.service.health.campaign")
			.params(ApiResponseParams.builder().build())
			.result(result)
			.build())
			.map(response -> {
				if (((JsonNode)response.result).get("status").textValue().equals(Status.UP.getCode())) {
					response.responseCode = HttpStatus.OK.name();
					return new ResponseEntity<>(response, HttpStatus.OK);
				}
				else {
					response.responseCode = HttpStatus.SERVICE_UNAVAILABLE.name();
					return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
				}
			});
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
