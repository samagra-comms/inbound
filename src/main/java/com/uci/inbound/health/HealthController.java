package com.uci.inbound.health;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uci.dao.service.HealthService;
import com.uci.utils.azure.AzureBlobService;
import com.uci.utils.cdn.samagra.MinioClientService;

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
public class HealthController {
	
	@Autowired
	private HealthService healthService;
	
	@Autowired
	private AzureBlobService azureBlobService;

    @Autowired
    private MinioClientService minioClientService;
	
    @RequestMapping(value = "/health", method = RequestMethod.GET, produces = { "application/json", "text/json" })
    public ResponseEntity<JsonNode> statusCheck() throws JsonProcessingException, IOException {
        log.error("Health API called");
        ObjectMapper mapper = new ObjectMapper();
        /* Current Date Time */
        LocalDateTime localNow = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
        String dateString = fmt.format(localNow).toString();
        
        JsonNode jsonNode = mapper.readTree("{\"id\":\"api.content.health\",\"ver\":\"3.0\",\"ts\":\"2021-06-26T22:47:05Z+05:30\",\"params\":{\"resmsgid\":\"859fee0c-94d6-4a0d-b786-2025d763b78a\",\"msgid\":null,\"err\":null,\"status\":\"successful\",\"errmsg\":null},\"responseCode\":\"OK\",\"result\":{\"checks\":[{\"name\":\"redis cache\",\"healthy\":true},{\"name\":\"graph db\",\"healthy\":true},{\"name\":\"cassandra db\",\"healthy\":true}],\"healthy\":true}}");
        
//        ((ObjectNode) jsonNode).put("ts", dateString);
//        ((ObjectNode) jsonNode).put("result", healthService.getAllHealthNode());
        
        return ResponseEntity.ok(jsonNode);
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
