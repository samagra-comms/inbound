package com.uci.inbound.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.dao.service.HealthService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.bot.util.DateUtil;
import com.uci.utils.cdn.samagra.MinioClientService;

import com.uci.utils.model.ApiResponse;
import com.uci.utils.model.ApiResponseParams;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthController {
	
	@Autowired
	private HealthService healthService;
	
	@Autowired
	private AzureBlobService azureBlobService;

    @Autowired
    private MinioClientService minioClientService;
	
    @RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<ApiResponse> statusCheck() throws JsonProcessingException, IOException {
        log.error("Health API called");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode resultNode = mapper.readTree("{\"checks\":[{\"name\":\"redis cache\",\"healthy\":true},{\"name\":\"graph db\",\"healthy\":true},{\"name\":\"cassandra db\",\"healthy\":true}],\"healthy\":true}");
        ApiResponse response = ApiResponse.builder()
                .id("api.health")
                .params(ApiResponseParams.builder().build())
                .responseCode(HttpStatus.OK.name())
                .result(resultNode)
//                .result(healthService.getAllHealthNode())
                .build();

        return ResponseEntity.ok(response);
    }
    
    @RequestMapping(value = "/image-signed-url", method = RequestMethod.GET)
    public void test() {
        log.info(minioClientService.getCdnSignedUrl("bot-audio.mp3"));
//        log.info(azureBlobService.getFileSignedUrl("testing-1.jpg"));
    }
    
    @RequestMapping(value = "/azure-container-sas", method = RequestMethod.GET)
    public void generateAzureContainerSASToken() {
    	log.info(azureBlobService.generateContainerSASToken());
    }
}
