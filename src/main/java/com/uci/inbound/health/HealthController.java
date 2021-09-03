package com.uci.inbound.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uci.dao.service.HealthService;
import com.uci.inbound.api.response.examples.HealthApiExample;
import com.uci.inbound.api.response.examples.SystemHealthApiExample;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@Tag(name = "System Health Apis")
public class HealthController {

	@Autowired
	private HealthService healthService;

	@Operation(summary = "Get System Health", description = "This API is used to get system health. It included health of two components.\n"
			+ "- Kafka\n" + "- Cassandra")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "OK!", content = {
			@Content(mediaType = "application/json", schema = @Schema(implementation = SystemHealthApiExample.class)) }) })
	@RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
	public ResponseEntity<JsonNode> statusCheck() throws JsonProcessingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		/* Current Date Time */
		LocalDateTime localNow = LocalDateTime.now();
		DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
		String dateString = fmt.format(localNow).toString();

		JsonNode jsonNode = mapper.readTree(
				"{\"id\":\"api.content.health\",\"ver\":\"3.0\",\"ts\":\"2021-06-26T22:47:05Z+05:30\",\"params\":{\"resmsgid\":\"859fee0c-94d6-4a0d-b786-2025d763b78a\",\"msgid\":null,\"err\":null,\"status\":\"successful\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"checks\":[{\"name\":\"redis cache\",\"healthy\":true},{\"name\":\"graph db\",\"healthy\":true},{\"name\":\"cassandra db\",\"healthy\":true}],\"healthy\":true}}");

		((ObjectNode) jsonNode).put("ts", dateString);
		((ObjectNode) jsonNode).put("result", healthService.getAllHealthNode());

		return ResponseEntity.ok(jsonNode);
	}
}
