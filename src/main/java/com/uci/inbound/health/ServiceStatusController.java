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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;

import org.json.JSONObject;
import org.springframework.web.bind.annotation.RequestParam;
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

	@Value("${test.user.segment.json1:#{''}}")
	private String userSegmentJson1;

	@Value("${test.user.segment.json2:#{''}}")
	private String userSegmentJson2;

	/**
	 * Used by services that are connected to inbound for ping check.
	 * @return
	 */
	@RequestMapping(value = "/ping", method = RequestMethod.GET, produces = { "application/json", "text/json" })
	public ResponseEntity<ApiResponse> pingCheck() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode pingResult = mapper.createObjectNode();
		pingResult.put("status", "UP");
		ObjectNode statusNode = mapper.createObjectNode();
		statusNode.set("UCI-CORE", mapper.createObjectNode().put("status", "UP"));
		pingResult.set("details", statusNode);
		ApiResponse response = ApiResponse.builder()
				.id("api.ping")
				.params(ApiResponseParams.builder().build())
				.responseCode("OK")
				.result(pingResult)
				.build();
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

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
	public ResponseEntity<JsonNode> testUserSegment(@RequestParam(name = "limit", required = false) String limit, @RequestParam(name = "offset", required = false) String offset) throws Exception {
		log.info("Json : "+userSegmentJson1);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode result = null;
		if(userSegmentJson1 == null || userSegmentJson1.isEmpty()){
			result = mapper.readTree("{\"error\" : \"User Segment JSON Not Found\"}");
			return  ResponseEntity.ok(result);
		} else{
			result = mapper.readTree(userSegmentJson1);
		}
		return ResponseEntity.ok(result);
	}

	@RequestMapping(value = "/testUserSegment2", method = RequestMethod.GET, produces = { "application/json", "text/json" })
	public ResponseEntity<JsonNode> testUserSegment2(@RequestParam(name = "limit", required = false) String limit, @RequestParam(name = "offset", required = false) String offset) throws Exception {
		log.info("Json : "+userSegmentJson2);
		ObjectMapper mapper = new ObjectMapper();
		JsonNode result = null;
		if(userSegmentJson2 == null || userSegmentJson2.isEmpty()){
			result = mapper.readTree("{\"error\" : \"User Segment JSON Not Found\"}");
			return  ResponseEntity.ok(result);
		} else{
			result = mapper.readTree(userSegmentJson2);
		}
		return ResponseEntity.ok(result);
	}
}
